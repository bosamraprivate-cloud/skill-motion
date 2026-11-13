package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import kotlin.math.abs
import kotlin.math.hypot

/**
 * `handsRubbing` (design "Check types"): both hands tracked, wrists (hand landmark 0) closer than
 * `maxWristRatio` x scale, and the relative movement of the two wrists over each 0.5 s window above
 * `minMotionRatio` x scale, held continuously for `holdSec`.
 *
 * - Motion: for each frame, the largest distance of the wrist offset (second wrist minus first) from its
 *   value at the window start, over every frame of the 0.5 s window ending at that frame. The hold runs from
 *   the first passing frame, so DONE needs `holdSec` + 0.5 s of rubbing.
 * - Hand identity: each hand is tracked by nearest wrist to the previous measured frame. When both
 *   pairings cost about the same (difference below `minMotionRatio` x scale) the frame is unmeasured.
 *   More than two hands in a frame: unmeasured, never pick two.
 * - Scale: pose shoulder width in the same frame. When the shoulders are missing, the mean hand size
 *   (wrist to middle-finger MCP, landmark 9) is used; a frame scaled by hand size can pass, but if it would
 *   fail it counts as unmeasured (doubt), never as a fail.
 * - A single isolated far or unmeasured frame is skipped (inside a window and inside the hold), not a break.
 * - Doubt: fewer than two hands in half the frames, or for a stretch >= hold + 0.5 s, gives `out_of_frame`;
 *   a stretch >= hold + 0.5 s without a failing frame gives `not_judgeable`. Doubt is never failure.
 */
internal object HandsRubbingEvaluator {
    private const val MOTION_WINDOW_MS = 500L
    private const val WRIST = 0
    private const val MIDDLE_MCP = 9

    private enum class State { PASS, FAIL, UNKNOWN }

    private class Sample(
        val ts: Long,
        val ax: Double,
        val ay: Double,
        val bx: Double,
        val by: Double,
        val scale: Double,
        val shoulderScaled: Boolean,
        val segment: Int
    ) {
        val dx get() = bx - ax
        val dy get() = by - ay
    }

    fun judge(check: Check.HandsRubbing, landmarks: List<LandmarkFrame>): Pair<Outcome, String> {
        val ordered = landmarks.sortedBy { it.timestampMs }
        val seen: (LandmarkFrame) -> Boolean = { f -> f.hands.count { it.size > MIDDLE_MCP } >= 2 }
        if (ordered.isEmpty() || ordered.count(seen) * 2 <= ordered.size) return Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME
        val holdMs = CheckEvaluator.holdMillis(check.holdSec)
        val samples = track(check, ordered)
        val states = samples.indices.map { state(check, samples, ordered, it) }
        if (holds(ordered, states, holdMs)) return Outcome.DONE to VerdictReason.CHECK_PASSED
        val doubtMs = holdMs + MOTION_WINDOW_MS
        val failed = ordered.indices.filter { states[it] == State.FAIL }.map { ordered[it] }.toSet()
        return when {
            ordered.last().timestampMs - ordered.first().timestampMs < doubtMs -> Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME
            CheckEvaluator.longestDoubt(ordered, seen) >= doubtMs -> Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME
            CheckEvaluator.longestDoubt(ordered) { it in failed } >= doubtMs -> Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE
            else -> Outcome.NOT_DONE to VerdictReason.CHECK_FAILED
        }
    }

    private fun holds(ordered: List<LandmarkFrame>, states: List<State>, holdMs: Long): Boolean {
        var runStart: Long? = null
        for (i in states.indices) {
            val ts = ordered[i].timestampMs
            if (i > 0 && ts - ordered[i - 1].timestampMs > MAX_GAP_MS) runStart = null
            val skippable = states[i] == State.UNKNOWN && states.getOrNull(i - 1) == State.PASS && states.getOrNull(i + 1) == State.PASS
            if (skippable) continue
            if (states[i] == State.PASS) {
                val start = runStart ?: ts
                runStart = start
                if (ts - start >= holdMs) return true
            } else {
                runStart = null
            }
        }
        return false
    }

    private fun close(check: Check.HandsRubbing, s: Sample) = hypot(s.dx, s.dy) < check.maxWristRatio * s.scale

    /** A single far frame between two close ones is noise, not a fail. */
    private fun isolated(check: Check.HandsRubbing, samples: List<Sample?>, i: Int): Boolean {
        val before = samples.getOrNull(i - 1)
        val after = samples.getOrNull(i + 1)
        return before != null && after != null && close(check, before) && close(check, after)
    }

    /** A failing judgement, or unmeasured when the frame is scaled by hand size. */
    private fun fail(s: Sample) = if (s.shoulderScaled) State.FAIL else State.UNKNOWN

