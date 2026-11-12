package com.aitherapist.dls.ui.child

import coil3.gif.MovieDrawable
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipPlaybackTest {
    @Test
    fun `still mode shows the still`() {
        assertEquals(ClipPlayback("s.webp", false, 0), clipPlayback("c.webp", "s.webp", false, true))
    }

    @Test
    fun `still mode without still shows first frame of clip`() {
        assertEquals("c.webp", clipPlayback("c.webp", null, true, true).path)
    }

    @Test
    fun `no clip shows the still`() {
        val p = clipPlayback(null, "s.webp", false, false)
        assertEquals("s.webp", p.path)
        assertEquals(false, p.animate)
    }

    @Test
    fun `loop repeats forever`() {
        assertEquals(
            ClipPlayback("c.webp", true, MovieDrawable.REPEAT_INFINITE),
            clipPlayback("c.webp", null, true, false)
        )
    }

    @Test
    fun `segment plays once`() {
        assertEquals(ClipPlayback("c.webp", true, 0), clipPlayback("c.webp", "s.webp", false, false))
    }

    @Test
    fun `nothing to show is null path`() {
        assertEquals(null, clipPlayback(null, null, false, false).path)
    }
}
