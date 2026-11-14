package com.aitherapist.dls.scoring

import com.aitherapist.dls.scoring.RuleOutcome.CANNOT_TELL
import com.aitherapist.dls.scoring.RuleOutcome.DONE
import com.aitherapist.dls.scoring.RuleOutcome.NOT_DONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesEngineTest {
    private fun row(number: Int, level: Int, outcome: RuleOutcome, none: Boolean = false) = RuleAttempt(number, level, outcome, none)

    @Test
    fun `done on first try at level 1 is three`() = assertEquals(StepLevel.THREE, RulesEngine.stepLevel(listOf(row(1, 1, DONE))))

    @Test
    fun `done on first number after help is two`() = assertEquals(StepLevel.TWO, RulesEngine.stepLevel(listOf(row(1, 2, DONE))))

    @Test
    fun `done on attempt 2 or 3 is two`() {
        assertEquals(StepLevel.TWO, RulesEngine.stepLevel(listOf(row(1, 1, NOT_DONE), row(2, 2, DONE))))
        assertEquals(StepLevel.TWO, RulesEngine.stepLevel(listOf(row(1, 1, NOT_DONE), row(2, 2, NOT_DONE), row(3, 3, DONE))))
    }

    @Test
    fun `three not done numbers is one`() =
        assertEquals(StepLevel.ONE, RulesEngine.stepLevel(listOf(row(1, 1, NOT_DONE), row(2, 2, NOT_DONE), row(3, 3, NOT_DONE))))

    @Test
    fun `cannot tell and its repeat are not scored`() =
        assertEquals(StepLevel.NOT_SCORED, RulesEngine.stepLevel(listOf(row(1, 1, CANNOT_TELL), row(2, 1, CANNOT_TELL))))

    @Test
    fun `old same-number cannot tell rows are still not scored`() =
        assertEquals(StepLevel.NOT_SCORED, RulesEngine.stepLevel(listOf(row(1, 1, CANNOT_TELL), row(1, 1, CANNOT_TELL))))

    @Test
    fun `non consecutive cannot tell is still open`() =
        assertNull(RulesEngine.stepLevel(listOf(row(1, 1, CANNOT_TELL), row(2, 1, NOT_DONE), row(3, 2, CANNOT_TELL))))

    @Test
    fun `cannot tell then done on the repeat is still three`() =
        assertEquals(StepLevel.THREE, RulesEngine.stepLevel(listOf(row(1, 1, CANNOT_TELL), row(2, 1, DONE))))

    @Test
    fun `done at level 1 after a not done is two`() =
        assertEquals(StepLevel.TWO, RulesEngine.stepLevel(listOf(row(1, 1, NOT_DONE), row(2, 1, DONE))))

    @Test
    fun `verifier none is not scored`() =
        assertEquals(StepLevel.NOT_SCORED, RulesEngine.stepLevel(listOf(row(1, 1, CANNOT_TELL, none = true))))

    @Test
    fun `open steps return null`() {
        assertNull(RulesEngine.stepLevel(emptyList()))
        assertNull(RulesEngine.stepLevel(listOf(row(1, 1, NOT_DONE), row(2, 2, NOT_DONE))))
    }

    @Test
    fun `correction wins`() = assertEquals(StepLevel.ONE, RulesEngine.stepLevel(listOf(row(1, 1, DONE)), StepLevel.ONE))

    // Rule 2 (scoring-rules.md)

    @Test
    fun `plan level boundaries are exact`() {
        assertEquals(PlanLevel.STRENGTH, RulesEngine.planLevel(3.0))
        assertEquals(PlanLevel.MILD, RulesEngine.planLevel(2.999))
        assertEquals(PlanLevel.MILD, RulesEngine.planLevel(2.5))
        assertEquals(PlanLevel.MODERATE, RulesEngine.planLevel(2.499))
        assertEquals(PlanLevel.MODERATE, RulesEngine.planLevel(1.5))
        assertEquals(PlanLevel.SEVERE, RulesEngine.planLevel(1.499))
        assertEquals(PlanLevel.SEVERE, RulesEngine.planLevel(1.0))
    }

    @Test
    fun `visual-motor and motor tags count once in motor coordination`() {
        val r = RulesEngine.domainAverages(
            mapOf("a" to StepLevel.ONE, "b" to StepLevel.THREE),
            mapOf("a" to listOf("Visual-motor coordination", "Motor coordination"), "b" to listOf("Visual-motor coordination"))
        )
        assertEquals(mapOf("Motor coordination" to 2.0), r.averages)
        assertEquals(2, r.scoredCounts["Motor coordination"])
    }

    @Test
    fun `multi-domain step counts once in each, not scored and open excluded, empty domain null`() {
        val r = RulesEngine.domainAverages(
            mapOf("a" to StepLevel.TWO, "b" to StepLevel.THREE, "c" to StepLevel.NOT_SCORED, "d" to null),
            mapOf(
                "a" to listOf("Hand skills", "Motor planning"),
                "b" to listOf("Hand skills"),
                "c" to listOf("Range of motion"),
                "d" to listOf("Range of motion")
            )
        )
        assertEquals(2.5, r.averages["Hand skills"]!!, 0.0)
        assertEquals(2.0, r.averages["Motor planning"]!!, 0.0)
        assertTrue(r.averages.containsKey("Range of motion"))
        assertNull(r.averages["Range of motion"])
        assertEquals(0, r.scoredCounts["Range of motion"])
        assertEquals(0, r.untaggedScored)
    }

    @Test
    fun `untagged scored steps feed no domain and are counted`() {
        val r = RulesEngine.domainAverages(mapOf("a" to StepLevel.ONE, "b" to StepLevel.NOT_SCORED), mapOf("a" to emptyList()))
        assertTrue(r.averages.isEmpty())
        assertEquals(1, r.untaggedScored)
    }

    @Test
    fun `correction wins over the automatic level, including not scored`() {
        val three = listOf(row(1, 1, DONE))
        assertEquals(StepLevel.THREE, RulesEngine.effectiveStepLevel(three, hasCorrection = false, correction = null))
        assertEquals(StepLevel.NOT_SCORED, RulesEngine.effectiveStepLevel(three, hasCorrection = true, correction = null))
        assertEquals(StepLevel.ONE, RulesEngine.effectiveStepLevel(three, hasCorrection = true, correction = 1))
        assertEquals(StepLevel.TWO, RulesEngine.effectiveStepLevel(emptyList(), hasCorrection = true, correction = 2))
        assertEquals(StepLevel.THREE, RulesEngine.correctionLevel(3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `correction level outside the rule 1 scores is rejected`() {
        RulesEngine.correctionLevel(0)
    }

    @Test
    fun `plan level from computed averages`() {
        val tags =
            mapOf(
                "a" to listOf("Hand skills"),
                "b" to listOf("Hand skills"),
                "c" to listOf("Motor planning"),
                "d" to listOf("Motor planning")
            )
        val levels = mapOf("a" to StepLevel.THREE, "b" to StepLevel.TWO, "c" to StepLevel.TWO, "d" to StepLevel.ONE)
        val r = RulesEngine.domainAverages(levels, tags)
        assertEquals(PlanLevel.MILD, RulesEngine.planLevel(r.averages["Hand skills"]!!))
        assertEquals(PlanLevel.MODERATE, RulesEngine.planLevel(r.averages["Motor planning"]!!))
    }

    @Test
    fun `scored steps missing from the catalogue are unknown, not untagged`() {
        val r = RulesEngine.domainAverages(mapOf("gone" to StepLevel.TWO, "u" to StepLevel.ONE), mapOf("u" to emptyList()))
        assertEquals(setOf("gone"), r.unknownSteps)
        assertEquals(1, r.untaggedScored)
        assertTrue(RulesEngine.isValidCorrection(null))
        assertTrue(RulesEngine.isValidCorrection(1))
        assertFalse(RulesEngine.isValidCorrection(0))
        assertFalse(RulesEngine.isValidCorrection(4))
    }

    @Test
    fun `scores follow rule 1`() {
        assertEquals(3, RulesEngine.score(StepLevel.THREE))
        assertEquals(2, RulesEngine.score(StepLevel.TWO))
        assertEquals(1, RulesEngine.score(StepLevel.ONE))
        assertNull(RulesEngine.score(StepLevel.NOT_SCORED))
    }

    @Test
    fun `strengths and needs split levels and keep domains`() {
        val tags =
            mapOf(
                "a" to listOf("Hand skills"),
                "b" to listOf("Motor coordination", "Visual-motor coordination", "Range of motion"),
                "c" to emptyList(),
                "d" to listOf("Hand skills"),
                "e" to listOf("Hand skills")
            )
        val levels =
            linkedMapOf(
                "a" to StepLevel.THREE,
                "b" to StepLevel.TWO,
                "c" to StepLevel.ONE,
                "d" to StepLevel.NOT_SCORED,
                "e" to null,
                "x" to StepLevel.ONE
            )
        val result = RulesEngine.strengthsAndNeeds(levels, tags)
        assertEquals(listOf("a"), result.strengths)
        assertEquals(
            listOf(
                Need("b", StepLevel.TWO, setOf("Motor coordination", "Range of motion")),
                Need("c", StepLevel.ONE, emptySet()),
                Need("x", StepLevel.ONE, emptySet())
            ),
            result.needs
        )
        assertEquals(listOf("d"), result.notScored)
    }

    @Test
    fun `strengths and needs of nothing is empty`() {
        assertEquals(StrengthsAndNeeds(emptyList(), emptyList(), emptyList()), RulesEngine.strengthsAndNeeds(emptyMap(), emptyMap()))
    }
}
