package com.aitherapist.dls.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun `forward in RTL enters from the left`() {
        assertEquals(-1, enterOffsetSign(forward = true, rtl = true))
    }

    @Test
    fun `forward in LTR enters from the right`() {
        assertEquals(1, enterOffsetSign(forward = true, rtl = false))
    }

    @Test
    fun `back mirrors forward in both directions`() {
        assertEquals(1, enterOffsetSign(forward = false, rtl = true))
        assertEquals(-1, enterOffsetSign(forward = false, rtl = false))
    }

    @Test
    fun `higher tab index is forward, lower is back`() {
        assertTrue(isForwardTab(from = 0, to = 3))
        assertFalse(isForwardTab(from = 3, to = 1))
    }

    @Test
    fun `durations stay calm and short`() {
        assertTrue(Motion.SLIDE_MS in 200..300)
        assertTrue(Motion.CROSSFADE_MS in 200..300)
        assertTrue(Motion.FADE_MS <= Motion.SLIDE_MS)
    }
}
