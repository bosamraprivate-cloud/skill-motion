package com.aitherapist.dls.vision

import kotlin.math.acos
import kotlin.math.hypot

/** Pure 2D geometry on normalised landmark points (AD-4). No Android imports. */
object Geometry {
    /** Euclidean distance between two points. */
    fun distance(a: LandmarkPoint, b: LandmarkPoint): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    /** Midpoint of two points; its visibility is the lower of the two. */
    fun midpoint(a: LandmarkPoint, b: LandmarkPoint): LandmarkPoint =
        LandmarkPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f, minOf(a.visibility, b.visibility))

    /** Angle a-vertex-b in degrees (0..180), or null when either arm has zero length. */
    fun angleDeg(a: LandmarkPoint, vertex: LandmarkPoint, b: LandmarkPoint): Double? {
        val ax = (a.x - vertex.x).toDouble()
        val ay = (a.y - vertex.y).toDouble()
        val bx = (b.x - vertex.x).toDouble()
        val by = (b.y - vertex.y).toDouble()
        val lengths = hypot(ax, ay) * hypot(bx, by)
        if (lengths == 0.0) return null
        val cos = ((ax * bx + ay * by) / lengths).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    /** Distance between the two shoulders in the same frame. */
    fun shoulderWidth(leftShoulder: LandmarkPoint, rightShoulder: LandmarkPoint): Double = distance(leftShoulder, rightShoulder)

    /** [value] as a ratio of [reference], or null when the reference is zero (no divide-by-zero). */
    fun ratio(value: Double, reference: Double): Double? = if (reference > 0.0) value / reference else null
}
