package com.aitherapist.dls.vision

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Probe result: [reachable] when `GET /verify` answered 405; [detail] is `http_<status>`, `network` or `timeout`. */
data class ProbeResult(val reachable: Boolean, val roundTripMs: Long, val detail: String)

/** Health-ping outcome shown on Diagnostics (Story 5.5). */
enum class ProxyHealth { OK, REJECTED_SECRET, NOT_CONFIGURED, SETTINGS_UNREADABLE, OFFLINE, TIMEOUT, UNEXPECTED }

/** Health ping result; [status] is the HTTP status when the proxy answered. */
data class HealthResult(val health: ProxyHealth, val roundTripMs: Long, val status: Int? = null)

/**
 * Maps a health-ping answer: 401 = wrong secret; 400 with JSON `{"error":"malformed_body"}` = healthy (auth passed,
 * nothing sent upstream). Anything else, 2xx included (likely a captive portal), is unexpected.
 */
fun proxyHealthFor(status: Int, body: String?): ProxyHealth = when {
    status == HTTP_UNAUTHORIZED -> ProxyHealth.REJECTED_SECRET
    status == HTTP_BAD_REQUEST && errorCode(body) == MALFORMED_BODY -> ProxyHealth.OK
    else -> ProxyHealth.UNEXPECTED
}

private fun errorCode(body: String?): String? = try {
    (Json.parseToJsonElement(body.orEmpty()) as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_BAD_REQUEST = 400
private const val MALFORMED_BODY = "malformed_body"
private const val MAX_BODY_CHARS = 1_024L

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

    /**
     * Health ping (Story 5.5): `POST {proxyUrl}/verify` with the shared secret and an empty text body, so no frames
     * and no child data are sent and the proxy never calls the model. Never throws; logs nothing.
     */
    suspend fun health(sharedSecret: String): HealthResult {
        if (proxyUrl.isBlank() || sharedSecret.isBlank()) return HealthResult(ProxyHealth.NOT_CONFIGURED, 0)
        val start = clockMs()
        val request =
            try {
                Request
                    .Builder()
                    .url(proxyUrl.trimEnd('/') + "/verify")
                    .header("Authorization", "Bearer $sharedSecret")
                    .post(ByteArray(0).toRequestBody("text/plain".toMediaType()))
                    .build()
            } catch (e: IllegalArgumentException) {
                return HealthResult(ProxyHealth.NOT_CONFIGURED, 0) // malformed URL
            }
        val (health, status) =
            try {
                val (code, body) = client.newCall(request).await()
                proxyHealthFor(code, body) to code
            } catch (e: java.io.InterruptedIOException) {
                ProxyHealth.TIMEOUT to null
            } catch (e: IOException) {
                ProxyHealth.OFFLINE to null
            }
        return HealthResult(health, clockMs() - start, status)
    }

    /** Enqueues the call and cancels it when the coroutine is cancelled; returns status and a capped body. */
    private suspend fun Call.await(): Pair<Int, String?> = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result =
                        response.use {
                            it.code to runCatching { it.peekBody(MAX_BODY_CHARS).string() }.getOrNull()
                        }
                    cont.resume(result)
                }
            }
        )
    }

    private companion object {
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val NANOS_PER_MS = 1_000_000L
    }
}
