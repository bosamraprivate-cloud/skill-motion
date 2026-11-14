package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Step
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportingVerifierTest {
    private fun reportsFor(reason: String): List<Boolean> = mutableListOf<Boolean>().also { reports ->
        val inner =
            object : Verifier {
                override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext) =
                    Verdict(Outcome.CANNOT_TELL, reason, latencyMs = 1)
            }
        runTest {
            val v = ReportingVerifier(inner, { 7L }) { gen, failed ->
                assertEquals(7L, gen)
                reports += failed
            }.verify(Step("s", Method.CLOUD), emptyList(), emptyList(), VerifyContext())
            assertEquals(reason, v.reason)
        }
    }

    @Test
    fun `cloud failures report failed`() = VerdictReason.CLOUD_FAILURES.forEach { assertEquals(it, listOf(true), reportsFor(it)) }

    @Test
    fun `model verdict reports success`() = assertEquals(listOf(false), reportsFor(VerdictReason.MODEL))

    @Test
    fun `no call made reports nothing`() = assertEquals(emptyList<Boolean>(), reportsFor(VerdictReason.OUT_OF_FRAME))
}
