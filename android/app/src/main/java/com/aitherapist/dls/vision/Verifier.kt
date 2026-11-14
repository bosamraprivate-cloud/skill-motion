package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Step
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Outcome {
    @SerialName("done")
    DONE,

    @SerialName("not_done")
    NOT_DONE,

    @SerialName("cannot_tell")
    CANNOT_TELL
}

/**
 * Machine reason codes for verdicts (AD-3 Conventions), the single place they are defined. Cloud failures use
 * [NETWORK], [TIMEOUT], [REFUSED] and [BAD_ANSWER]; a verdict the model gave uses [MODEL] and keeps the model's
 * free text in [Verdict.modelReason].
 */
object VerdictReason {
    const val OUT_OF_FRAME = "out_of_frame"
    const val NO_ZONE = "no_zone"
    const val NOT_JUDGEABLE = "not_judgeable"
    const val CHECK_PASSED = "check_passed"
    const val CHECK_FAILED = "check_failed"
    const val TIMEOUT = "timeout"
    const val NETWORK = "network"

    /** The model refused or the proxy answered with an HTTP error status. */
    const val REFUSED = "refused"

    /** A 200 answer that is not a well-formed verdict with provenance. */
    const val BAD_ANSWER = "bad_answer"

    /** The verdict came from the model; its free-text reason is in [Verdict.modelReason]. */
    const val MODEL = "model"
    const val VERIFIER_ERROR = "verifier_error"

    /** Proxy probe detail only (Settings); verdicts use [REFUSED]. */
    fun http(status: Int) = "http_$status"

    /** Reason codes a cloud verdict can carry when the network or proxy failed. */
    val CLOUD_FAILURES = setOf(NETWORK, TIMEOUT, REFUSED, BAD_ANSWER)
}

@Serializable
data class Verdict(
    val outcome: Outcome,
    val reason: String,
    val modelId: String? = null,
    val promptVersion: String? = null,
    val latencyMs: Long,
    /** The model's short free-text reason, set only when [reason] is [VerdictReason.MODEL]. */
    val modelReason: String? = null
)

/**
 * An attempt frame held in memory only (never written to disk or logged). The platform wrapper
 * (a Bitmap in shell code) is released by the attempt's owner after the verdict.
 */
interface AttemptImage {
    val width: Int
    val height: Int

    fun release()
}

/** A captured camera frame at [timestampMs]; [image] is the upright, un-mirrored inference frame. */
class Frame(val timestampMs: Long, val image: AttemptImage? = null)

/**
 * Landmarks paired with the frame at [timestampMs] (AD-3). [pose] is null when no pose result was
 * paired within 100 ms (the frame counts as not visible). Points are in the un-mirrored inference frame.
 */
data class LandmarkFrame(val timestampMs: Long, val pose: List<LandmarkPoint>?, val hands: List<List<LandmarkPoint>> = emptyList())

/** A setup zone, normalised and un-mirrored. */
data class Zone(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class VerifyContext(val zones: Map<String, Zone> = emptyMap())

/** Single verifier contract (AD-3). Implementations never throw. */
interface Verifier {
    suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict
}
