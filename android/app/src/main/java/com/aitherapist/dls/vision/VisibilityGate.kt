package com.aitherapist.dls.vision

/**
 * Visibility gate (AD-3), run before any check. A set of needed pose landmarks is visible when every
 * landmark has MediaPipe visibility > 0.5 in more than half of the attempt's frames; frames without a
 * paired pose result (100 ms rule) count as not visible.
 */
object VisibilityGate {
    const val MIN_VISIBILITY = 0.5f

    /** True when at least one of [alternatives] (e.g. left side, right side) is visible. */
    fun passes(landmarks: List<LandmarkFrame>, alternatives: List<Set<Int>>): Boolean =
        landmarks.isNotEmpty() && alternatives.any { needed -> visibleCount(landmarks, needed) * 2 > landmarks.size }

    /** True when every [needed] landmark is present and visible in this frame. */
    fun visibleIn(frame: LandmarkFrame, needed: Set<Int>): Boolean {
        val pose = frame.pose ?: return false
        return needed.all { index -> (pose.getOrNull(index)?.visibility ?: 0f) > MIN_VISIBILITY }
    }

    private fun visibleCount(landmarks: List<LandmarkFrame>, needed: Set<Int>) = landmarks.count { visibleIn(it, needed) }
}
