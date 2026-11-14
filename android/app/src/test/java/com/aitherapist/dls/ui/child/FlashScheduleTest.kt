package com.aitherapist.dls.ui.child

import com.aitherapist.dls.data.EffectLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashScheduleTest {
    @Test
    fun manyFlashesInShortTimeAreThinnedToThreePerSecond() {
        val times = FlashSchedule.times(count = 20, durationMs = 2_000)
        assertTrue(FlashSchedule.isSafe(times))
        assertTrue(times.size <= 6)
        assertTrue(times.zipWithNext().all { (a, b) -> b - a >= FlashSchedule.MIN_GAP_MS })
    }

    @Test
    fun everyCountAndDurationIsSafe() {
        for (count in 0..30) {
            for (d in listOf(1L, 300L, 1_000L, 1_600L, 2_000L, 5_000L)) {
                assertTrue("$count/$d", FlashSchedule.isSafe(FlashSchedule.times(count, d)))
            }
        }
    }

    @Test
    fun emptyForNoFlashes() {
        assertEquals(emptyList<Long>(), FlashSchedule.times(0, 2_000))
        assertEquals(emptyList<Long>(), FlashSchedule.times(3, 0))
    }

    @Test
    fun isSafeDetectsFourInOneSecond() {
        assertFalse(FlashSchedule.isSafe(listOf(0, 200, 400, 600)))
        assertTrue(FlashSchedule.isSafe(listOf(0, 334, 668, 1_002)))
    }

    @Test
    fun durationsAreBounded() {
        assertEquals(2_000L, StepEffect(1, StepEffect.Kind.CELEBRATE, EffectLevel.FULL).durationMs)
        assertEquals(1_500L, StepEffect(1, StepEffect.Kind.CELEBRATE, EffectLevel.CALM).durationMs)
        assertEquals(1_500L, StepEffect(1, StepEffect.Kind.ENCOURAGE, EffectLevel.FULL).durationMs)
        assertEquals(RewardSounds.CHIME, RewardSounds.forCelebration(EffectLevel.FULL))
        assertEquals(RewardSounds.SOFT, RewardSounds.forCelebration(EffectLevel.CALM))
    }
}
