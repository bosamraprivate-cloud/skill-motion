package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.Zone

/** A rectangle in display coordinates (normalised 0..1 over the mirrored preview). */
data class DisplayRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Converts between the mirrored front preview the therapist draws on and stored zones, which are normalised
 * and un-mirrored (x' = 1 - x) like the analysis frames.
 */
object ZoneMath {
    /** Smallest zone side as a fraction of the preview; smaller drags are rejected. */
    const val MIN_SIDE = 0.03f

    /** The stored zone for a drag from ([x1], [y1]) to ([x2], [y2]) on the preview, or null when too small. */
    fun fromDisplay(x1: Float, y1: Float, x2: Float, y2: Float): Zone? {
        val ax = 1f - x1.clamp()
        val bx = 1f - x2.clamp()
        val ay = y1.clamp()
        val by = y2.clamp()
        val zone = Zone(left = minOf(ax, bx), top = minOf(ay, by), right = maxOf(ax, bx), bottom = maxOf(ay, by))
        return zone.takeIf { it.right - it.left >= MIN_SIDE && it.bottom - it.top >= MIN_SIDE }
    }

    /** Where a stored zone appears on the mirrored preview. */
    fun toDisplay(zone: Zone): DisplayRect =
        DisplayRect(left = 1f - zone.right, top = zone.top, right = 1f - zone.left, bottom = zone.bottom)

    private fun Float.clamp(): Float = if (isNaN()) 0f else coerceIn(0f, 1f)
}
