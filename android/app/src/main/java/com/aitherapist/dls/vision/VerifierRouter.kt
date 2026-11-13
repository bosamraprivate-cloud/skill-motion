package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Step
import kotlin.coroutines.cancellation.CancellationException

/**
 * Picks the verifier for a step by its `method` (AD-3): `none` -> [NoneVerifier]; otherwise the [VisibilityGate] runs
 * first (more than half of the window's frames must carry a paired pose result, else `out_of_frame`), then
 * [pose] / [hand] / the cloud verifier. Setup zones come from [zones] when the context carries none. A missing cloud
 * configuration becomes cannot tell `network`; any exception becomes cannot tell `verifier_error`. Never throws
 * (except cancellation) and never returns `NOT_DONE` by itself.
 */
class VerifierRouter(
    private val pose: Verifier,
    private val hand: Verifier,
    /** The cloud verifier for the current proxy settings, or null when the proxy is not set up. */
    private val cloud: suspend () -> Verifier?,
    private val zones: suspend () -> Map<String, Zone> = { emptyMap() }
) : Verifier {
    override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict {
        if (step.method == Method.NONE) return NoneVerifier.verify(step, frames, landmarks, ctx)
        if (!VisibilityGate.passes(landmarks, listOf(emptySet()))) return cannotTell(VerdictReason.OUT_OF_FRAME)
        return try {
            val context = if (ctx.zones.isEmpty()) ctx.copy(zones = zones()) else ctx
            val chosen =
                when (step.method) {
                    Method.POSE -> pose
                    Method.HAND -> hand
                    Method.CLOUD -> cloud() ?: return cannotTell(VerdictReason.NETWORK)
                    Method.NONE -> NoneVerifier
                }
            chosen.verify(step, frames, landmarks, context)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            cannotTell(VerdictReason.VERIFIER_ERROR)
        }
    }

    private fun cannotTell(reason: String) = Verdict(Outcome.CANNOT_TELL, reason, latencyMs = 0)
}
