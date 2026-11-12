package com.aitherapist.dls.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device test (Xiaomi, Android 13): CameraX front-camera analysis frames are already un-mirrored,
 * so the subject's right side is on the image left. Inference must therefore not flip.
 */
class FrameTransformTest {
    // For each rotation, a source point that lies at x = 0.2 (image left) of the upright frame.
    private val leftWhenUpright =
        mapOf(
            0 to NormPoint(0.2f, 0.5f),
            90 to NormPoint(0.5f, 0.8f),
            180 to NormPoint(0.8f, 0.5f),
            270 to NormPoint(0.5f, 0.2f)
        )

    @Test
    fun `inference transform keeps the subject's right side on the image left for every rotation`() {
        leftWhenUpright.forEach { (rotation, source) ->
            val inference = FrameTransform.map(source, rotation, flip = false)
            assertEquals("rotation $rotation", 0.2f, inference.x, 1e-6f)
            assertTrue("subject's right must stay on image left", inference.x < 0.5f)
        }
    }

    @Test
    fun `overlay mapping flips x into the mirrored preview for every rotation`() {
        leftWhenUpright.forEach { (rotation, source) ->
            val inference = FrameTransform.map(source, rotation, flip = false)
            val preview = FrameTransform.map(source, rotation, flip = true)
            assertEquals("rotation $rotation", 0.8f, preview.x, 1e-6f)
            assertEquals(Mirror.flipX(inference.x), preview.x, 1e-6f)
            assertEquals(inference.y, preview.y, 1e-6f)
        }
    }

    @Test
    fun `quarter turns swap axes`() {
        assertTrue(FrameTransform.swapsAxes(90))
        assertTrue(FrameTransform.swapsAxes(270))
        assertFalse(FrameTransform.swapsAxes(0))
        assertFalse(FrameTransform.swapsAxes(180))
    }
}
