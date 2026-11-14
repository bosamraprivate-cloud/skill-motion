package com.aitherapist.dls.training

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.Criterion
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.vision.Geometry
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.MAX_GAP_MS
import com.aitherapist.dls.vision.PoseLandmarkIndex
import com.aitherapist.dls.vision.VisibilityGate
import com.aitherapist.dls.vision.candidateSides
import com.aitherapist.dls.vision.indices
import com.aitherapist.dls.vision.point

/**
 * Pure counter for one camera-counted activity [Criterion] (Story 4.5; `criterion.schema.json`).
 * Reps use hysteresis on `low`/`high`: angleAbove/pointAbove count above `high` then below `low`;
 * angleBelow/distanceBelow count below `low` then above `high`. A rep counts only when it lasts at least
 * [MIN_REP_MS] from crossing start to crossing end and spent at least [MIN_PAST_START_MS] past start; an excursion
 * that falls back into the middle band before [MIN_PAST_START_MS] is dropped. Hold accumulates continuous time past
 * the start threshold (equal to the check's deg/ratio/margin, so hold = "check met") and resets on a break.
 * Unmeasurable frames (missing/invisible landmarks, non-finite values, zero shoulder width) are ignored; a gap above
 * [MAX_GAP_MS] (250 ms, the pose path's value) between measured frames resets the phase in progress. Frames not
 * newer than the last measured one are ignored. Each candidate side is tracked independently. `perSide`: each side
 * must reach `target`; otherwise the best side counts (max, never a merge of two arms).
 */
class RepCounter(private val criterion: Criterion) {
    private val check = criterion.check
    private val low = requireNotNull(criterion.low) { "counter needs low" }
    private val high = requireNotNull(criterion.high) { "counter needs high" }
    private val rising: Boolean =
        when (check) {
            is Check.AngleAbove, is Check.PointAbove -> true
            is Check.AngleBelow, is Check.DistanceBelow -> false
            else -> throw IllegalArgumentException("counter check must be angleAbove, angleBelow, pointAbove or distanceBelow")
        }
    private val checkSide: Side? =
        when (check) {
            is Check.AngleAbove -> check.side
            is Check.AngleBelow -> check.side
            is Check.PointAbove -> check.side
            is Check.DistanceBelow -> check.side
            else -> null
        }
    private val checkThreshold: Double =
        when (check) {
            is Check.AngleAbove -> check.deg
            is Check.AngleBelow -> check.deg
            is Check.PointAbove -> check.margin
            is Check.DistanceBelow -> check.ratio
            else -> Double.NaN
        }

    init {
        require(low < high) { "counter needs low < high" }
        require(checkThreshold == if (rising) high else low) { "check threshold must equal the counter's start threshold" }
    }

    private val trackers: Map<Side?, Tracker> = candidateSides(checkSide).associateWith { Tracker() }

    /** Repetitions per tracked side (key null for a side-less check). */
    val counts: Map<Side?, Int> get() = trackers.mapValues { it.value.count }

    /** Repetitions: the sum over sides when per side, else the best side. */
    val count: Int get() = trackers.values.map { it.count }.let { if (criterion.perSide) it.sum() else it.max() }

    /** Current continuous hold seconds per side. */
    val holdSec: Map<Side?, Double> get() = trackers.mapValues { it.value.currentMs() / MS_PER_SEC }

    /** Best continuous hold seconds per side. */
    val bestHoldSec: Map<Side?, Double> get() = trackers.mapValues { it.value.bestMs / MS_PER_SEC }

    /** Per side when `perSide` (every side), else any side reached `target`; counting continues afterwards. */
    val targetReached: Boolean
        get() {
            val reached =
                trackers.values.map { t ->
                    when (criterion) {
                        is Criterion.Reps -> t.count >= criterion.target
                        is Criterion.Hold -> t.bestMs / MS_PER_SEC >= criterion.target
                    }
                }
            return if (criterion.perSide) reached.all { it } else reached.any { it }
        }

