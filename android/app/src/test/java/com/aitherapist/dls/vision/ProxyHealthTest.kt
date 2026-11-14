package com.aitherapist.dls.vision

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Story 5.5: the Diagnostics health ping maps 200 / 400 / 401 / timeout / IO / not configured. */
class ProxyHealthTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun health(url: String = server.url("/").toString(), secret: String = "s3cret", timeoutMs: Long = CloudVerifier.TIMEOUT_MS) =
        runBlocking { ProxyProbe(url, clockMs = { 0L }, timeoutMs = timeoutMs).health(secret) }

    @Test
    fun `400 malformed_body is ok and the request carries the secret but no body`() {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"malformed_body"}""").build())
        val result = health()
        assertEquals(ProxyHealth.OK, result.health)
        assertEquals(400, result.status)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/verify", request.url.encodedPath)
        assertEquals("Bearer s3cret", request.headers["Authorization"])
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun `other 400 answers are unexpected`() {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"invalid_request_id"}""").build())
        assertEquals(ProxyHealth.UNEXPECTED, health().health)
        server.enqueue(MockResponse.Builder().code(400).body("<html>bad</html>").build())
        assertEquals(ProxyHealth.UNEXPECTED, health().health)
    }

    @Test
    fun `2xx is unexpected (captive portal)`() {
        server.enqueue(MockResponse.Builder().code(200).body("<html>login</html>").build())
        assertEquals(ProxyHealth.UNEXPECTED, health().health)
        server.enqueue(MockResponse.Builder().code(204).build())
        assertEquals(ProxyHealth.UNEXPECTED, health().health)
    }

    @Test
    fun `401 is a rejected secret`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        val result = health()
        assertEquals(ProxyHealth.REJECTED_SECRET, result.health)
        assertEquals(401, result.status)
    }

    @Test
    fun `other statuses are unexpected`() {
        server.enqueue(MockResponse.Builder().code(502).build())
        assertEquals(ProxyHealth.UNEXPECTED, health().health)
    }

    @Test
    fun `delayed headers time out`() {
        server.enqueue(MockResponse.Builder().code(200).headersDelay(5, TimeUnit.SECONDS).build())
        val result = health(timeoutMs = 500)
        assertEquals(ProxyHealth.TIMEOUT, result.health)
        assertNull(result.status)
    }

    @Test
    fun `closed server is offline`() {
        val url = server.url("/").toString()
        server.close()
        assertEquals(ProxyHealth.OFFLINE, health(url).health)
    }

    @Test
    fun `missing url or secret is not configured without a request`() {
        assertEquals(ProxyHealth.NOT_CONFIGURED, health(url = "").health)
        assertEquals(ProxyHealth.NOT_CONFIGURED, health(secret = "").health)
        assertEquals(ProxyHealth.NOT_CONFIGURED, health(url = "not a url").health)
        assertEquals(0, server.requestCount)
    }
}
