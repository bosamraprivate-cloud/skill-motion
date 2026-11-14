package com.aitherapist.dls.ui.child

import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.session.CloseReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeRingTrackerTest {
    private val full = EffectLevel.FULL
    private val tracker = TimeRingTracker()

    @Test
    fun `waiting for the window the ring is empty and still`() {
        val ring = tracker.waiting(20_000, full)
        assertEquals(0f, ring.startFraction)
        assertFalse(ring.running)
    }

    @Test
    fun `an open window fills with the elapsed time`() {
        val ring = tracker.attempt(1_000, 20_000, 6_000, held = false, level = full)
        assertEquals(0.25f, ring.startFraction, 0.0001f)
        assertEquals(15_000L, ring.remainingMs)
        assertTrue(ring.running)
        assertEquals(1_000L, ring.key)
    }

    @Test
    fun `a hold freezes the ring where the hold began and releasing resumes`() {
        tracker.attempt(0, 20_000, 2_000, held = false, level = full)
        val frozen = tracker.attempt(0, 20_000, 5_000, held = true, level = full)
        assertEquals(0.25f, frozen.startFraction, 0.0001f)
        assertFalse(frozen.running)
        val later = tracker.attempt(0, 20_000, 9_000, held = true, level = full)
        assertEquals("still frozen", 0.25f, later.startFraction, 0.0001f)
        assertEquals(0.25f, tracker.paused(full).startFraction, 0.0001f)
        val resumed = tracker.attempt(0, 20_000, 10_000, held = false, level = full)
        assertTrue(resumed.running)
        assertEquals(0.5f, resumed.startFraction, 0.0001f)
    }

    @Test
    fun `a new window restarts the ring`() {
        tracker.attempt(0, 20_000, 10_000, held = false, level = full)
        val next = tracker.attempt(30_000, 20_000, 30_000, held = false, level = full)
        assertEquals(0f, next.startFraction)
        assertEquals(30_000L, next.key)
    }

    @Test
    fun `done first ends early once, timeout ends full and is never repeated`() {
        tracker.attempt(0, 20_000, 0, held = false, level = full)
        val early = tracker.closed(0, CloseReason.DONE, 5_000, full)!!
        assertEquals(TimeRing.Ending.DONE_EARLY, early.ending)
        assertEquals(0.25f, early.startFraction, 0.0001f)
        assertNull("once per window", tracker.closed(0, CloseReason.DONE, 6_000, full))
        tracker.attempt(40_000, 20_000, 40_000, held = false, level = full)
        val up = tracker.closed(40_000, CloseReason.TIMEOUT, 60_000, full)!!
        assertEquals(TimeRing.Ending.TIME_UP, up.ending)
        assertEquals(1f, up.startFraction)
        assertFalse(up.running)
    }

    @Test
    fun `only the full effect level pulses, and only in the last three seconds`() {
        val running = TimeRing(20_000, 0f, 20_000, running = true, level = EffectLevel.FULL)
        assertFalse(running.pulses(0.5f))
        assertTrue(running.pulses(0.9f))
        assertFalse("full ring no longer pulses", running.pulses(1f))
        assertFalse("calm stays static", running.copy(level = EffectLevel.CALM).pulses(0.9f))
        assertFalse("frozen ring is still", running.copy(running = false).pulses(0.9f))
    }
}
