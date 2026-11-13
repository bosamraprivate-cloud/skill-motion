package com.aitherapist.dls.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplePairerTest {
    private val pose = listOf(List(33) { LandmarkPoint(0.5f, 0.5f, 0.9f) })
    private val hands = listOf(List(21) { LandmarkPoint(0.4f, 0.6f) })

    @Test
    fun `pose only step completes on the pose result at the same timestamp`() {
        val p = SamplePairer()
        assertTrue(p.tryBegin(10, { null }, pose = true, hand = false))
        val out = p.onResult(LandmarkResult(FpsStream.POSE, 10, pose))!!
        assertEquals(10L, out.landmarks.timestampMs)
        assertEquals(10L, out.frame.timestampMs)
        assertEquals(pose.first(), out.landmarks.pose)
    }

    @Test
    fun `one frame in flight - a new frame waits until both results arrive`() {
        val p = SamplePairer()
        assertTrue(p.tryBegin(10, { null }, pose = true, hand = true))
        assertFalse(p.tryBegin(20, { null }, pose = true, hand = true))
        assertNull(p.onResult(LandmarkResult(FpsStream.POSE, 10, pose)))
        val out = p.onResult(LandmarkResult(FpsStream.HAND, 10, hands))!!
        assertEquals(hands, out.landmarks.hands)
        assertTrue(p.tryBegin(30, { null }, pose = true, hand = true))
    }

    @Test
    fun `a dropped frame frees the slot and an empty pose result means no pose`() {
        val p = SamplePairer()
        assertTrue(p.tryBegin(10, { null }, pose = true, hand = false))
        p.drop(10)
        assertTrue(p.tryBegin(20, { null }, pose = true, hand = false))
        assertNull(p.onResult(LandmarkResult(FpsStream.POSE, 20, emptyList()))!!.landmarks.pose)
    }

    @Test
    fun `an unanswered frame goes stale and is left out of the series entirely`() {
        val p = SamplePairer(staleMs = 500)
        val stale = FakeImage()
        assertTrue(p.tryBegin(10, { stale }, pose = true, hand = false))
        assertFalse(p.tryBegin(400, { null }, pose = true, hand = false))
        assertTrue(p.tryBegin(600, { null }, pose = true, hand = false))
        assertTrue(stale.released)
        assertNull(p.onResult(LandmarkResult(FpsStream.POSE, 10, pose)))
        assertEquals(600L, p.onResult(LandmarkResult(FpsStream.POSE, 600, pose))!!.frame.timestampMs)
    }

    private class FakeImage : AttemptImage {
        var released = false
        override val width = 1
        override val height = 1

        override fun release() {
            released = true
        }
    }
}
