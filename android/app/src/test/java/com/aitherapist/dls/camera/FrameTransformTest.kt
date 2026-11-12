package com.aitherapist.dls.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTransformTest {
    // For each rotation, a source point that lies on the left (x = 0.2) of the upright, still-mirrored frame.
    private val leftWhenUpright =
        mapOf(
            0 to NormPoint(0.2f, 0.5f),
            90 to NormPoint(0.5f, 0.8f),
            180 to NormPoint(0.8f, 0.5f),
            270 to NormPoint(0.5f, 0.2f)
        )

    @Test
    fun `left-side point ends on the right after un-mirroring for every rotation`() {
        leftWhenUpright.forEach { (rotation, source) ->
            val upright = FrameTransform.map(source, rotation, flip = false)
            assertEquals("rotation $rotation upright", 0.2f, upright.x, 1e-6f)
            val unmirrored = FrameTransform.map(source, rotation, flip = true)
            assertEquals("rotation $rotation un-mirrored", 0.8f, unmirrored.x, 1e-6f)
            assertTrue(unmirrored.x > 0.5f)
            assertEquals(upright.y, unmirrored.y, 1e-6f)
        }
    }

    @Test
    fun `quarter turns swap axes`() {
        assertTrue(FrameTransform.swapsAxes(90))
        assertTrue(FrameTransform.swapsAxes(270))
        assertTrue(!FrameTransform.swapsAxes(0))
        assertTrue(!FrameTransform.swapsAxes(180))
    }
}