    private fun state(check: Check.HandsRubbing, samples: List<Sample?>, ordered: List<LandmarkFrame>, i: Int): State {
        val now = samples[i] ?: return State.UNKNOWN
        if (!close(check, now)) return if (isolated(check, samples, i)) State.UNKNOWN else fail(now)
        var j = i
        while (now.ts - ordered[j].timestampMs < MOTION_WINDOW_MS) {
            if (j == 0 || ordered[j].timestampMs - ordered[j - 1].timestampMs > MAX_GAP_MS) return State.UNKNOWN
            j--
        }
        val start = samples[j]?.takeIf { close(check, it) && it.segment == now.segment } ?: return State.UNKNOWN
        var motion = 0.0
        var previousBad: Sample? = null
        var previousWasBad = false
        for (k in j..i) {
            val s = samples[k]
            if (s != null && s.segment == now.segment && close(check, s)) {
                motion = maxOf(motion, hypot(s.dx - start.dx, s.dy - start.dy))
                previousWasBad = false
                previousBad = null
                continue
            }
            if (previousWasBad) {
                // Two bad frames in a row: a real fail only when both were measured far by shoulder scale.
                val bothFar = s != null && previousBad != null && s.shoulderScaled && previousBad.shoulderScaled
                return if (bothFar) State.FAIL else State.UNKNOWN
            }
            previousWasBad = true
            previousBad = s
        }
        return if (motion > check.minMotionRatio * now.scale) State.PASS else fail(now)
    }

    /** Measures every frame and keeps the identity of each hand by nearest wrist; null marks an unmeasured frame. */
    private fun track(check: Check.HandsRubbing, ordered: List<LandmarkFrame>): List<Sample?> {
        val samples = ArrayList<Sample?>(ordered.size)
        var segment = 0
        var lastIndex = -1
        for ((i, frame) in ordered.withIndex()) {
            samples.add(measure(check, frame, samples.getOrNull(lastIndex)?.takeIf { continuous(ordered, lastIndex, i) }, segment))
            val added = samples[i] ?: continue
            segment = added.segment
            lastIndex = i
        }
        return samples
    }

    private fun measure(check: Check.HandsRubbing, frame: LandmarkFrame, last: Sample?, segment: Int): Sample? {
        val hands = frame.hands
        if (hands.size != 2 || hands.any { it.size <= MIDDLE_MCP }) return null
        val (scale, shoulderScaled) = scale(frame, hands[0], hands[1]) ?: return null
        val p0 = hands[0][WRIST]
        val p1 = hands[1][WRIST]
        if (!listOf(p0.x, p0.y, p1.x, p1.y).all { it.isFinite() }) return null
        var swap = false
        if (last != null) {
            val straight = hypot(p0.x - last.ax, p0.y - last.ay) + hypot(p1.x - last.bx, p1.y - last.by)
            val swapped = hypot(p0.x - last.bx, p0.y - last.by) + hypot(p1.x - last.ax, p1.y - last.ay)
            if (abs(straight - swapped) < check.minMotionRatio * scale) return null
            swap = swapped < straight
        }
        val (a, b) = if (swap) p1 to p0 else p0 to p1
        val seg = if (last != null) segment else segment + 1
        return Sample(frame.timestampMs, a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble(), scale, shoulderScaled, seg)
    }

    /** The previous measured frame is usable when at most one frame lies between and no gap exceeds [MAX_GAP_MS]. */
    private fun continuous(ordered: List<LandmarkFrame>, from: Int, to: Int): Boolean =
        to - from <= 2 && (from + 1..to).all { ordered[it].timestampMs - ordered[it - 1].timestampMs <= MAX_GAP_MS }

    /** Scale and whether it came from the shoulders, or null when no usable scale exists. */
    private fun scale(frame: LandmarkFrame, a: List<LandmarkPoint>, b: List<LandmarkPoint>): Pair<Double, Boolean>? {
        val shoulders = setOf(PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER)
        if (VisibilityGate.visibleIn(frame, shoulders)) {
            val pose = frame.pose!!
            val w = Geometry.shoulderWidth(pose[PoseLandmarkIndex.LEFT_SHOULDER], pose[PoseLandmarkIndex.RIGHT_SHOULDER])
            if (w.isFinite() && w > 0.0) return w to true
        }
        val size = (Geometry.distance(a[WRIST], a[MIDDLE_MCP]) + Geometry.distance(b[WRIST], b[MIDDLE_MCP])) / 2
        return if (size.isFinite() && size > 0.0) size to false else null
    }

    private fun hypot(x: Float, y: Float) = hypot(x.toDouble(), y.toDouble())
}
