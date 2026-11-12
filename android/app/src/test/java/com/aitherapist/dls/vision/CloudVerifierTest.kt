package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Step
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartReader
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** MockWebServer tests for CloudVerifier (Story 1.5 matrix). The step and question are test fixtures. */
class CloudVerifierTest {
    private lateinit var server: MockWebServer
    private val step = Step("test.cup.01", Method.CLOUD, Check.Question("Is the test object touching the lips?"))
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
    private val encoder = JpegEncoder { _, _, _, _ -> jpeg }

    private class FakeImage : AttemptImage {
        override val width = 640
        override val height = 480

        override fun release() = Unit
    }

    private fun pose(visibility: Float = 0.9f) = List(33) { LandmarkPoint(0.5f, 0.3f, visibility) }

    private fun attempt(count: Int, visibility: Float = 0.9f): Pair<List<Frame>, List<LandmarkFrame>> {
        val ts = (0 until count).map { it * 100L }
        return ts.map { Frame(it, FakeImage()) } to ts.map { LandmarkFrame(it, pose(visibility)) }
    }

    private fun verifier(
        url: String = server.url("/").toString(),
        secret: String = "test-secret",
        timeoutMs: Long = CloudVerifier.TIMEOUT_MS
    ) = CloudVerifier(url, secret, encoder, clockMs = { 0L }, timeoutMs = timeoutMs)

    private fun verify(v: CloudVerifier = verifier(), count: Int = 10, visibility: Float = 0.9f): Verdict {
        val (frames, landmarks) = attempt(count, visibility)
        return runBlocking { v.verify(step, frames, landmarks, VerifyContext()) }
    }

    private fun respond(code: Int, body: String = "") = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `200 done returns done with model and prompt version`() {
        respond(200, """{"verdict":"done","reason":"rim at lip","modelId":"m-1","promptVersion":"v1"}""")
        val v = verify()
        assertEquals(Outcome.DONE, v.outcome)
        assertEquals("rim at lip", v.reason)
        assertEquals("m-1", v.modelId)
        assertEquals("v1", v.promptVersion)
    }

    @Test
    fun `200 not_done returns not_done`() {
        respond(200, """{"verdict":"not_done","reason":"cup away","modelId":"m-1","promptVersion":"v1"}""")
        assertEquals(Outcome.NOT_DONE, verify().outcome)
    }

    @Test
    fun `200 cannot_tell passes through`() {
        respond(200, """{"verdict":"cannot_tell","reason":"invalid_model_output","modelId":"m-1","promptVersion":"v1"}""")
        val v = verify()
        assertEquals(Outcome.CANNOT_TELL, v.outcome)
        assertEquals("invalid_model_output", v.reason)
    }

    @Test
    fun `200 garbage is invalid_model_output`() {
        listOf("not json", """{"verdict":"maybe","reason":"x"}""", """{"reason":"x"}""").forEach { body ->
            respond(200, body)
            val v = verify()
            assertEquals(body, Outcome.CANNOT_TELL, v.outcome)
            assertEquals(body, VerdictReason.INVALID_MODEL_OUTPUT, v.reason)
        }
    }

    @Test
    fun `non-200 maps to http status`() {
        listOf(401, 413, 502).forEach { code ->
            respond(code, "{}")
            val v = verify()
            assertEquals(Outcome.CANNOT_TELL, v.outcome)
            assertEquals("http_$code", v.reason)
            assertNull(v.modelId)
        }
    }

