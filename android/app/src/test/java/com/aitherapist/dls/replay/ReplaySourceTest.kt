package com.aitherapist.dls.replay

import com.aitherapist.dls.catalog.Landmarker
import com.aitherapist.dls.vision.AttemptImage
import com.aitherapist.dls.vision.LandmarkFrame
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySourceTest {
    private class Image : AttemptImage {
        override val width = 2
        override val height = 2

        override fun release() = Unit
    }

    private class Clip(override val durationMs: Long, private val missing: Set<Long> = emptySet()) : DecodedClip {
        var closed = false

        override fun frameAt(timeMs: Long): AttemptImage? = if (timeMs in missing) null else Image()

        override fun close() {
            closed = true
        }
    }

    private val opened = mutableListOf<Pair<Boolean, Boolean>>()
    private var detectorClosed = 0
    private val engine =
        VideoLandmarkEngine { pose, hand ->
            opened += pose to hand
            object : VideoDetector {
                override fun detect(image: AttemptImage, timestampMs: Long) = LandmarkFrame(timestampMs, emptyList())

                override fun close() {
                    detectorClosed++
                }
            }
        }

    @Test
    fun `decodes at the interval and pairs every frame with its landmarks`() = runTest {
        val clip = Clip(850, missing = setOf(200L))
        val seen = mutableListOf<Long>()
        val ok =
            ReplaySource({ _, _ -> clip }, engine).read(File("x"), setOf(Landmarker.POSE, Landmarker.HAND)) { frame, lm ->
                assertEquals(frame!!.timestampMs, lm!!.timestampMs)
                seen += frame.timestampMs
            }
        assertTrue(ok)
        assertEquals(listOf(0L, 100L, 300L, 400L, 500L, 600L, 700L, 800L), seen)
        assertEquals(listOf(true to true), opened)
        assertEquals(1, detectorClosed)
        assertTrue(clip.closed)
    }

    @Test
    fun `no landmarkers means no detector and empty landmarks`() = runTest {
        var lms = 0
        val ok =
            ReplaySource({ _, _ -> Clip(100) }, engine).read(File("x"), emptySet()) { _, lm ->
                assertNull(lm!!.pose)
                lms++
            }
        assertTrue(ok)
        assertEquals(2, lms)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `undecodable clips return false`() = runTest {
        assertFalse(ReplaySource({ _, _ -> null }, engine).read(File("x"), setOf(Landmarker.POSE)) { _, _ -> })
        assertFalse(ReplaySource({ _, _ -> Clip(0) }, engine).read(File("x"), setOf(Landmarker.POSE)) { _, _ -> })
        val allMissing = Clip(100, missing = setOf(0L, 100L))
        assertFalse(ReplaySource({ _, _ -> allMissing }, engine).read(File("x"), setOf(Landmarker.POSE)) { _, _ -> })
    }

    @Test
    fun `clips longer than 90 s are not read`() = runTest {
        val long = Clip(ReplaySource.MAX_DURATION_MS + 1)
        val source = ReplaySource({ _, _ -> long }, engine)
        assertFalse(source.read(File("x"), setOf(Landmarker.POSE)) { _, _ -> error("no frames expected") })
        assertEquals(ReplaySource.MAX_DURATION_MS + 1, source.durationMs(File("x")))
        assertNull(ReplaySource({ _, _ -> null }, engine).durationMs(File("x")))
    }

    @Test
    fun `less than 80 percent decoded coverage is not judgeable`() = runTest {
        // 10 slots (0..900 ms), 3 missing = 70 % coverage.
        val gappy = Clip(900, missing = setOf(100L, 400L, 700L))
        assertFalse(ReplaySource({ _, _ -> gappy }, engine).read(File("x"), setOf(Landmarker.POSE)) { _, _ -> })
        // 2 missing = 80 %: judgeable.
        val ok = Clip(900, missing = setOf(100L, 400L))
        assertTrue(ReplaySource({ _, _ -> ok }, engine).read(File("x"), setOf(Landmarker.POSE)) { _, _ -> })
    }

    @Test
    fun `mirrored flag reaches the decoder`() = runTest {
        val flags = mutableListOf<Boolean>()
        val source = ReplaySource({ _, m ->
            flags += m
            Clip(100)
        }, engine)
        source.read(File("x"), emptySet(), mirrored = true) { _, _ -> }
        source.read(File("x"), emptySet()) { _, _ -> }
        assertEquals(listOf(true, false), flags)
    }
}
