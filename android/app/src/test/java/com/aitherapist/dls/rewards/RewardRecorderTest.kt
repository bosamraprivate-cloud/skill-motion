package com.aitherapist.dls.rewards

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardRecorderTest {
    private val store = FakeRewardStore(evalChildren = setOf("eval"))
    private val recorder = RewardRecorder(store, wallClock = { 42L })

    @Test
    fun `a step is rewarded once per session and step`() = runTest {
        assertTrue(recorder.recordStep("c", "s", "st1", Finish.Done(firstTry = true)))
        assertFalse(recorder.recordStep("c", "s", "st1", Finish.Done(firstTry = true)))
        assertTrue(recorder.recordStep("c", "s", "st2", Finish.Finished))
        assertEquals(3, recorder.starsForSession("s"))
        val row = store.rows.getValue(RewardRecorder.stepKey("s", "st1"))
        assertEquals(42L, row.createdAt)
        assertNull(row.stickerId)
    }

    @Test
    fun `the eval child earns nothing`() = runTest {
        assertFalse(recorder.recordStep("eval", "s", "st1", Finish.Done(firstTry = true)))
        assertFalse(recorder.recordTrainingSet("eval", Finish.Done(firstTry = true), resultId = "t0"))
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `a training set follows the same rules and only a finished run counts as a task`() = runTest {
        store.finishedAssessments = 2
        assertTrue(recorder.recordTrainingSet("c", Finish.Done(firstTry = true), resultId = "t1"))
        assertFalse(recorder.recordTrainingSet("c", Finish.Done(firstTry = true), resultId = "t1"))
        assertTrue(recorder.recordTrainingSet("c", Finish.Finished, resultId = "t2"))
        assertEquals(listOf(2, 1), store.rows.values.map { it.stars })
        assertTrue(store.rows.values.all { it.sessionId == null })
        assertEquals(2, recorder.totalFinishedTasks("c"))
    }
}
