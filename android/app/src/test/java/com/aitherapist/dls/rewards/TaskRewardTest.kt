package com.aitherapist.dls.rewards

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskRewardTest {
    private val store = FakeRewardStore(evalChildren = setOf("eval"))
    private val recorder = RewardRecorder(store, wallClock = { 42L })

    @Test
    fun `stickers unlock at 3 6 9 and the path shows the unlocking task full`() {
        assertEquals(listOf(0, 0, 1, 1, 1, 2, 2, 2, 3), (1..9).map { RewardRules.stickersUnlocked(it) })
        assertEquals(listOf(0, 1, 2, 3, 1, 3), listOf(0, 1, 2, 3, 4, 6).map { RewardRules.pathFilled(it) })
    }

    @Test
    fun `a third task unlocks one sticker on its own row, once`() = runTest {
        store.finishedAssessments = 3
        recorder.recordStep("c", "s", "a", Finish.Done(firstTry = true))
        recorder.recordStep("c", "s", "b", Finish.Finished)
        val first = recorder.finishAssessment("c", "s")!!
        assertEquals(3, first.stars)
        assertEquals(3, first.finishedTasks)
        assertEquals("sticker.01", first.newSticker)
        assertEquals("sticker.01", store.rows.getValue("task:session:s").stickerId)
        // Restore / retry: same sticker shown, no duplicate.
        assertEquals("sticker.01", recorder.finishAssessment("c", "s")!!.newSticker)
        assertEquals(listOf("sticker.01"), recorder.stickerIds("c"))
    }

    @Test
    fun `a fourth task unlocks nothing`() = runTest {
        store.finishedAssessments = 3
        recorder.finishAssessment("c", "s3")
        store.finishedAssessments = 4
        assertNull(recorder.finishAssessment("c", "s4")!!.newSticker)
        assertEquals(1, recorder.stickerIds("c").size)
    }

    @Test
    fun `a task without reward rows still carries its sticker`() = runTest {
        store.finishedAssessments = 3
        val reward = recorder.finishAssessment("c", "empty")!!
        assertEquals(0, reward.stars)
        assertEquals("sticker.01", reward.newSticker)
    }

    @Test
    fun `one training run with many activities counts as one task`() = runTest {
        store.finishedAssessments = 1
        repeat(5) { recorder.recordTrainingSet("c", Finish.Done(firstTry = true), "run:$it") }
        assertEquals(1, recorder.totalFinishedTasks("c"))
        val reward = recorder.finishTrainingRun("c", "run")!!
        assertEquals(10, reward.stars)
        assertEquals(2, reward.finishedTasks)
        assertNull(reward.newSticker)
        recorder.finishTrainingRun("c", "run")
        assertEquals(2, recorder.totalFinishedTasks("c"))
    }

    @Test
    fun `concurrent finishes never duplicate a sticker`() = runTest {
        store.finishedAssessments = 3
        val results = List(4) { async { recorder.finishAssessment("c", "s$it") } }.awaitAll()
        assertEquals(listOf("sticker.01"), recorder.stickerIds("c"))
        assertEquals(1, results.count { it!!.newSticker != null })
    }

    @Test
    fun `the eval child gets no reward screen`() = runTest {
        assertNull(recorder.finishAssessment("eval", "s"))
    }

    @Test
    fun `sticker ids never wrap`() {
        assertEquals("sticker.30", Stickers.idFor(29))
        assertEquals("sticker.31", Stickers.idFor(30))
        assertEquals(30, Stickers.indexOf("sticker.31"))
    }
}
