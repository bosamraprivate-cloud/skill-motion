package com.aitherapist.dls.scoring

import com.aitherapist.dls.scoring.RuleOutcome.CANNOT_TELL
import com.aitherapist.dls.scoring.RuleOutcome.DONE
import com.aitherapist.dls.scoring.RuleOutcome.NOT_DONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
