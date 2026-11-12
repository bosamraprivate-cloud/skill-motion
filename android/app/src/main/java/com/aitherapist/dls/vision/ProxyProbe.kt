package com.aitherapist.dls.vision

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Probe result: [reachable] when `GET /verify` answered 405; [detail] is `http_<status>`, `network` or `timeout`. */
data class ProbeResult(val reachable: Boolean, val roundTripMs: Long, val detail: String)

/** Proxy reachability probe (FR-1): `GET {proxyUrl}/verify` must answer 405. Never throws; logs nothing. */
class ProxyProbe(
    private val proxyUrl: String,
    client: OkHttpClient = OkHttpClient(),
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
    timeoutMs: Long = CloudVerifier.TIMEOUT_MS
) {
    private val client = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()

    suspend fun probe(): ProbeResult {
        val start = clockMs()
        if (proxyUrl.isBlank()) return ProbeResult(false, 0, VerdictReason.NETWORK)
        val detail =
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(proxyUrl.trimEnd('/') + "/verify").get().build()
                    client.newCall(request).execute().use { VerdictReason.http(it.code) }
                } catch (e: java.io.InterruptedIOException) {
                    VerdictReason.TIMEOUT
                } catch (e: IOException) {
                    VerdictReason.NETWORK
                } catch (e: IllegalArgumentException) {
                    VerdictReason.NETWORK // malformed URL
                }
            }
        return ProbeResult(detail == VerdictReason.http(HTTP_METHOD_NOT_ALLOWED), clockMs() - start, detail)
    }

    private companion object {
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val NANOS_PER_MS = 1_000_000L
    }
}
