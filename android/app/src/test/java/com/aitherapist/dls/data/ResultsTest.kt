package com.aitherapist.dls.data

import com.aitherapist.dls.scoring.StepLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsTest {
    private fun attempt(
        stepId: String,
        number: Int,
        verdict: AttemptVerdict,
        level: Int = number,
        verifier: VerifierKind = VerifierKind.POSE
    ) = AttemptEntity(
        "$stepId-$number", "s", stepId, number, level, false, verdict, "r", verifier, null, null, 0, emptyList(), null, number.toLong()
    )

    private fun correction(stepId: String, level: Int?, at: Long) = CorrectionEntity("c$at", "s", stepId, level, null, at)

    private fun session(kind: SessionKind, state: SessionState, id: String = "s", taskId: String = "t", startedAt: Long = 0) =
        SessionEntity(id, "c", taskId, kind, state, startedAt, null, "v", "r", 0, 1, 1)

    private fun result(id: String, taskId: String, startedAt: Long) =
        SessionResult(session(SessionKind.ASSESSMENT, SessionState.DONE, id, taskId, startedAt), emptyMap())

    @Test
    fun `invalid stored correction level is ignored and reported without data`() {
        val reported = mutableListOf<String>()
        val corrections = listOf(correction("a", 0, 1), correction("a", 4, 2))
        val levels = sessionLevels(listOf(attempt("a", 1, AttemptVerdict.DONE)), corrections, reported::add)
        assertEquals(StepLevel.THREE, levels["a"])
        assertEquals(2, reported.size)
    }

    @Test
    fun `latest finished session per task wins and jacket on and off are both kept`() {
        // Newest first, as the DAO orders them (startedAt DESC, rowid DESC).
        val ordered = listOf(result("new", "jacket.on", 20), result("off", "jacket.off", 15), result("old", "jacket.on", 10))
        assertEquals(listOf("new", "off"), latestPerTask(ordered).map { it.session.id })
    }

    @Test
    fun `levels come from attempts and the latest correction wins`() {
        val attempts =
            listOf(
                attempt("a", 1, AttemptVerdict.DONE),
                attempt("b", 1, AttemptVerdict.NOT_DONE),
                attempt("b", 2, AttemptVerdict.DONE),
                attempt("c", 1, AttemptVerdict.DONE),
                attempt("n", 1, AttemptVerdict.DONE, verifier = VerifierKind.NONE)
            )
        val corrections = listOf(correction("c", 1, 5), correction("c", null, 9), correction("b", 3, 2), correction("x", 2, 1))
        val levels = sessionLevels(attempts, corrections)
        assertEquals(StepLevel.THREE, levels["a"])
        assertEquals(StepLevel.THREE, levels["b"])
        assertEquals(StepLevel.NOT_SCORED, levels["c"])
        assertEquals(StepLevel.NOT_SCORED, levels["n"])
        assertEquals(StepLevel.TWO, levels["x"])
        assertNull("still open", sessionLevels(listOf(attempt("d", 1, AttemptVerdict.NOT_DONE)), emptyList())["d"])
    }

    @Test
    fun `only finished assessment sessions count`() {
        assertTrue(countsForResults(session(SessionKind.ASSESSMENT, SessionState.DONE)))
        assertFalse(countsForResults(session(SessionKind.REPLAY, SessionState.DONE)))
        listOf(SessionState.RUNNING, SessionState.PAUSED, SessionState.ABANDONED).forEach {
            assertFalse(countsForResults(session(SessionKind.ASSESSMENT, it)))
        }
    }
}
