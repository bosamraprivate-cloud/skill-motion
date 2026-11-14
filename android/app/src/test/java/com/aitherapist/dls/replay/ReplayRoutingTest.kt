package com.aitherapist.dls.replay

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.catalog.Step
import com.aitherapist.dls.vision.Frame
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.Outcome
import com.aitherapist.dls.vision.Verdict
import com.aitherapist.dls.vision.VerdictReason
import com.aitherapist.dls.vision.Verifier
import com.aitherapist.dls.vision.VerifyContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayRoutingTest {
    private val frames = (0L until 10L).map { t -> LandmarkFrame(t * 100, List(33) { LandmarkPoint(0.5f, 0.5f, 0.9f) }) }

    @Test
    fun `zone steps are cannot tell no_zone because replay uses no setup zones`() = runTest {
        val step = Step("s", Method.POSE, Check.InZone(LandmarkRef.Lm("WRIST"), "mat", 0.3, Side.EITHER))
        val v = replayRouter { null }.verify(step, emptyList(), frames, VerifyContext())
        assertEquals(Outcome.CANNOT_TELL, v.outcome)
        assertEquals(VerdictReason.NO_ZONE, v.reason)
    }

    @Test
    fun `cloud steps use the given verifier directly and no proxy means network`() = runTest {
        var calls = 0
        val cloud =
            object : Verifier {
                override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict {
                    calls++
                    return Verdict(Outcome.DONE, VerdictReason.MODEL, latencyMs = 1)
                }
            }
        val step = Step("c", Method.CLOUD)
        assertEquals(Outcome.DONE, replayRouter { cloud }.verify(step, emptyList(), frames, VerifyContext()).outcome)
        assertEquals(1, calls)
        assertEquals(VerdictReason.NETWORK, replayRouter { null }.verify(step, emptyList(), frames, VerifyContext()).reason)
    }
}
