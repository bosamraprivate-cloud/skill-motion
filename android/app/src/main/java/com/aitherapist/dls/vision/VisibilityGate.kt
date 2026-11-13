package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.catalog.StepDetector

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

    /* ---- Framing check (Story 3.4) ---- */

    /** Edge band (normalised) that counts as touching the frame edge. */
    const val EDGE_MARGIN = 0.02f

    /** Visible body wider or taller than this share of the frame: step back. */
    const val MAX_EXTENT = 0.9f

    /** Visible body smaller than this share of the frame in both directions: move closer. */
    const val MIN_EXTENT = 0.25f

    /** Body centre further than this from the frame centre (horizontally): move left or right. */
    const val MAX_OFFSET = 0.2f

    /** Needed landmarks must stay visible this long, continuously, before the outline turns sage. */
    const val HOLD_MS = 1_000L

    /** A gap longer than this between landmark updates resets the hold (a dropped stream is not "continuous"). */
    const val MAX_GAP_MS = 300L

    /** Pose and hand results further apart than this are not paired (AD-3 100 ms rule). */
    const val PAIR_MS = 100L

    private val SHOULDERS = setOf(PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER)
    private val WRISTS = setOf(PoseLandmarkIndex.LEFT_WRIST, PoseLandmarkIndex.RIGHT_WRIST)
    private val HIPS = setOf(PoseLandmarkIndex.LEFT_HIP, PoseLandmarkIndex.RIGHT_HIP)

    /**
     * The single place for each framing's default body set, used for cloud steps (whose question names no landmark):
     * table = shoulders + wrists + nose; standing = shoulders + hips + wrists; sink = shoulders + wrists + both hands.
     */
    fun framingDefault(framing: Framing): NeededLandmarks = when (framing) {
        Framing.TABLE -> NeededLandmarks(SHOULDERS + WRISTS + PoseLandmarkIndex.NOSE, hands = 0)
        Framing.STANDING -> NeededLandmarks(SHOULDERS + HIPS + WRISTS, hands = 0)
        Framing.SINK -> NeededLandmarks(SHOULDERS + WRISTS, hands = 2)
    }

    /**
     * Landmarks a task needs (union over its steps): landmarks referenced by local checks (both sides for `either`;
     * distance and point checks add the shoulders they scale by), the framing default for cloud steps, nothing for
     * `none`. Hand steps need one hand, hands rubbing needs both. Empty for a content-only task.
     */
    fun neededLandmarks(detectors: List<StepDetector>): NeededLandmarks {
        val framing = Framing.of(detectors.firstNotNullOfOrNull { it.framing })
        var pose = emptySet<Int>()
        var hands = 0
        for (d in detectors) {
            when (d.method) {
                Method.NONE -> Unit
                Method.CLOUD -> framingDefault(framing).let {
                    pose = pose + it.pose
                    hands = maxOf(hands, it.hands)
                }
                Method.POSE, Method.HAND -> {
                    pose = pose + checkLandmarks(d.check)
                    if (d.method == Method.HAND) hands = maxOf(hands, 1)
                    if (d.check is Check.HandsRubbing) hands = 2
                }
            }
        }
        return NeededLandmarks(pose, hands)
    }

    private fun checkLandmarks(check: Check?): Set<Int> = when (check) {
        null, is Check.Question -> emptySet()
        is Check.AngleAbove -> refs(check.side, check.a, check.vertex, check.b)
        is Check.AngleBelow -> refs(check.side, check.a, check.vertex, check.b)
        is Check.PointAbove -> SHOULDERS + refs(check.side, check.point, check.ref)
        is Check.DistanceBelow -> SHOULDERS + refs(check.side, check.a, check.b)
        is Check.DistanceAbove -> SHOULDERS + refs(check.side, check.a, check.b)
        is Check.InZone -> refs(check.side, check.landmark)
        is Check.HandsRubbing -> SHOULDERS + WRISTS
    }

    private fun refs(side: Side?, vararg refs: LandmarkRef): Set<Int> =
        candidateSides(side).flatMap { s -> refs.flatMap { indices(it, s) } }.toSet()

    /** True when every needed pose landmark is visible and at least the needed number of hands was found. */
    fun framed(frame: LandmarkFrame, needed: NeededLandmarks): Boolean = visibleIn(frame, needed.pose) && frame.hands.size >= needed.hands

    /**
     * Purely geometric advice for the adult from one frame: the bounding box of the needed landmarks that are visible
     * and inside the frame (MediaPipe's predicted out-of-frame points are ignored) against the frame. Left/right are as
     * seen in the mirrored preview: the body showing left of centre means "move right".
     */
    fun guidance(frame: LandmarkFrame, needed: NeededLandmarks): FramingGuidance {
        val pose = frame.pose ?: return FramingGuidance.NO_PERSON
        val inside = needed.pose.filter { i ->
            pose.getOrNull(i)?.let { it.visibility > MIN_VISIBILITY && it.x in 0f..1f && it.y in 0f..1f } == true
        }
        if (inside.isEmpty()) return FramingGuidance.NO_PERSON
        val seen = inside.map { pose[it] }
        val minX = seen.minOf { it.x }
        val maxX = seen.maxOf { it.x }
        val minY = seen.minOf { it.y }
        val maxY = seen.maxOf { it.y }
        val touchesEdge = minX < EDGE_MARGIN || maxX > 1f - EDGE_MARGIN || minY < EDGE_MARGIN || maxY > 1f - EDGE_MARGIN
        val missing = needed.pose - inside.toSet()
        val shownCentre = 1f - (minX + maxX) / 2f // mirrored display
        return when {
            touchesEdge || maxX - minX > MAX_EXTENT || maxY - minY > MAX_EXTENT -> FramingGuidance.STEP_BACK
            shownCentre < 0.5f - MAX_OFFSET -> FramingGuidance.MOVE_RIGHT
            shownCentre > 0.5f + MAX_OFFSET -> FramingGuidance.MOVE_LEFT
            maxX - minX < MIN_EXTENT && maxY - minY < MIN_EXTENT -> FramingGuidance.MOVE_CLOSER
            missing.isNotEmpty() && WRISTS.containsAll(missing) -> FramingGuidance.SHOW_HANDS
            missing.isNotEmpty() -> FramingGuidance.STEP_BACK
            frame.hands.size < needed.hands -> FramingGuidance.SHOW_HANDS
            else -> FramingGuidance.OK
        }
    }

    /** Continuous-visibility timer on landmark timestamps: true once framed for [holdMs]; any lost frame resets it. */
    class Hold(private val holdMs: Long = HOLD_MS) {
        private var since: Long? = null
        private var last: Long? = null

        fun update(framed: Boolean, timestampMs: Long): Boolean {
            val previous = last
            last = timestampMs
            if (!framed) {
                since = null
                return false
            }
            if (previous != null && timestampMs - previous > MAX_GAP_MS) since = null
            val start = since ?: timestampMs.also { since = it }
            return timestampMs - start >= holdMs
        }
    }
}

/** Camera framing of a task, from the catalogue's detector `framing`; unknown or missing falls back to standing. */
enum class Framing {
    TABLE,
    STANDING,
    SINK;

    companion object {
        fun of(value: String?): Framing = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STANDING
    }
}

/** Pose landmark indices that must be visible, and how many hands must be found. */
data class NeededLandmarks(val pose: Set<Int>, val hands: Int) {
    val isEmpty: Boolean get() = pose.isEmpty() && hands == 0
}

/** Adult guidance on the framing check. */
enum class FramingGuidance { OK, NO_PERSON, MOVE_CLOSER, STEP_BACK, MOVE_LEFT, MOVE_RIGHT, SHOW_HANDS }
