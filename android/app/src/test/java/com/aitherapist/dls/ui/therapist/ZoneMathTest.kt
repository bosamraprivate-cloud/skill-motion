package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneMathTest {
    private fun assertZone(expected: Zone, actual: Zone?) {
        requireNotNull(actual)
        assertEquals(expected.left, actual.left, 1e-6f)
        assertEquals(expected.top, actual.top, 1e-6f)
        assertEquals(expected.right, actual.right, 1e-6f)
        assertEquals(expected.bottom, actual.bottom, 1e-6f)
    }

    @Test
    fun `drag on the mirrored preview is stored un-mirrored`() {
        // Drawn on the display's left side = the frame's right side.
        assertZone(Zone(0.7f, 0.2f, 0.9f, 0.5f), ZoneMath.fromDisplay(0.1f, 0.2f, 0.3f, 0.5f))
    }

    @Test
    fun `any drag direction gives left lt right and top lt bottom`() {
        assertZone(Zone(0.7f, 0.2f, 0.9f, 0.5f), ZoneMath.fromDisplay(0.3f, 0.5f, 0.1f, 0.2f))
    }

    @Test
    fun `points outside the preview are clamped to 0 to 1`() {
        assertZone(Zone(0f, 0f, 1f, 1f), ZoneMath.fromDisplay(-0.5f, -1f, 1.5f, 2f))
    }

    @Test
    fun `tiny or zero rectangles are rejected`() {
        assertNull(ZoneMath.fromDisplay(0.5f, 0.5f, 0.5f, 0.5f))
        assertNull(ZoneMath.fromDisplay(0.1f, 0.1f, 0.11f, 0.6f))
        assertNull(ZoneMath.fromDisplay(Float.NaN, 0.1f, Float.NaN, 0.6f))
    }

    @Test
    fun `toDisplay inverts fromDisplay`() {
        val zone = requireNotNull(ZoneMath.fromDisplay(0.1f, 0.2f, 0.3f, 0.5f))
        val r = ZoneMath.toDisplay(zone)
        assertEquals(0.1f, r.left, 1e-6f)
        assertEquals(0.2f, r.top, 1e-6f)
        assertEquals(0.3f, r.right, 1e-6f)
        assertEquals(0.5f, r.bottom, 1e-6f)
    }
}
