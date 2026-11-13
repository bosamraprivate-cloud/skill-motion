package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Step
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionStore
import com.aitherapist.dls.session.AssessmentRunner
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.session.PoseSample
import com.aitherapist.dls.session.RunnerEvent
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Story 3.5 router tests. Steps and questions are test fixtures, not catalogue content. */
class VerifierRouterTest {
    private class Recording(private val outcome: Outcome = Outcome.DONE) : Verifier {
        val calls = mutableListOf<VerifyContext>()

        override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict {
            calls += ctx
            return Verdict(outcome, VerdictReason.CHECK_PASSED, latencyMs = 1)
        }
    }

    private class FakeImage : AttemptImage {
        override val width = 640
        override val height = 480

        override fun release() = Unit
    }

    private val visible = (0 until 10).map { LandmarkFrame(it * 100L, List(33) { LandmarkPoint(0.5f, 0.3f, 0.9f) }) }
    private val hidden = (0 until 10).map { LandmarkFrame(it * 100L, null) }
    private val zone = mapOf("cup" to Zone(0.1f, 0.1f, 0.4f, 0.4f))

    private fun route(router: VerifierRouter, method: Method, landmarks: List<LandmarkFrame> = visible) =
        runBlocking { router.verify(Step("t.x.01", method), emptyList(), landmarks, VerifyContext()) }

    @Test
    fun `each method goes to its verifier with zones from settings`() {
        val pose = Recording()
        val hand = Recording()
        val cloud = Recording()
        val router = VerifierRouter(pose, hand, { cloud }, { zone })
        route(router, Method.POSE)
        route(router, Method.HAND)
        route(router, Method.CLOUD)
        assertEquals(listOf(zone), pose.calls.map { it.zones })
        assertEquals(1, hand.calls.size)
        assertEquals(1, cloud.calls.size)
        val none = route(router, Method.NONE)
        assertEquals(VerdictReason.NOT_JUDGEABLE, none.reason)
        assertEquals(1, pose.calls.size)
    }

    @Test
    fun `gate runs first - out of frame is cannot tell and no verifier runs`() {
        val pose = Recording(Outcome.NOT_DONE)
        val router = VerifierRouter(pose, pose, { pose })
        listOf(Method.POSE, Method.HAND, Method.CLOUD).forEach { m ->
            listOf(hidden, emptyList()).forEach { lm ->
                val v = route(router, m, lm)
                assertEquals(Outcome.CANNOT_TELL, v.outcome)
                assertEquals(VerdictReason.OUT_OF_FRAME, v.reason)
            }
        }
        assertTrue(pose.calls.isEmpty())
    }

    @Test
    fun `missing proxy settings and throwing verifiers become cannot tell`() {
        val throwing =
            object : Verifier {
                override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict =
                    error("boom")
            }
        assertEquals(VerdictReason.NETWORK, route(VerifierRouter(throwing, throwing, { null }), Method.CLOUD).reason)
        val v = route(VerifierRouter(throwing, throwing, { throwing }), Method.POSE)
        assertEquals(Outcome.CANNOT_TELL, v.outcome)
        assertEquals(VerdictReason.VERIFIER_ERROR, v.reason)
        val badZones = route(VerifierRouter(Recording(), Recording(), { null }, { throw IOException("store") }), Method.POSE)
        assertEquals(Outcome.CANNOT_TELL, badZones.outcome)
    }

    @Test
    fun `body steps make no network request even with a failing http client`() {
        var requests = 0
        val failing =
            OkHttpClient.Builder().addInterceptor {
                requests++
                throw IOException("offline")
            }.build()
        val cloud = CloudVerifier("https://proxy.invalid", "s", { _, _, _, _ -> ByteArray(1) }, failing)
        val router = VerifierRouter(PoseVerifier(), HandVerifier(), { cloud })
        val pose =
            Step("t.x.01", Method.POSE, Check.PointAbove(LandmarkRef.Lm("NOSE"), LandmarkRef.Lm("LEFT_HIP"), margin = 0.0, holdSec = 0.0))
        val v = runBlocking { router.verify(pose, emptyList(), visible, VerifyContext()) }
        assertFalse(v.reason == VerdictReason.NETWORK)
        assertEquals(0, requests)
        // The same client really fails for a cloud step: it maps to cannot tell, never not done.
        val frames = visible.map { Frame(it.timestampMs, FakeImage()) }
        val c = runBlocking { router.verify(Step("t.x.02", Method.CLOUD, Check.Question("q?")), frames, visible, VerifyContext()) }
        assertEquals(Outcome.CANNOT_TELL, c.outcome)
        assertEquals(1, requests)
    }

    @Test
    fun `cloud request from the runner carries no child or session identifiers`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse.Builder().code(200).body("""{"verdict":"done","reason":"r","modelId":"m-1","promptVersion":"p-1"}""").build()
            )
            val cloud = CloudVerifier(server.url("/").toString(), "secret", { _, _, _, _ -> byteArrayOf(1, 2, 3) })
            val router = VerifierRouter(Recording(), Recording(), { cloud })
            val rows = mutableListOf<AttemptEntity>()
            val store =
                object : SessionStore {
                    override suspend fun add(session: SessionEntity) = Unit

                    override suspend fun update(session: SessionEntity) = Unit

                    override suspend fun addAttempt(attempt: AttemptEntity) {
                        rows += attempt
                    }

                    override suspend fun attempts(sessionId: String, stepId: String) = rows.toList()

                    override suspend fun resumable(childId: String) = emptyList<SessionEntity>()
                }
            var now = 0L
            val runner =
                AssessmentRunner(
                    "session-7f3a", "child-K42", "t.x", "cat", listOf(Step("t.x.01", Method.CLOUD, Check.Question("q?"))), store, router,
                    Clock { now }, Dispatchers.IO
                )
            runner.start()
            runner.onEvent(RunnerEvent.AudioEnded)
            visible.forEach {
                now = it.timestampMs + 1
                runner.onEvent(RunnerEvent.Sample(PoseSample(now, emptyMap()), Frame(now, FakeImage()), it.copy(timestampMs = now)))
            }
            runner.onEvent(RunnerEvent.Done)
            val body = server.takeRequest().body!!.utf8()
            listOf("session-7f3a", "child-K42").forEach { assertFalse(it, body.contains(it)) }
            assertEquals("m-1", rows.single().modelId)
            assertEquals("p-1", rows.single().promptVersion)
        } finally {
            server.close()
        }
    }
}
