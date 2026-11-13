package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Side
import kotlin.math.roundToLong

/**
 * Pure evaluator for every body [Check] type (AD-4, design "Check types"): `angleAbove`, `angleBelow`,
 * `pointAbove`, `distanceBelow`, `distanceAbove`, `inZone`. Visibility first, then a continuous `holdSec`
 * on any candidate side. Doubt is never failure: anything that cannot be measured becomes `CANNOT_TELL`.
 * `handsRubbing` is judged by [HandsRubbingEvaluator]. Cloud checks return `CANNOT_TELL` / `not_judgeable` (other verifiers handle them).
 * May throw on malformed content; callers catch.
 */
object CheckEvaluator {
    private const val MS_PER_SEC = 1_000.0

    /** A per-frame test for one side; returns null when the frame cannot be measured (e.g. zero shoulder width). */
    private class SideRule(val needed: Set<Int>, val test: (List<LandmarkPoint>) -> Boolean?)

    fun evaluate(check: Check?, landmarks: List<LandmarkFrame>, zones: Map<String, Zone>): Pair<Outcome, String> {
        val (holdSec, rules) =
            when (check) {
                is Check.AngleAbove ->
                    check.holdSec to
                        sides(check.side) { s -> angleRule(check.a, check.vertex, check.b, s) { it > check.deg } }
                is Check.AngleBelow ->
                    check.holdSec to
                        sides(check.side) { s -> angleRule(check.a, check.vertex, check.b, s) { it < check.deg } }
                is Check.PointAbove -> check.holdSec to sides(check.side) { s -> pointAboveRule(check, s) }
                is Check.DistanceBelow ->
                    check.holdSec to
                        sides(check.side) { s -> distanceRule(check.a, check.b, s) { it <= check.ratio } }
                is Check.DistanceAbove ->
                    check.holdSec to
                        sides(check.side) { s -> distanceRule(check.a, check.b, s) { it >= check.ratio } }
                is Check.InZone -> {
                    val zone = normalise(zones[check.zone]) ?: return Outcome.CANNOT_TELL to VerdictReason.NO_ZONE
                    check.holdSec to sides(check.side) { s -> zoneRule(check.landmark, zone, s) }
                }
                is Check.HandsRubbing -> return HandsRubbingEvaluator.judge(check, landmarks)
                else -> return Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE
            }
        return judge(rules, holdMillis(holdSec), landmarks)
    }

    /** Non-positive or non-finite hold means "at least one frame". */
    internal fun holdMillis(holdSec: Double): Long = if (holdSec.isFinite() && holdSec > 0.0) (holdSec * MS_PER_SEC).roundToLong() else 0L

    /** A zone with min/max edges, or null when missing, non-finite or without area. */
    private fun normalise(zone: Zone?): Zone? {
        zone ?: return null
        val edges = listOf(zone.left, zone.top, zone.right, zone.bottom)
        if (!edges.all { it.isFinite() }) return null
        val n = Zone(minOf(zone.left, zone.right), minOf(zone.top, zone.bottom), maxOf(zone.left, zone.right), maxOf(zone.top, zone.bottom))
        return if (n.right > n.left && n.bottom > n.top) n else null
    }

    private fun judge(rules: List<SideRule>, holdMs: Long, landmarks: List<LandmarkFrame>): Pair<Outcome, String> {
        if (rules.none { VisibilityGate.passes(landmarks, listOf(it.needed)) }) return Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME
        val ordered = landmarks.sortedBy { it.timestampMs }
        val passed =
            rules.any { rule ->
                VisibilityGate.passes(landmarks, listOf(rule.needed)) &&
                    holdsContinuously(ordered, holdMs) { frame ->
                        VisibilityGate.visibleIn(frame, rule.needed) &&
                            rule.test(frame.pose!!) == true
                    }
            }
        if (passed) return Outcome.DONE to VerdictReason.CHECK_PASSED
        // Doubt is decided per side: NOT_DONE only when every candidate side was measured well enough to rule the hold out.
        val doubts = rules.mapNotNull { doubt(it, holdMs, landmarks, ordered) }
        return when {
            VerdictReason.OUT_OF_FRAME in doubts -> Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME
            doubts.isNotEmpty() -> Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE
            else -> Outcome.NOT_DONE to VerdictReason.CHECK_FAILED
        }
    }

