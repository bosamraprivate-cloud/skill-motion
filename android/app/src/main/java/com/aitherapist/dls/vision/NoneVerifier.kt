package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Step

/** Verifier for `method: none` steps: never judgeable, so the step is saved as not scored. */
object NoneVerifier : Verifier {
    override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict =
        Verdict(outcome = Outcome.CANNOT_TELL, reason = VerdictReason.NOT_JUDGEABLE, latencyMs = 0)
}
