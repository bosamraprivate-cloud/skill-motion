package com.aitherapist.dls.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardRulesTest {
    @Test
    fun `done first try earns 2 stars, later done earns 1`() {
        assertEquals(2, RewardRules.stars(Finish.Done(firstTry = true)))
        assertEquals(1, RewardRules.stars(Finish.Done(firstTry = false)))
    }

    @Test
    fun `finished without done, not scored or content only earns 1 star`() {
        assertEquals(1, RewardRules.stars(Finish.Finished))
    }

    @Test
    fun `a sticker unlocks every 3 finished tasks`() {
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 2, 2, 2, 3), (0..9).map(RewardRules::stickersUnlocked))
        assertEquals(0, RewardRules.stickersUnlocked(-1))
        assertFalse(RewardRules.unlocksSticker(0))
        assertFalse(RewardRules.unlocksSticker(2))
        assertTrue(RewardRules.unlocksSticker(3))
        assertTrue(RewardRules.unlocksSticker(6))
        assertEquals(listOf(3, 2, 1, 3), (0..3).map(RewardRules::tasksToNextSticker))
    }
}
