package com.aitherapist.dls.camera

/** Normalised point in [0, 1] image coordinates (y grows downward). */
data class NormPoint(val x: Float, val y: Float)

/**
 * Pure model of the analysis-frame transform: rotate clockwise by [rotationDegrees]
 * (0/90/180/270, as reported by CameraX) to make the frame upright, then flip horizontally
 * when [flip] is set. Inference uses `flip = false`: CameraX front-camera analysis frames are
 * already un-mirrored (as others see the subject), so the subject's right side is on the image
 * left. `flip = true` maps into the mirrored preview space. `CameraController` builds its
 * bitmap matrix from this function, so the JVM test covers the real camera path.
 */
object FrameTransform {
    fun map(point: NormPoint, rotationDegrees: Int, flip: Boolean): NormPoint {
        val upright =
            when (Math.floorMod(rotationDegrees, FULL_TURN)) {
                0 -> point
                QUARTER -> NormPoint(1f - point.y, point.x)
                HALF -> NormPoint(1f - point.x, 1f - point.y)
                THREE_QUARTERS -> NormPoint(point.y, 1f - point.x)
                else -> throw IllegalArgumentException("rotation must be a multiple of 90")
            }
        return if (flip) NormPoint(Mirror.flipX(upright.x), upright.y) else upright
    }

    /** True when the rotation swaps width and height. */
    fun swapsAxes(rotationDegrees: Int): Boolean = Math.floorMod(rotationDegrees, HALF) == QUARTER

    private const val QUARTER = 90
    private const val HALF = 180
    private const val THREE_QUARTERS = 270
    private const val FULL_TURN = 360
}
