package com.aitherapist.dls.vision

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProxyProbeTest {
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

    private fun probe(url: String = server.url("/").toString(), timeoutMs: Long = CloudVerifier.TIMEOUT_MS) =
        runBlocking { ProxyProbe(url, clockMs = { 0L }, timeoutMs = timeoutMs).probe() }

    @Test
    fun `405 is reachable`() {
        server.enqueue(MockResponse.Builder().code(405).build())
        val result = probe()
        assertTrue(result.reachable)
        assertEquals("http_405", result.detail)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/verify", request.url.encodedPath)
    }

    @Test
    fun `200 is not reachable`() {
        server.enqueue(MockResponse.Builder().code(200).build())
        val result = probe()
        assertFalse(result.reachable)
        assertEquals("http_200", result.detail)
    }

    @Test
    fun `closed server is network`() {
        val url = server.url("/").toString()
        server.close()
        val result = probe(url)
        assertFalse(result.reachable)
        assertEquals(VerdictReason.NETWORK, result.detail)
    }

    @Test
    fun `blank and malformed url are network without a request`() {
        assertEquals(VerdictReason.NETWORK, probe("").detail)
        assertEquals(VerdictReason.NETWORK, probe("not a url").detail)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `delayed headers time out`() {
        server.enqueue(MockResponse.Builder().code(405).headersDelay(5, TimeUnit.SECONDS).build())
        val result = probe(timeoutMs = 500)
        assertFalse(result.reachable)
        assertEquals(VerdictReason.TIMEOUT, result.detail)
    }
}
