package com.aitherapist.dls.vision

/**
 * Normalised landmark position in un-mirrored image space (subject's right side has the smaller x).
 * [visibility] is MediaPipe's pose visibility (0..1); hand points carry 1f.
 */
data class LandmarkPoint(val x: Float, val y: Float, val visibility: Float = 1f)

/** MediaPipe Pose landmark indices, names as in the MediaPipe documentation (un-mirrored frame). */
object PoseLandmarkIndex {
    const val NOSE = 0
    const val MOUTH_LEFT = 9
    const val MOUTH_RIGHT = 10
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16

    private val NAMES =
        listOf(
            "NOSE", "LEFT_EYE_INNER", "LEFT_EYE", "LEFT_EYE_OUTER", "RIGHT_EYE_INNER", "RIGHT_EYE", "RIGHT_EYE_OUTER",
            "LEFT_EAR", "RIGHT_EAR", "MOUTH_LEFT", "MOUTH_RIGHT", "LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW",
            "RIGHT_ELBOW", "LEFT_WRIST", "RIGHT_WRIST", "LEFT_PINKY", "RIGHT_PINKY", "LEFT_INDEX", "RIGHT_INDEX",
            "LEFT_THUMB", "RIGHT_THUMB", "LEFT_HIP", "RIGHT_HIP", "LEFT_KNEE", "RIGHT_KNEE", "LEFT_ANKLE",
            "RIGHT_ANKLE", "LEFT_HEEL", "RIGHT_HEEL", "LEFT_FOOT_INDEX", "RIGHT_FOOT_INDEX"
        )

    /** All 33 MediaPipe Pose landmark names to their indices. */
    val byName: Map<String, Int> = NAMES.withIndex().associate { (i, name) -> name to i }
}