    /** Why this side cannot rule the hold out, or null when it was measured well enough. */
    private fun doubt(rule: SideRule, holdMs: Long, landmarks: List<LandmarkFrame>, ordered: List<LandmarkFrame>): String? {
        if (!VisibilityGate.passes(landmarks, listOf(rule.needed))) return VerdictReason.OUT_OF_FRAME
        val seen: (LandmarkFrame) -> Boolean = { VisibilityGate.visibleIn(it, rule.needed) }
        val measured: (LandmarkFrame) -> Boolean = { seen(it) && rule.test(it.pose!!) != null }
        if (holdMs == 0L) return if (ordered.any(measured)) null else VerdictReason.NOT_JUDGEABLE
        if (ordered.last().timestampMs - ordered.first().timestampMs < holdMs) return VerdictReason.OUT_OF_FRAME
        return when {
            longestDoubt(ordered, seen) >= holdMs -> VerdictReason.OUT_OF_FRAME
            longestDoubt(ordered, measured) >= holdMs -> VerdictReason.NOT_JUDGEABLE
            else -> null
        }
    }

    /**
     * Longest time the hold could have happened unobserved: spans between good frames that contain bad frames
     * or a gap above [MAX_GAP_MS], plus the stretches from the window edges to the first/last good frame.
     */
    internal fun longestDoubt(ordered: List<LandmarkFrame>, good: (LandmarkFrame) -> Boolean): Long {
        val firstTs = ordered.first().timestampMs
        var lastGood: Long? = null
        var badSince = false
        var longest = 0L
        var previousTs = firstTs
        for (frame in ordered) {
            val ts = frame.timestampMs
            if (ts - previousTs > MAX_GAP_MS) badSince = true
            previousTs = ts
            if (good(frame)) {
                val from = lastGood ?: firstTs
                if (lastGood == null || badSince) longest = maxOf(longest, ts - from)
                lastGood = ts
                badSince = false
            } else {
                badSince = true
            }
        }
        val end = ordered.last().timestampMs
        longest = maxOf(longest, end - (lastGood ?: firstTs))
        return longest
    }

    private fun sides(side: Side?, rule: (Side?) -> SideRule): List<SideRule> = candidateSides(side).map(rule)

    private val SHOULDERS = setOf(PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER)

    private fun shoulderWidth(pose: List<LandmarkPoint>) =
        Geometry.shoulderWidth(pose[PoseLandmarkIndex.LEFT_SHOULDER], pose[PoseLandmarkIndex.RIGHT_SHOULDER])

    private fun angleRule(a: LandmarkRef, vertex: LandmarkRef, b: LandmarkRef, side: Side?, passes: (Double) -> Boolean) =
        SideRule(indices(a, side) + indices(vertex, side) + indices(b, side)) { pose ->
            Geometry.angleDeg(point(pose, a, side), point(pose, vertex, side), point(pose, b, side)).finiteOrNull()?.let(passes)
        }

    private fun distanceRule(a: LandmarkRef, b: LandmarkRef, side: Side?, passes: (Double) -> Boolean) =
        SideRule(SHOULDERS + indices(a, side) + indices(b, side)) { pose ->
            Geometry.ratio(Geometry.distance(point(pose, a, side), point(pose, b, side)), shoulderWidth(pose)).finiteOrNull()?.let(passes)
        }

    /** Image y grows downward, so "higher on screen" means a smaller y. */
    private fun pointAboveRule(check: Check.PointAbove, side: Side?) =
        SideRule(SHOULDERS + indices(check.point, side) + indices(check.ref, side)) { pose ->
            val rise = (point(pose, check.ref, side).y - point(pose, check.point, side).y).toDouble()
            Geometry.ratio(rise, shoulderWidth(pose)).finiteOrNull()?.let { it >= check.margin }
        }

    private fun zoneRule(landmark: LandmarkRef, zone: Zone, side: Side?) = SideRule(indices(landmark, side)) { pose ->
        val p = point(pose, landmark, side)
        if (p.x.isFinite() && p.y.isFinite()) p.x in zone.left..zone.right && p.y in zone.top..zone.bottom else null
    }

    /** Non-finite values (NaN/Inf from bad coordinates) are unmeasurable, never a fail. */
    private fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
}
