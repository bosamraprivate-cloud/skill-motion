package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.StepDetector
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure rules of the AI scene check (Story 6.4). */
object ScenePlan {
    /** Always requested with a task's zone kinds so the child's box is found too (owner, 2026-11-15). */
    const val PERSON = "person"

    /** The zone kinds the task's steps reference (catalogue `inZone` checks), in first-use order. */
    fun zoneKinds(detectors: List<StepDetector>): List<String> = detectors.mapNotNull { (it.check as? Check.InZone)?.zone }.distinct()

    /** Kinds to request: the zone kinds plus [PERSON]; empty (no scene phase) when the task uses no zones. */
    fun requestKinds(zoneKinds: List<String>): List<String> = if (zoneKinds.isEmpty()) emptyList() else zoneKinds + PERSON

    /**
     * Client-side acceptance: every zone kind has a box and no issue blocks. Issues about the person (missing, partly
     * visible, in view) never block: the room may be empty or the child already seated.
     */
    fun accepted(result: SceneResult.Checked, zoneKinds: List<String>): Boolean =
        zoneKinds.all { k -> result.objects.any { it.kind == k } } && blockingIssues(result).isEmpty()

    /** Issues shown to the therapist (person-related ones dropped), deduplicated. */
    fun blockingIssues(result: SceneResult.Checked): List<SceneIssue> =
        result.issues.filterNot { it.code == SceneIssueCode.PERSON_IN_VIEW || it.kind == PERSON }.distinct()

    /** Returned boxes as stored zones (normalised, un-mirrored), person included (it widens cloud crops). */
    fun zonesOf(result: SceneResult.Checked): Map<String, Zone> =
        result.objects.mapNotNull { o -> o.box.toZone()?.let { o.kind to it } }.toMap()
}

/**
 * Face blur geometry (owner, 2026-11-15): no unblurred face leaves the tablet. The face box is the visible pose face
 * points (nose, eyes, ears, mouth) padded by [PAD] of their size per side and at least [MIN_SIDE]; without face points
 * but with a person box, the top [HEAD_FRACTION] of the person box.
 */
object FaceBlur {
    val FACE_POINTS: IntRange = 0..10
    const val PAD = 0.6f
    const val MIN_SIDE = 0.08f
    const val HEAD_FRACTION = 0.25f

    fun faceBox(pose: List<LandmarkPoint>?, person: Zone?): Zone? {
        val pts = pose?.let { p -> FACE_POINTS.mapNotNull { p.getOrNull(it) } }.orEmpty().filter {
            it.visibility >
                VisibilityGate.MIN_VISIBILITY
        }
        if (pts.isNotEmpty()) {
            var l = pts.minOf { it.x }
            var r = pts.maxOf { it.x }
            var t = pts.minOf { it.y }
            var b = pts.maxOf { it.y }
            val side = max(max(r - l, b - t), MIN_SIDE)
            val cx = (l + r) / 2
            val cy = (t + b) / 2
            val half = side * (0.5f + PAD)
            l = cx - half
            r = cx + half
            t = cy - half
            b = cy + half
            return clamp(Zone(l, t, r, b))
        }
        return person?.let { clamp(Zone(it.left, it.top, it.right, it.top + (it.bottom - it.top) * HEAD_FRACTION)) }
    }

    /** [zone] in pixels of a [w] x [h] frame, relative to [crop] (the part that is encoded); null when outside it. */
    fun inCrop(zone: Zone, w: Int, h: Int, crop: CropBox): CropBox? {
        val l = max((zone.left * w).roundToInt(), crop.left) - crop.left
        val r = min((zone.right * w).roundToInt(), crop.right) - crop.left
        val t = max((zone.top * h).roundToInt(), crop.top) - crop.top
        val b = min((zone.bottom * h).roundToInt(), crop.bottom) - crop.top
        return if (r > l && b > t) CropBox(l, t, r, b) else null
    }

    private fun clamp(z: Zone): Zone? {
        val c = Zone(z.left.coerceIn(0f, 1f), z.top.coerceIn(0f, 1f), z.right.coerceIn(0f, 1f), z.bottom.coerceIn(0f, 1f))
        return if (c.right > c.left && c.bottom > c.top) c else null
    }
}
