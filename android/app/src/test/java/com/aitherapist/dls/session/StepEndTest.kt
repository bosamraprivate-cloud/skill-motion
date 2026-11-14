package com.aitherapist.dls.session

import com.aitherapist.dls.data.AttemptVerdict.CANNOT_TELL
import com.aitherapist.dls.data.AttemptVerdict.DONE
import com.aitherapist.dls.data.AttemptVerdict.NOT_DONE
import org.junit.Assert.assertEquals
import org.junit.Test

class StepEndTest {
    @Test
    fun outcomeFollowsVerdicts() {
        assertEquals(StepEnd.Outcome.DONE, StepEnd.of(0, listOf(NOT_DONE, DONE)).outcome)
        assertEquals(StepEnd.Outcome.NOT_DONE, StepEnd.of(0, listOf(NOT_DONE, NOT_DONE, NOT_DONE)).outcome)
        assertEquals(StepEnd.Outcome.TOOK_PART, StepEnd.of(0, listOf(CANNOT_TELL, CANNOT_TELL)).outcome)
    }
}
