package com.aitherapist.dls.session

import com.aitherapist.dls.vision.Geometry
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.PoseLandmarkIndex
import com.aitherapist.dls.vision.VisibilityGate

/**
 * One observation at [tMs]; [joints] maps MediaPipe Pose indices to points (empty when no pose was found). [hands] are
 * the hand landmark sets found in the same frame; their wrists count as movement too.
 */
data class PoseSample(val tMs: Long, val joints: Map<Int, LandmarkPoint>, val hands: List<List<LandmarkPoint>> = emptyList()) {
    val hasPose: Boolean get() = joints.isNotEmpty()
}

/** Movement test (FR-13). Pure Kotlin, no Android types. */
object Movement {
    /** Joints whose displacement counts as movement. */
    val TRACKED_JOINTS =
        setOf(
            PoseLandmarkIndex.LEFT_WRIST,
            PoseLandmarkIndex.RIGHT_WRIST,
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.RIGHT_ELBOW
        )

    /**
     * True when any tracked joint's max displacement among samples in the last
     * [WindowRules.MOVEMENT_SPAN_MS] (up to [nowMs]) is strictly greater than
     * [WindowRules.MOVEMENT_SHOULDER_FRACTION] x shoulder width. Low-visibility points are skipped per
     * joint; without a known, non-zero shoulder width the answer is false.
     */
    fun moved(samples: List<PoseSample>, nowMs: Long): Boolean {
        val recent = samples.filter { it.tMs in (nowMs - WindowRules.MOVEMENT_SPAN_MS)..nowMs }
        val shoulderWidth = shoulderWidth(recent) ?: return false
        val threshold = WindowRules.MOVEMENT_SHOULDER_FRACTION * shoulderWidth
        val bodyMoved =
            TRACKED_JOINTS.any { joint ->
                val points = recent.mapNotNull { it.joints[joint]?.takeIf(::visible) }
                maxDisplacement(points) > threshold
            }
        // Hands are matched across samples by their order left to right in the frame (hand wrist = point 0).
        val handMoved =
            (0 until MAX_HANDS).any { slot ->
                val points = recent.mapNotNull { sample -> sample.hands.mapNotNull { it.firstOrNull() }.sortedBy { it.x }.getOrNull(slot) }
                maxDisplacement(points) > threshold
            }
        return bodyMoved || handMoved
    }

    /** Shoulder width from the latest sample with both shoulders visible, or null if none / zero. */
    private fun shoulderWidth(samples: List<PoseSample>): Double? = samples.sortedByDescending { it.tMs }.firstNotNullOfOrNull { sample ->
        val left = sample.joints[PoseLandmarkIndex.LEFT_SHOULDER]?.takeIf(::visible)
        val right = sample.joints[PoseLandmarkIndex.RIGHT_SHOULDER]?.takeIf(::visible)
        if (left != null && right != null) Geometry.shoulderWidth(left, right).takeIf { it > 0.0 } else null
    }

    private const val MAX_HANDS = 2

    private fun visible(point: LandmarkPoint) = point.visibility > VisibilityGate.MIN_VISIBILITY

    private fun maxDisplacement(points: List<LandmarkPoint>): Double {
        var max = 0.0
        for (i in points.indices) {
            for (j in i + 1 until points.size) max = maxOf(max, Geometry.distance(points[i], points[j]))
        }
        return max
    }
}
