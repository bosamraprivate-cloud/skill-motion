package com.aitherapist.dls.camera

/**
 * Maps a normalised x between the un-mirrored inference frame (raw front-camera analysis frames,
 * not flipped) and the mirrored preview. Used for display only, never before inference.
 */
object Mirror {
    fun flipX(x: Float): Float = 1f - x
}
