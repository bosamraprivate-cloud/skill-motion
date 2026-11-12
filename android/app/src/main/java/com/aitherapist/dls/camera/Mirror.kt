package com.aitherapist.dls.camera

/**
 * Front-camera un-mirroring. Frames are flipped horizontally before inference so the subject's
 * right side is MediaPipe's RIGHT_*; this maps a normalised x between mirrored and un-mirrored space.
 */
object Mirror {
    fun flipX(x: Float): Float = 1f - x
}
