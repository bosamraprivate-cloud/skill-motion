package com.aitherapist.dls.vision

/** Landmark groups shown on Diagnostics (Story 5.5). */
enum class LandmarkGroup { HEAD, SHOULDERS, ARMS, HIPS, HANDS }

/** How well a group is seen, shown as a word next to the bar (never colour alone). */
enum class VisibilityLevel { CLEAR, PARTLY, NOT_SEEN }

/**
 * Rolling per-group visibility over the last [window] pose and hand results. A pose group counts as seen in a
 * result when every one of its landmarks is above [VisibilityGate.MIN_VISIBILITY]; hands count as seen when the
 * hand landmarker found at least one hand. Not thread-safe; feed it from one collector.
 */
class GroupVisibility(private val window: Int = DEFAULT_WINDOW) {
    private val pose = ArrayDeque<Set<LandmarkGroup>>()
    private val hands = ArrayDeque<Boolean>()

    fun onPose(points: List<LandmarkPoint>?) {
        val seen =
            POSE_GROUPS
                .filterValues { indices ->
                    points != null && indices.all { (points.getOrNull(it)?.visibility ?: 0f) > VisibilityGate.MIN_VISIBILITY }
                }.keys
        pose.addLast(seen)
        while (pose.size > window) pose.removeFirst()
    }

    fun onHands(sets: List<List<LandmarkPoint>>) {
        hands.addLast(sets.isNotEmpty())
        while (hands.size > window) hands.removeFirst()
    }

    fun clear() {
        pose.clear()
        hands.clear()
    }

    /** Whole percent per group (0 when nothing has arrived yet). */
    fun percents(): Map<LandmarkGroup, Int> = LandmarkGroup.entries.associateWith { group ->
        if (group == LandmarkGroup.HANDS) {
            percent(hands.count { it }, hands.size)
        } else {
            percent(pose.count { group in it }, pose.size)
        }
    }

    companion object {
        const val DEFAULT_WINDOW = 30
        const val CLEAR_PERCENT = 80
        const val PARTLY_PERCENT = 40
        private const val PERCENT = 100

        private val POSE_GROUPS =
            mapOf(
                LandmarkGroup.HEAD to setOf(PoseLandmarkIndex.NOSE, PoseLandmarkIndex.MOUTH_LEFT, PoseLandmarkIndex.MOUTH_RIGHT),
                LandmarkGroup.SHOULDERS to setOf(PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER),
                LandmarkGroup.ARMS to
                    setOf(
                        PoseLandmarkIndex.LEFT_ELBOW,
                        PoseLandmarkIndex.RIGHT_ELBOW,
                        PoseLandmarkIndex.LEFT_WRIST,
                        PoseLandmarkIndex.RIGHT_WRIST
                    ),
                LandmarkGroup.HIPS to setOf(PoseLandmarkIndex.LEFT_HIP, PoseLandmarkIndex.RIGHT_HIP)
            )

        fun level(percent: Int): VisibilityLevel = when {
            percent >= CLEAR_PERCENT -> VisibilityLevel.CLEAR
            percent >= PARTLY_PERCENT -> VisibilityLevel.PARTLY
            else -> VisibilityLevel.NOT_SEEN
        }

        private fun percent(n: Int, total: Int): Int = if (total == 0) 0 else (n * PERCENT + total / 2) / total
    }
}
