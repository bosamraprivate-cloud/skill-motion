package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.Step
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.math.abs
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

/**
 * Cloud verifier for object steps (AD-3, AD-7, FR-19/20). Gates on face visibility, picks the start,
 * middle and end visible frames, crops and encodes them, and sends one multipart request to the proxy.
 * Every failure becomes `CANNOT_TELL` with a fixed reason code (`network`, `timeout`, `refused`, `bad_answer`); `NOT_DONE` comes only from a 200 `not_done`. Never
 * throws (except coroutine cancellation). Logs nothing.
 */
class CloudVerifier(
    private val proxyUrl: String,
    private val sharedSecret: String,
    private val encoder: JpegEncoder,
    client: OkHttpClient = OkHttpClient(),
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
    timeoutMs: Long = TIMEOUT_MS
) : Verifier {
    private val client = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()

    override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict {
        val start = clockMs()
        val verdict =
            try {
                judge(step, frames, landmarks, ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Result(Outcome.CANNOT_TELL, VerdictReason.NOT_JUDGEABLE)
            } catch (e: OutOfMemoryError) {
                Result(Outcome.CANNOT_TELL, VerdictReason.NOT_JUDGEABLE)
            }
        return Verdict(verdict.outcome, verdict.reason, verdict.modelId, verdict.promptVersion, clockMs() - start, verdict.modelReason)
    }

    private suspend fun judge(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Result {
        val question = (step.check as? Check.Question)?.text ?: return Result(Outcome.CANNOT_TELL, VerdictReason.NOT_JUDGEABLE)
        if (!VisibilityGate.passes(
                landmarks,
                listOf(FrameCropper.FACE_LANDMARKS)
            )
        ) {
            return Result(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME)
        }
        val picked = pickFrames(frames, landmarks) ?: return Result(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME)
        val url =
            (proxyUrl.trimEnd('/') + "/verify").toHttpUrlOrNull()
                ?: return Result(Outcome.CANNOT_TELL, VerdictReason.NETWORK)
        if (sharedSecret.isBlank()) return Result(Outcome.CANNOT_TELL, VerdictReason.NETWORK)
        val crops =
            picked.map { (frame, lm) ->
                val image = requireNotNull(frame.image)
                val box =
                    FrameCropper.cropBox(image.width, image.height, FrameCropper.cropPoints(lm), ctx.zones.values)
                        ?: return Result(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME) // never upload an uncropped frame
                val face = FaceBlur.faceBox(lm.pose, ctx.zones[ScenePlan.PERSON])
                Triple(image, box, listOfNotNull(face?.let { FaceBlur.inCrop(it, image.width, image.height, box) }))
            }
        // Faces are pixelated on device before upload (Story 6.4); crops already include the scene-check zones.
        val jpegs = crops.map { (image, box, blur) ->
            encoder.encode(image, box, FrameCropper.scaledSize(box), FrameCropper.JPEG_QUALITY, blur)
        }
        val body =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("requestId", newRequestId())
                .addFormDataPart("stepId", step.id)
                .addFormDataPart("question", question)
                .apply {
                    jpegs.forEachIndexed { i, bytes -> addFormDataPart("image", "frame$i.jpg", bytes.toRequestBody(JPEG)) }
                }.build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer $sharedSecret")
                .post(body)
                .build()
        return call(request)
    }

    /** One async call; coroutine cancellation cancels the HTTP call. */
    private suspend fun call(request: Request): Result = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val reason = if (e is InterruptedIOException) VerdictReason.TIMEOUT else VerdictReason.NETWORK
                    cont.resume(Result(Outcome.CANNOT_TELL, reason))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result =
                        try {
                            response.use {
                                if (it.code !=
                                    HTTP_OK
                                ) {
                                    Result(Outcome.CANNOT_TELL, VerdictReason.REFUSED)
                                } else {
                                    parse(it.body.string())
                                }
                            }
                        } catch (e: InterruptedIOException) {
                            Result(Outcome.CANNOT_TELL, VerdictReason.TIMEOUT)
                        } catch (e: IOException) {
                            Result(Outcome.CANNOT_TELL, VerdictReason.NETWORK)
                        }
                    cont.resume(result)
                }
            }
        )
    }

    private fun parse(text: String): Result {
        val body =
            try {
                json.decodeFromString(ProxyVerdict.serializer(), text)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                null
            }
        val outcome = body?.verdict?.let { v -> Outcome.entries.firstOrNull { it.serialName == v } }
        if (body == null ||
            outcome == null ||
            body.reason.isNullOrBlank() ||
            body.modelId.isNullOrBlank() ||
            body.promptVersion.isNullOrBlank()
        ) {
            return Result(Outcome.CANNOT_TELL, VerdictReason.BAD_ANSWER, body?.modelId, body?.promptVersion)
        }
        return Result(outcome, VerdictReason.MODEL, body.modelId, body.promptVersion, body.reason)
    }

    /** Start, middle and end visible frames by timestamp, each with its paired landmarks; null if fewer than 3. */
    private fun pickFrames(frames: List<Frame>, landmarks: List<LandmarkFrame>): List<Pair<Frame, LandmarkFrame>>? {
        val visible =
            frames
                .filter { it.image != null }
                .sortedBy { it.timestampMs }
                .mapNotNull { frame ->
                    val lm = nearest(landmarks, frame.timestampMs)
                    if (lm != null && VisibilityGate.visibleIn(lm, FrameCropper.FACE_LANDMARKS)) frame to lm else null
                }
        if (visible.size < IMAGE_COUNT) return null
        return listOf(visible.first(), visible[visible.size / 2], visible.last())
    }

    private fun nearest(landmarks: List<LandmarkFrame>, ts: Long): LandmarkFrame? =
        landmarks.filter { abs(it.timestampMs - ts) <= PAIR_WINDOW_MS }.minByOrNull { abs(it.timestampMs - ts) }

    private data class Result(
        val outcome: Outcome,
        val reason: String,
        val modelId: String? = null,
        val promptVersion: String? = null,
        val modelReason: String? = null
    )

    @Serializable
    private data class ProxyVerdict(
        val verdict: String? = null,
        val reason: String? = null,
        val modelId: String? = null,
        val promptVersion: String? = null
    )

    companion object {
        const val TIMEOUT_MS = 8_000L
        const val IMAGE_COUNT = 3
        private const val PAIR_WINDOW_MS = 100L
        private const val HTTP_OK = 200
        private const val NANOS_PER_MS = 1_000_000L
        private val JPEG = "image/jpeg".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        private val Outcome.serialName: String get() = name.lowercase()
    }
}
