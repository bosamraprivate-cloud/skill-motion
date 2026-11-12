package com.aitherapist.dls.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryTest {
    private fun p(x: Float, y: Float, v: Float = 1f) = LandmarkPoint(x, y, v)

    @Test
    fun `distance is euclidean`() {
        assertEquals(0.5, Geometry.distance(p(0f, 0f), p(0.3f, 0.4f)), 1e-6)
    }

    @Test
    fun `midpoint averages and keeps lower visibility`() {
        val m = Geometry.midpoint(p(0.2f, 0.4f, 0.9f), p(0.4f, 0.6f, 0.6f))
        assertEquals(0.3f, m.x, 1e-6f)
        assertEquals(0.5f, m.y, 1e-6f)
        assertEquals(0.6f, m.visibility, 1e-6f)
    }

    @Test
    fun `right angle is 90 degrees`() {
        assertEquals(90.0, Geometry.angleDeg(p(1f, 0f), p(0f, 0f), p(0f, 1f))!!, 1e-6)
    }

    @Test
    fun `straight angle is 180 degrees`() {
        assertEquals(180.0, Geometry.angleDeg(p(-1f, 0f), p(0f, 0f), p(1f, 0f))!!, 1e-6)
    }

    @Test
    fun `angle with zero-length arm is null`() {
        assertNull(Geometry.angleDeg(p(0f, 0f), p(0f, 0f), p(1f, 0f)))
    }

    @Test
    fun `shoulder width and ratio`() {
        val width = Geometry.shoulderWidth(p(0.6f, 0.5f), p(0.4f, 0.5f))
        assertEquals(0.2, width, 1e-6)
        assertEquals(0.5, Geometry.ratio(0.1, width)!!, 1e-6)
    }

    @Test
    fun `ratio against zero reference is null`() {
        assertNull(Geometry.ratio(0.1, 0.0))
    }
}
