package com.aitherapist.dls.replay

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipNameTest {
    private fun problem(name: String) = (ClipName.parse(name) as ClipName.Parsed.Invalid).problem

    @Test
    fun `valid name parses with prompt level from the attempt number`() {
        val clip = (ClipName.parse("eat.spoon.01__2__not_done__vol-1.mp4") as ClipName.Parsed.Ok).clip
        assertEquals("eat.spoon.01", clip.stepId)
        assertEquals(2, clip.attemptNumber)
        assertEquals(ScriptedOutcome.NOT_DONE, clip.outcome)
        assertEquals("vol-1", clip.volunteer)
        assertEquals(2, clip.promptLevel)
        assertEquals(3, (ClipName.parse("a__5__done__v.MP4") as ClipName.Parsed.Ok).clip.promptLevel)
    }

    @Test
    fun `malformed names are rejected`() {
        listOf(
            "foo.mp4",
            "a__1__done__v.mov",
            "a__1__done.mp4",
            "a__1__done__v__x.mp4",
            "a__1__done__.mp4",
            "a/b__1__done__v.mp4",
            "a___1__done__v.mp4"
        )
            .forEach { assertEquals(it, ClipProblem.MALFORMED_NAME, problem(it)) }
    }

    @Test
    fun `bad attempt and outcome tokens are rejected`() {
        assertEquals(ClipProblem.BAD_ATTEMPT, problem("a__0__done__v.mp4"))
        assertEquals(ClipProblem.BAD_ATTEMPT, problem("a__x__done__v.mp4"))
        assertEquals(ClipProblem.BAD_OUTCOME, problem("a__1__maybe__v.mp4"))
        assertEquals(ClipProblem.BAD_OUTCOME, problem("a__1__cannot_tell__v.mp4"))
    }
}
