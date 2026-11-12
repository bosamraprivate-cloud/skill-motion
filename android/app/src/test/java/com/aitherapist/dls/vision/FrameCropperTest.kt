package com.aitherapist.dls.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCropperTest {
    @Test
    fun `union box gets 25 percent margin per side`() {
        val points = listOf(LandmarkPoint(0.4f, 0.4f), LandmarkPoint(0.6f, 0.6f))
        // 1000x1000: box 400..600, margin 50 each side.
        assertEquals(CropBox(350, 350, 650, 650), FrameCropper.cropBox(1000, 1000, points))
    }

    @Test
    fun `zones join the union`() {
        val points = listOf(LandmarkPoint(0.4f, 0.4f), LandmarkPoint(0.5f, 0.5f))
        val box = FrameCropper.cropBox(1000, 1000, points, listOf(Zone(0.5f, 0.5f, 0.6f, 0.6f)))
        assertEquals(CropBox(350, 350, 650, 650), box)
    }

    @Test
    fun `box is clamped to the frame`() {
        val points = listOf(LandmarkPoint(0.0f, 0.0f), LandmarkPoint(0.9f, 0.9f))
        assertEquals(CropBox(0, 0, 1000, 1000), FrameCropper.cropBox(1000, 1000, points))
    }

    @Test
    fun `small box grows to 20 percent of the shorter side and shifts inside the frame`() {
        val box = FrameCropper.cropBox(640, 480, listOf(LandmarkPoint(0.0f, 0.5f)))!!
        assertEquals(96, box.width) // 0.2 * 480
        assertEquals(96, box.height)
        assertEquals(0, box.left)
    }

    @Test
    fun `no points and no zones give no crop`() {
        assertEquals(null, FrameCropper.cropBox(640, 480, emptyList()))
    }

    @Test
    fun `landmarks outside the frame stay clipped`() {
        val box = FrameCropper.cropBox(640, 480, listOf(LandmarkPoint(-0.5f, -0.5f), LandmarkPoint(1.5f, 1.5f)))
        assertEquals(CropBox(0, 0, 640, 480), box)
    }

    @Test
    fun `scaled size caps the longest side at 512 and never upscales`() {
        assertEquals(PixelSize(512, 288), FrameCropper.scaledSize(CropBox(0, 0, 1920, 1080)))
        assertEquals(PixelSize(288, 512), FrameCropper.scaledSize(CropBox(0, 0, 1080, 1920)))
        assertEquals(PixelSize(300, 200), FrameCropper.scaledSize(CropBox(0, 0, 300, 200)))
    }

    @Test
    fun `crop points take all hands and visible face landmarks only`() {
        val pose = MutableList(33) { LandmarkPoint(0.9f, 0.9f, 0.9f) }
        pose[PoseLandmarkIndex.MOUTH_LEFT] = LandmarkPoint(0.1f, 0.1f, 0.2f) // not visible
        val hand = List(21) { LandmarkPoint(0.3f, 0.3f) }
        val points = FrameCropper.cropPoints(LandmarkFrame(0, pose, listOf(hand, hand)))
        assertEquals(42 + 2, points.size)
        assertTrue(points.none { it.x == 0.1f })
        assertEquals(emptyList<LandmarkPoint>(), FrameCropper.cropPoints(null))
        assertEquals(1, FrameCropper.cropPoints(LandmarkFrame(0, null, listOf(listOf(LandmarkPoint(0f, 0f))))).size)
    }
}
