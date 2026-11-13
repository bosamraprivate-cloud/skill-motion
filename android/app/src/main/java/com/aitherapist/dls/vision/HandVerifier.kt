package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Step
import kotlin.coroutines.cancellation.CancellationException

/**
 * Local verifier for `method: hand` steps (AD-3). Delegates to [CheckEvaluator] (handsRubbing; other check
 * types take the body path). Zones come from [VerifyContext]. Never throws.
 */
class HandVerifier(private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MS }) : Verifier {
    override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict {
        val start = clockMs()
        val (outcome, reason) =
            try {
                evaluate(step, landmarks, ctx.zones)
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
    fun evaluate(step: Step, landmarks: List<LandmarkFrame>, zones: Map<String, Zone> = emptyMap()): Pair<Outcome, String> =
        CheckEvaluator.evaluate(step.check, landmarks, zones)

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
