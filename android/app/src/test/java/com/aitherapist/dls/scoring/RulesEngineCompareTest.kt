package com.aitherapist.dls.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesEngineCompareTest {
    private val tags =
        mapOf(
            "s1" to listOf("Hand skills"),
            "s2" to listOf("Hand skills", "Visual-motor coordination"),
            "s3" to listOf("Motor coordination", "Visual-motor coordination"),
            "s4" to listOf("Midline crossing"),
            "s5" to emptyList()
        )

    @Test
    fun `per-domain averages over common scored steps and difference`() {
        val a = mapOf("s1" to StepLevel.ONE, "s2" to StepLevel.TWO, "s3" to StepLevel.TWO, "s4" to StepLevel.THREE)
        val b = mapOf("s1" to StepLevel.THREE, "s2" to StepLevel.TWO, "s3" to StepLevel.THREE, "s4" to StepLevel.TWO)
        val result = RulesEngine.compare(a, b, tags)
        assertEquals(4, result.commonSteps)
        val hand = result.domains.single { it.domain == "Hand skills" }
        assertEquals(1.5, hand.averageA, 1e-9)
        assertEquals(2.5, hand.averageB, 1e-9)
        assertEquals(1.0, hand.difference, 1e-9)
        assertEquals(2, hand.steps)
        // Visual-motor folds into Motor coordination, counted once per step (s2 and s3).
        val motor = result.domains.single { it.domain == RulesEngine.MOTOR_COORDINATION }
        assertEquals(2, motor.steps)
        assertEquals(2.0, motor.averageA, 1e-9)
        assertEquals(2.5, motor.averageB, 1e-9)
        assertEquals(-1.0, result.domains.single { it.domain == "Midline crossing" }.difference, 1e-9)
        assertTrue(result.domains.none { it.domain == RulesEngine.VISUAL_MOTOR_COORDINATION })
    }

    @Test
    fun `not scored open or missing in one session is excluded`() {
        val a = mapOf("s1" to StepLevel.NOT_SCORED, "s2" to null, "s4" to StepLevel.THREE, "s5" to StepLevel.TWO)
        val b = mapOf("s1" to StepLevel.THREE, "s2" to StepLevel.TWO, "s3" to StepLevel.ONE, "s4" to StepLevel.ONE, "s5" to StepLevel.THREE)
        val result = RulesEngine.compare(a, b, tags)
        assertEquals(2, result.commonSteps) // s4 and the untagged s5
        assertEquals(listOf("Midline crossing"), result.domains.map { it.domain })
    }

    @Test
    fun `no common scored steps gives no domains`() {
        val result = RulesEngine.compare(mapOf("s1" to StepLevel.THREE), mapOf("s4" to StepLevel.THREE), tags)
        assertEquals(0, result.commonSteps)
        assertTrue(result.domains.isEmpty())
    }

    @Test
    fun `step tagged with both Visual-motor and Motor counts once in Motor coordination`() {
        val result = RulesEngine.compare(mapOf("s3" to StepLevel.ONE), mapOf("s3" to StepLevel.THREE), tags)
        val motor = result.domains.single()
        assertEquals(RulesEngine.MOTOR_COORDINATION, motor.domain)
        assertEquals(1, motor.steps)
        assertEquals(1.0, motor.averageA, 1e-9)
        assertEquals(3.0, motor.averageB, 1e-9)
        assertEquals(2.0, motor.difference, 1e-9)
    }

    @Test
    fun `small real differences are kept unrounded`() {
        val result = RulesEngine.compare(mapOf("s4" to StepLevel.TWO), mapOf("s4" to StepLevel.TWO), tags)
        assertEquals(0.0, result.domains.single().difference, 0.0)
    }
}