    @Test
    fun `slow response times out`() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").headersDelay(5, TimeUnit.SECONDS).build())
        val v = verify(verifier(timeoutMs = 500))
        assertEquals(Outcome.CANNOT_TELL, v.outcome)
        assertEquals(VerdictReason.TIMEOUT, v.reason)
    }

    @Test
    fun `200 verdict without provenance is invalid_model_output`() {
        listOf(
            """{"verdict":"done","reason":"ok","promptVersion":"v1"}""",
            """{"verdict":"not_done","reason":"ok","modelId":"m","promptVersion":" "}"""
        ).forEach { body ->
            respond(200, body)
            val v = verify()
            assertEquals(body, Outcome.CANNOT_TELL, v.outcome)
            assertEquals(body, VerdictReason.INVALID_MODEL_OUTPUT, v.reason)
        }
    }

    @Test
    fun `malformed proxy url is network without a request`() {
        assertEquals(VerdictReason.NETWORK, verify(verifier(url = "not a url")).reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `dropped connection is network`() {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.ShutdownConnection).build())
        assertEquals(VerdictReason.NETWORK, verify().reason)
    }

    @Test
    fun `no server is network`() {
        val url = server.url("/").toString()
        server.close()
        val v = verify(verifier(url))
        assertEquals(Outcome.CANNOT_TELL, v.outcome)
        assertEquals(VerdictReason.NETWORK, v.reason)
    }

    @Test
    fun `missing config is network without a request`() {
        assertEquals(VerdictReason.NETWORK, verify(verifier(url = "")).reason)
        assertEquals(VerdictReason.NETWORK, verify(verifier(secret = "")).reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `fewer than 3 visible frames is out_of_frame without a request`() {
        assertEquals(VerdictReason.OUT_OF_FRAME, verify(count = 2).reason)
        assertEquals(VerdictReason.OUT_OF_FRAME, verify(visibility = 0.2f).reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `non-question step is not_judgeable`() {
        val (frames, landmarks) = attempt(5)
        val v = runBlocking { verifier().verify(step.copy(check = null), frames, landmarks, VerifyContext()) }
        assertEquals(VerdictReason.NOT_JUDGEABLE, v.reason)
    }

    @Test
    fun `encoder failure is not_judgeable`() {
        val failing = CloudVerifier(server.url("/").toString(), "s", { _, _, _, _ -> error("boom") }, clockMs = { 0L })
        assertEquals(VerdictReason.NOT_JUDGEABLE, verify(failing).reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `request shape is multipart with fresh uuid, step, question and 3 jpegs`() {
        val ids = mutableListOf<String>()
        repeat(2) {
            respond(200, """{"verdict":"done","reason":"ok","modelId":"m","promptVersion":"v1"}""")
            verify()
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/verify", request.url.encodedPath)
            assertEquals("Bearer test-secret", request.headers["Authorization"])
            val contentType = requireNotNull(request.headers["Content-Type"]).toMediaType()
            assertEquals("multipart", contentType.type)
            val reader = MultipartReader(Buffer().write(requireNotNull(request.body)), requireNotNull(contentType.parameter("boundary")))
            val fields = mutableMapOf<String, String>()
            val images = mutableListOf<ByteArray>()
            while (true) {
                val part = reader.nextPart() ?: break
                val disposition = requireNotNull(part.headers["Content-Disposition"])
                val name = Regex("name=\"([^\"]+)\"").find(disposition)!!.groupValues[1]
                if (name == "image") {
                    assertEquals("image/jpeg", part.headers["Content-Type"])
                    images += part.body.readByteArray()
                } else {
                    assertTrue("duplicate $name", fields.put(name, part.body.readUtf8()) == null)
                }
            }
            assertEquals(setOf("requestId", "stepId", "question"), fields.keys)
            assertEquals(step.id, fields["stepId"])
            assertEquals((step.check as Check.Question).text, fields["question"])
            assertEquals(4, UUID.fromString(fields["requestId"]).version())
            ids += fields.getValue("requestId")
            assertEquals(3, images.size)
            images.forEach { assertArrayEquals(jpeg.copyOf(3), it.copyOf(3)) }
        }
        assertTrue("requestId must be fresh", ids[0] != ids[1])
    }

    @Test
    fun `picks start, middle and end visible frames`() {
        respond(200, """{"verdict":"done","reason":"ok","modelId":"m","promptVersion":"v1"}""")
        val used = mutableListOf<Long>()
        val images = (0 until 7).associateWith { FakeImage() }
        val tracking = JpegEncoder { image, _, _, _ ->
            used += images.entries.first { it.value === image }.key.toLong()
            jpeg
        }
        val frames = images.map { (i, img) -> Frame(i * 100L, img) }.reversed()
        val landmarks = (0 until 7).map { LandmarkFrame(it * 100L, pose()) }
        runBlocking {
            CloudVerifier(server.url("/").toString(), "s", tracking, clockMs = { 0L }).verify(step, frames, landmarks, VerifyContext())
        }
        assertEquals(listOf(0L, 3L, 6L), used)
    }
}
