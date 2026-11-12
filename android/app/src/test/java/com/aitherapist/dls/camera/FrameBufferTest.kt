package com.aitherapist.dls.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameBufferTest {
    private var now = 0L
    private val buffer = FrameBuffer<String, String>(clock = { now })

    @Test
    fun `result within 100 ms is attached`() {
        now = 1000
        buffer.addFrame(1000, "f")
        now = 1060
        buffer.addResult(1060, "r")
        val paired = buffer.paired(1000)!!
        assertTrue(paired.visible)
        assertEquals("r", paired.result)
    }

    @Test
    fun `result after 100 ms leaves frame invisible`() {
        now = 1000
        buffer.addFrame(1000, "f")
        now = 1150
        buffer.addResult(1150, "r")
        assertFalse(buffer.paired(1000)!!.visible)
    }

    @Test
    fun `no result by 100 ms leaves frame invisible`() {
        now = 1000
        buffer.addFrame(1000, "f")
        now = 1050
        assertNull(buffer.paired(1000))
        now = 1100
        assertFalse(buffer.paired(1000)!!.visible)
    }

    @Test
    fun `nearest result wins`() {
        now = 1030
        buffer.addResult(960, "early")
        buffer.addFrame(1000, "f")
        buffer.addResult(1030, "late")
        assertEquals("late", buffer.paired(1000)!!.result)
    }

    @Test
    fun `only last 3 s are kept`() {
        for (t in 0L..10_000L step 100) {
            now = t
            buffer.addFrame(t, "f$t")
        }
        assertEquals(7_000L, buffer.resolved().first().timestampMs)
        assertEquals(31, buffer.frameCount())
    }
}
