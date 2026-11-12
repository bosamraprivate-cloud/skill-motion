package com.aitherapist.dls.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FpsMeterTest {
    private var now = 0L
    private val meter = FpsMeter(clock = { now })

    @Test
    fun `empty window is zero`() {
        assertEquals(0.0, meter.fps(FpsStream.POSE), 0.0)
        assertNull(meter.finishRun())
    }

    @Test
    fun `30 results in 1 s is 30 fps and window slides`() {
        repeat(30) { i ->
            now = 1000L + i * 33
            meter.onResult(FpsStream.POSE, now)
        }
        assertEquals(30.0, meter.fps(FpsStream.POSE), 0.0)
        now = 3000
        assertEquals(0.0, meter.fps(FpsStream.POSE), 0.0)
    }

    @Test
    fun `combined counts frames with both results`() {
        // 25 frames in one second; pose on every frame, hand on 12 of them.
        for (i in 0 until 25) {
            val frameTs = 1000L + i * 40
            now = frameTs + 5
            meter.onResult(FpsStream.POSE, frameTs)
            if (i % 2 == 0 && i < 24) meter.onResult(FpsStream.HAND, frameTs)
        }
        assertEquals(25.0, meter.fps(FpsStream.POSE), 0.0)
        assertEquals(12.0, meter.fps(FpsStream.HAND), 0.0)
        assertEquals(12.0, meter.fps(FpsStream.COMBINED), 0.0)
    }

    @Test
    fun `run averages over the run duration`() {
        now = 0
        meter.startRun()
        for (i in 0 until 600) {
            now = i * 100L
            meter.onResult(FpsStream.HAND, now)
            meter.onResult(FpsStream.POSE, now)
        }
        now = 60_000
        val result = meter.finishRun()!!
        assertEquals(10.0, result.pose, 1e-9)
        assertEquals(10.0, result.hand, 1e-9)
        assertEquals(10.0, result.combined, 1e-9)
        assertEquals(60_000L, result.durationMs)
    }
}
