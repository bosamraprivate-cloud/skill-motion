package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Step

/**
 * Wraps the cloud verifier and reports whether each proxy call failed (Story 3.6 network banner). A verdict with a
 * cloud failure code reports failed; a model verdict reports success; a verdict decided before any call (for example
 * `out_of_frame`) reports nothing. The generation lets the status ignore a failure from a call that started before the
 * device came back online. The verdict passes through unchanged.
 */
class ReportingVerifier(
    private val inner: Verifier,
    /** Called as the call starts; returns the generation passed back to [report]. */
    private val begin: () -> Long,
    private val report: (generation: Long, failed: Boolean) -> Unit
) : Verifier {
    override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict {
        val generation = begin()
        val verdict = inner.verify(step, frames, landmarks, ctx)
        when (verdict.reason) {
            in VerdictReason.CLOUD_FAILURES -> report(generation, true)
            VerdictReason.MODEL -> report(generation, false)
        }
        return verdict
    }
}
