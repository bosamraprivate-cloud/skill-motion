package com.aitherapist.dls.session

import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.PoseLandmarkIndex.LEFT_SHOULDER
import com.aitherapist.dls.vision.PoseLandmarkIndex.LEFT_WRIST
import com.aitherapist.dls.vision.PoseLandmarkIndex.RIGHT_SHOULDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttemptWindowTest {
    private class FakeClock(var now: Long = 0) : Clock {
        override fun nowMs() = now
    }

    private val opened = 1_000L
    private val clock = FakeClock(opened)
    private val window = AttemptWindow(opened)

    private fun pose(tMs: Long, x: Float) = PoseSample(
        tMs,
        mapOf(
            LEFT_WRIST to LandmarkPoint(x, 0.5f),
            LEFT_SHOULDER to LandmarkPoint(0.75f, 0.3f),
            RIGHT_SHOULDER to LandmarkPoint(0.25f, 0.3f)
        )
    )

    // The movement test looks back 0.5 s, so the last wrist jump at L is still detected at L + 500.
    // "Still from 18 s" therefore means the last jump is at 17.5 s; "movement at 25 s" means at 24.5 s.

    /** Feeds samples every 100 ms from [fromMs] (relative) to [toMs]; moving alternates the wrist. */
    private fun feed(fromMs: Long, toMs: Long, moving: Boolean) {
        var t = fromMs
        while (t <= toMs) {
            val x = if (moving && (t / 100) % 2 == 1L) 0.6f else 0.5f
            window.onSample(pose(opened + t, x))
            t += 100
        }
    }

    private fun at(relMs: Long): CloseReason? {
        clock.now = opened + relMs
        return window.check(clock)
    }

    @Test
    fun `done closes with reason done`() {
        assertNull(at(2_999))
        window.onDone(opened + 3_000)
        window.onDone(opened + 4_000)
        assertEquals(CloseReason.DONE, at(3_000))
    }

    @Test
    fun `still from 18 s closes at 20 s`() {
        feed(0, 17_500, moving = true)
        feed(17_600, 20_000, moving = false)
        assertNull(at(19_999))
        assertEquals(CloseReason.STILL, at(20_000))
    }

    @Test
    fun `movement at 25 s keeps window open until 27 s`() {
        feed(0, 24_500, moving = true)
        assertNull(at(25_000))
        feed(24_600, 27_000, moving = false)
        assertNull(at(26_999))
        assertEquals(CloseReason.STILL, at(27_000))
    }

    @Test
    fun `constant movement closes at 45 s with timeout`() {
        feed(0, 45_000, moving = true)
        assertNull(at(44_999))
        assertEquals(CloseReason.TIMEOUT, at(45_000))
    }

    @Test
    fun `no samples at all closes as still at 20 s`() {
        assertNull(at(19_999))
        assertEquals(CloseReason.STILL, window.check(opened + 20_000))
    }

    @Test
    fun `window does not reopen after still`() {
        assertEquals(CloseReason.STILL, at(20_000))
        feed(20_100, 21_000, moving = true)
        assertEquals(CloseReason.STILL, at(21_000))
    }

    @Test
    fun `done at 50 s gives timeout`() {
        window.onDone(opened + 50_000)
        assertEquals(CloseReason.TIMEOUT, at(50_000))
    }

    @Test
    fun `done before 20 s wins over a later still`() {
        window.onDone(opened + 10_000)
        assertEquals(CloseReason.DONE, at(25_000))
    }

    @Test
    fun `check before done time does not report done`() {
        window.onDone(opened + 10_000)
        assertNull(at(5_000))
        assertEquals(CloseReason.DONE, at(10_000))
    }

    @Test
    fun `samples before open are ignored`() {
        window.onSample(pose(opened - 300, 0.9f))
        window.onSample(pose(opened, 0.5f))
        assertEquals(CloseReason.STILL, at(20_000))
    }

    @Test
    fun `out of order samples are dropped`() {
        feed(0, 18_000, moving = false)
        window.onSample(pose(opened + 17_900, 0.9f))
        assertEquals(CloseReason.STILL, at(20_000))
    }

    @Test
    fun `storage names are lower snake`() {
        assertEquals(listOf("done", "still", "timeout"), CloseReason.entries.map { it.storageName })
    }

    @Test
    fun `rules version is set`() {
        assertEquals(3, WindowRules.RULES_VERSION)
    }

    @Test
    fun `samples without a pose never let the still rule close the window`() {
        val w = AttemptWindow(0)
        var t = 0L
        while (t < 30_000) {
            t += 100
            w.onSample(PoseSample(t, emptyMap()))
        }
        assertEquals(null, w.check(t))
        assertEquals(CloseReason.TIMEOUT, w.check(45_000))
    }

    @Test
    fun `a step time closes the window at that time and never on stillness`() {
        val w = AttemptWindow(0, WindowRules.stepTimeoutMs(25))
        var t = 0L
        while (t < 24_900) {
            t += 100
            w.onSample(pose(t, 0.5f)) // completely still for 24.9 s
            assertNull("open at $t", w.check(t))
        }
        assertEquals(CloseReason.TIMEOUT, w.check(25_000))
    }

    @Test
    fun `a step time longer than the old cap is respected`() {
        val w = AttemptWindow(0, WindowRules.stepTimeoutMs(60))
        assertNull(w.check(45_000))
        assertEquals(CloseReason.TIMEOUT, w.check(60_000))
    }

    @Test
    fun `done before the step time closes as done`() {
        val w = AttemptWindow(0, 10_000)
        w.onDone(4_000)
        assertEquals(CloseReason.DONE, w.check(4_000))
    }

    @Test
    fun `step timeout maps seconds and keeps old rules for untimed steps`() {
        assertEquals(15_000L, WindowRules.stepTimeoutMs(15))
        assertNull(WindowRules.stepTimeoutMs(null))
        assertNull(WindowRules.stepTimeoutMs(0))
    }
}
