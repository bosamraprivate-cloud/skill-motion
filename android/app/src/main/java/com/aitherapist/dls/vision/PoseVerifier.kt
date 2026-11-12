package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.catalog.Step
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToLong

/**
 * Local verifier for pose steps (AD-3, FR-18). Runs [VisibilityGate] first, then evaluates the step's
 * check over the attempt's landmark frames. This story evaluates only `distanceBelow`; every other
 * check type returns `CANNOT_TELL` / `not_judgeable`. Never throws.
 */
class PoseVerifier(private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MS }) : Verifier {
    override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict {
        val start = clockMs()
        val (outcome, reason) =
            try {
                evaluate(step, landmarks)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE
            }
        return Verdict(outcome, reason, latencyMs = clockMs() - start)
    }

    /** Pure evaluation; may throw on malformed content (caught by [verify]). */
    fun evaluate(step: Step, landmarks: List<LandmarkFrame>): Pair<Outcome, String> {
        val check = step.check as? Check.DistanceBelow ?: return Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE
        val sides = candidateSides(check.side)
        val alternatives =
            sides.map { side ->
                setOf(PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER) +
                    indices(check.a, side) + indices(check.b, side)
            }
        val visibleSides = sides.zip(alternatives).filter { (_, needed) -> VisibilityGate.passes(landmarks, listOf(needed)) }
        if (visibleSides.isEmpty()) return Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME

        val holdMs = (check.holdSec * MS_PER_SEC).roundToLong()
        val ordered = landmarks.sortedBy { it.timestampMs }
        val met =
            visibleSides.any { (side, needed) ->
                holdsContinuously(ordered, holdMs) { frame ->
                    VisibilityGate.visibleIn(frame, needed) && distanceBelow(frame.pose!!, check, side)
                }
            }
        if (met) return Outcome.DONE to VerdictReason.CHECK_PASSED
        // Doubt is never failure: an unseen stretch long enough to hide the hold means we cannot tell.
        val seen: (LandmarkFrame) -> Boolean = { frame -> visibleSides.any { (_, needed) -> VisibilityGate.visibleIn(frame, needed) } }
        if (holdsContinuously(ordered, holdMs) { !seen(it) }) return Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME
        val measurable = ordered.any { seen(it) && shoulderWidth(it.pose!!) > 0.0 }
        if (!measurable) return Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE
        return Outcome.NOT_DONE to VerdictReason.CHECK_FAILED
    }

    private fun shoulderWidth(pose: List<LandmarkPoint>) =
        Geometry.shoulderWidth(pose[PoseLandmarkIndex.LEFT_SHOULDER], pose[PoseLandmarkIndex.RIGHT_SHOULDER])

    private fun distanceBelow(pose: List<LandmarkPoint>, check: Check.DistanceBelow, side: Side?): Boolean {
        val width = shoulderWidth(pose)
        val distance = Geometry.distance(point(pose, check.a, side), point(pose, check.b, side))
        val ratio = Geometry.ratio(distance, width) ?: return false // zero shoulder width: frame does not pass
        return ratio <= check.ratio
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val MS_PER_SEC = 1_000.0
    }
}

/** Largest gap between consecutive frames still treated as continuous (above ~100 ms at 10 fps). */
internal const val MAX_GAP_MS = 250L

/**
 * True when [passes] holds on consecutive frames spanning at least [holdMs]; a failing frame or a gap
 * above [MAX_GAP_MS] between frames resets the run.
 */
internal fun holdsContinuously(ordered: List<LandmarkFrame>, holdMs: Long, passes: (LandmarkFrame) -> Boolean): Boolean {
    var runStart: Long? = null
    var previousTs: Long? = null
    for (frame in ordered) {
        if (previousTs != null && frame.timestampMs - previousTs > MAX_GAP_MS) runStart = null
        previousTs = frame.timestampMs
        if (passes(frame)) {
            val startTs = runStart ?: frame.timestampMs
            runStart = startTs
            if (frame.timestampMs - startTs >= holdMs) return true
        } else {
            runStart = null
        }
    }
    return false
}

/** `either` tries both sides; `left`/`right` one; no side means names are already sided. */
internal fun candidateSides(side: Side?): List<Side?> = when (side) {
    Side.EITHER -> listOf(Side.LEFT, Side.RIGHT)
    null -> listOf(null)
    else -> listOf(side)
}

/** Resolves a landmark name to its pose index; a side-less name (`WRIST`) takes LEFT_/RIGHT_ from [side]. */
internal fun resolveIndex(name: String, side: Side?): Int {
    PoseLandmarkIndex.byName[name]?.let { return it }
    val prefix =
        when (side) {
            Side.LEFT -> "LEFT_"
            Side.RIGHT -> "RIGHT_"
            else -> throw IllegalArgumentException("side-less landmark needs side left or right")
        }
    return requireNotNull(PoseLandmarkIndex.byName[prefix + name]) { "unknown pose landmark" }
}

internal fun indices(ref: LandmarkRef, side: Side?): Set<Int> = when (ref) {
    is LandmarkRef.Lm -> setOf(resolveIndex(ref.lm, side))
    is LandmarkRef.Mid -> ref.mid.map { resolveIndex(it, side) }.toSet()
}

internal fun point(pose: List<LandmarkPoint>, ref: LandmarkRef, side: Side?): LandmarkPoint = when (ref) {
    is LandmarkRef.Lm -> pose[resolveIndex(ref.lm, side)]
    is LandmarkRef.Mid -> Geometry.midpoint(pose[resolveIndex(ref.mid[0], side)], pose[resolveIndex(ref.mid[1], side)])
}
