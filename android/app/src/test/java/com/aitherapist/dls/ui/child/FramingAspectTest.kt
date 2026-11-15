package com.aitherapist.dls.ui.child

import org.junit.Assert.assertEquals
import org.junit.Test

class FramingAspectTest {
    @Test
    fun `landscape frame box is four by three`() {
        assertEquals(4f / 3f, framingAspect(portrait = false), 1e-6f)
    }

    @Test
    fun `portrait frame box is three by four`() {
        assertEquals(3f / 4f, framingAspect(portrait = true), 1e-6f)
    }
}