    fun feed(frame: LandmarkFrame) {
        trackers.forEach { (side, tracker) -> measure(frame, side)?.let { tracker.update(frame.timestampMs, it) } }
    }

    private fun measure(frame: LandmarkFrame, side: Side?): Double? {
        val (needed, compute) =
            when (check) {
                is Check.AngleAbove -> angle(check.a, check.vertex, check.b, side)
                is Check.AngleBelow -> angle(check.a, check.vertex, check.b, side)
                is Check.PointAbove -> rise(check.point, check.ref, side)
                is Check.DistanceBelow -> distance(check.a, check.b, side)
                else -> return null
            }
        if (!VisibilityGate.visibleIn(frame, needed)) return null
        return compute(frame.pose!!)?.takeIf { it.isFinite() }
    }

    private fun angle(a: LandmarkRef, v: LandmarkRef, b: LandmarkRef, side: Side?): Pair<Set<Int>, (List<LandmarkPoint>) -> Double?> =
        (indices(a, side) + indices(v, side) + indices(b, side)) to
            { pose -> Geometry.angleDeg(point(pose, a, side), point(pose, v, side), point(pose, b, side)) }

    private fun distance(a: LandmarkRef, b: LandmarkRef, side: Side?): Pair<Set<Int>, (List<LandmarkPoint>) -> Double?> =
        (SHOULDERS + indices(a, side) + indices(b, side)) to
            { pose -> Geometry.ratio(Geometry.distance(point(pose, a, side), point(pose, b, side)), shoulderWidth(pose)) }

    /** Image y grows downward, so the rise is ref.y - point.y in shoulder widths. */
    private fun rise(p: LandmarkRef, ref: LandmarkRef, side: Side?): Pair<Set<Int>, (List<LandmarkPoint>) -> Double?> =
        (SHOULDERS + indices(p, side) + indices(ref, side)) to
            { pose -> Geometry.ratio((point(pose, ref, side).y - point(pose, p, side).y).toDouble(), shoulderWidth(pose)) }

    private fun shoulderWidth(pose: List<LandmarkPoint>) =
        Geometry.shoulderWidth(pose[PoseLandmarkIndex.LEFT_SHOULDER], pose[PoseLandmarkIndex.RIGHT_SHOULDER])

    private fun pastStart(v: Double) = if (rising) v > high else v < low

    private fun pastEnd(v: Double) = if (rising) v < low else v > high

    /** Per-side state: rep phase and hold run. */
    private inner class Tracker {
        var count = 0
        var bestMs = 0L
        private var excursionStart: Long? = null
        private var lastPastStart: Long? = null
        private var holdStart: Long? = null
        private var lastTs: Long? = null

        fun currentMs(): Long = holdStart?.let { start -> (lastTs ?: start) - start } ?: 0L

        fun update(ts: Long, v: Double) {
            val previous = lastTs
            if (previous != null && ts <= previous) return
            if (previous != null && ts - previous > MAX_GAP_MS) {
                excursionStart = null
                holdStart = null
            }
            lastTs = ts
            if (pastStart(v)) {
                if (excursionStart == null) excursionStart = ts
                lastPastStart = ts
                if (holdStart == null) holdStart = ts
                bestMs = maxOf(bestMs, currentMs())
                return
            }
            holdStart = null
            val start = excursionStart ?: return
            val pastMs = (lastPastStart ?: start) - start
            if (pastEnd(v)) {
                if (ts - start >= MIN_REP_MS && pastMs >= MIN_PAST_START_MS) count++
                excursionStart = null
            } else if (pastMs < MIN_PAST_START_MS) {
                excursionStart = null
            }
        }
    }

    companion object {
        const val MIN_REP_MS = 400L
        const val MIN_PAST_START_MS = 100L
        private const val MS_PER_SEC = 1_000.0
        private val SHOULDERS = setOf(PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER)
    }
}
