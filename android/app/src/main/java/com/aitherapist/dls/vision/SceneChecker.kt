package com.aitherapist.dls.vision

import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** A box in normalised, un-mirrored frame coordinates (x, y = top-left). */
data class SceneBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    /** As a stored zone (left, top, right, bottom), clamped to the frame; null when empty. */
    fun toZone(): Zone? {
        val l = x.coerceIn(0f, 1f)
        val t = y.coerceIn(0f, 1f)
        val r = (x + w).coerceIn(0f, 1f)
        val b = (y + h).coerceIn(0f, 1f)
        return if (r > l && b > t) Zone(l, t, r, b) else null
    }
}

data class SceneObject(val kind: String, val box: SceneBox, val confidence: Float)

/** Fixed issue codes of `POST /scene` (proxy README). */
enum class SceneIssueCode(val wire: String) {
    MISSING_OBJECT("missing_object"),
    PARTLY_VISIBLE("partly_visible"),
    TOO_DARK("too_dark"),
    PERSON_IN_VIEW("person_in_view"),
    CAMERA_TILTED("camera_tilted"),
    TOO_FAR("too_far"),
    TOO_CLOSE("too_close"),
    UNCLEAR("unclear");

    companion object {
        fun of(wire: String): SceneIssueCode? = entries.firstOrNull { it.wire == wire }
    }
}

data class SceneIssue(val code: SceneIssueCode, val kind: String? = null)

sealed interface SceneResult {
    data class Checked(
        val ok: Boolean,
        val objects: List<SceneObject>,
        val issues: List<SceneIssue>,
        val modelId: String,
        val promptVersion: String
    ) : SceneResult

    /** Network, timeout, refusal, bad answer or not configured; [reason] is a fixed code only. */
    data class Unavailable(val reason: String) : SceneResult
}

/**
 * AI scene check client (Story 6.4): one face-blurred JPEG plus the required kinds to `POST {proxy}/scene`, with the
 * same auth as `/verify`. Never throws (except cancellation); logs nothing.
 */
class SceneChecker(
    private val proxyUrl: String,
    private val sharedSecret: String,
    client: OkHttpClient = OkHttpClient(),
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
    timeoutMs: Long = TIMEOUT_MS
) {
    private val client = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()

    suspend fun check(jpeg: ByteArray, kinds: List<String>, locale: String = LOCALE): SceneResult {
        val url = (proxyUrl.trimEnd('/') + PATH).toHttpUrlOrNull() ?: return SceneResult.Unavailable(VerdictReason.NETWORK)
        if (sharedSecret.isBlank() || kinds.isEmpty()) return SceneResult.Unavailable(VerdictReason.NETWORK)
        val body =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(FIELD_REQUEST_ID, newRequestId())
                .addFormDataPart(FIELD_KINDS, kinds.distinct().joinToString(","))
                .addFormDataPart(FIELD_LOCALE, locale)
                .addFormDataPart(FIELD_IMAGE, "scene.jpg", jpeg.toRequestBody(JPEG))
                .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $sharedSecret").post(body).build()
        return try {
            call(request)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            SceneResult.Unavailable(VerdictReason.NETWORK)
        }
    }

    private suspend fun call(request: Request): SceneResult = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resume(SceneResult.Unavailable(if (e is InterruptedIOException) VerdictReason.TIMEOUT else VerdictReason.NETWORK))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result =
                        try {
                            response.use {
                                if (it.code !=
                                    HTTP_OK
                                ) {
                                    SceneResult.Unavailable(VerdictReason.REFUSED)
                                } else {
                                    parse(it.body.string())
                                }
                            }
                        } catch (e: InterruptedIOException) {
                            SceneResult.Unavailable(VerdictReason.TIMEOUT)
                        } catch (e: IOException) {
                            SceneResult.Unavailable(VerdictReason.NETWORK)
                        }
                    cont.resume(result)
                }
            }
        )
    }

    @Serializable
    private data class WireBox(val x: Float? = null, val y: Float? = null, val w: Float? = null, val h: Float? = null)

    @Serializable
    private data class WireObject(val kind: String? = null, val box: WireBox? = null, val confidence: Float? = null)

    @Serializable
    private data class WireIssue(val code: String? = null, val kind: String? = null)

    @Serializable
    private data class WireScene(
        val ok: Boolean? = null,
        val objects: List<WireObject>? = null,
        val issues: List<WireIssue>? = null,
        val modelId: String? = null,
        val promptVersion: String? = null
    )

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val LOCALE = "ar-EG"

        // Wire contract (proxy README "POST /scene"); keep in one place.
        const val PATH = "/scene"
        const val FIELD_REQUEST_ID = "requestId"
        const val FIELD_KINDS = "kinds"
        const val FIELD_LOCALE = "locale"
        const val FIELD_IMAGE = "image"

        private const val HTTP_OK = 200
        private val JPEG = "image/jpeg".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses and validates a 200 body; anything malformed is Unavailable(bad_answer). Unknown issue codes are dropped. */
        internal fun parse(text: String): SceneResult {
            val bad = SceneResult.Unavailable(VerdictReason.BAD_ANSWER)
            val wire =
                try {
                    json.decodeFromString(WireScene.serializer(), text)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    return bad
                }
            val ok = wire.ok ?: return bad
            val modelId = wire.modelId?.takeIf { it.isNotBlank() } ?: return bad
            val prompt = wire.promptVersion?.takeIf { it.isNotBlank() } ?: return bad
            val objects =
                (wire.objects ?: return bad).map { o ->
                    val b = o.box ?: return bad
                    val x = b.x ?: return bad
                    val y = b.y ?: return bad
                    val w = b.w ?: return bad
                    val h = b.h ?: return bad
                    val kind = o.kind?.takeIf { it.isNotBlank() } ?: return bad
                    if (listOf(x, y, w, h).any { it.isNaN() || it < 0f || it > 1f } || w <= 0f || h <= 0f) return bad
                    SceneObject(kind, SceneBox(x, y, w, h), (o.confidence ?: 0f).coerceIn(0f, 1f))
                }
            val issues = wire.issues.orEmpty().mapNotNull { i -> i.code?.let(SceneIssueCode::of)?.let { SceneIssue(it, i.kind) } }
            return SceneResult.Checked(ok, objects, issues, modelId, prompt)
        }
    }
}
