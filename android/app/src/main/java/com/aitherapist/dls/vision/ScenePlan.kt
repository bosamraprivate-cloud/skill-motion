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

    /** Kinds that must each have a box: the zone kinds plus the task's catalogue `sceneObjects`, deduplicated, never [PERSON]. */
    fun requiredKinds(zoneKinds: List<String>, sceneObjects: List<String> = emptyList()): List<String> =
        (zoneKinds + sceneObjects).distinct().filterNot { it == PERSON }

    /** Kinds to request: the required kinds plus [PERSON]; empty (no scene phase) when nothing is required. */
    fun requestKinds(zoneKinds: List<String>, sceneObjects: List<String> = emptyList()): List<String> =
        requiredKinds(zoneKinds, sceneObjects).let { if (it.isEmpty()) emptyList() else it + PERSON }

    /**
     * Client-side acceptance: every required kind (zones and scene objects) has a box and no issue blocks. Issues about the person (missing, partly
     * visible, in view) never block: the room may be empty or the child already seated.
     */
    fun accepted(result: SceneResult.Checked, requiredKinds: List<String>): Boolean =
        requiredKinds.filterNot { it == PERSON }.all { k -> result.objects.any { it.kind == k } } && blockingIssues(result).isEmpty()

    /** Issues shown to the therapist (person-related ones dropped), deduplicated. */
    fun blockingIssues(result: SceneResult.Checked): List<SceneIssue> =
        result.issues.filterNot { it.code == SceneIssueCode.PERSON_IN_VIEW || it.kind == PERSON }.distinct()

    /**
     * Returned boxes as stored zones (normalised, un-mirrored), person included (it widens cloud crops). Only the
     * [zoneKinds] scoring uses and [PERSON] are kept; scene-object boxes are shown, never saved (scoring unchanged).
     */
    fun zonesOf(result: SceneResult.Checked, zoneKinds: List<String>): Map<String, Zone> = result.objects
        .filter { it.kind in zoneKinds || it.kind == PERSON }
        .mapNotNull { o -> o.box.toZone()?.let { o.kind to it } }
        .toMap()
}

/**
 * Face blur geometry (owner, 2026-11-15): only the eye band is blurred, so the mouth stays visible for eating and
 * drinking steps. With visible eye points the band spans ear to ear (eyes and ears, padded by [PAD_X] of their width
 * per side, at least [MIN_SIDE] wide) and from [ABOVE] face units above the eyes (eyebrows, forehead) to [BELOW] units
 * below them; a face unit is the eye-to-mouth distance (else twice eye-to-nose, else [UNIT_OF_WIDTH] of the eye-ear
 * width). Without eye points but with a person box: the top [HEAD_FRACTION] of the person box.
 */
object FaceBlur {
    val EYE_POINTS: IntRange = 1..6
    val EAR_POINTS: IntRange = 7..8
    val NOSE_POINTS: IntRange = 0..0
    val MOUTH_POINTS: IntRange = 9..10
    const val PAD_X = 0.2f
    const val ABOVE = 1.1f
    const val BELOW = 0.45f
    const val UNIT_OF_WIDTH = 0.45f
    const val MIN_SIDE = 0.08f
    const val HEAD_FRACTION = 0.15f

    fun faceBox(pose: List<LandmarkPoint>?, person: Zone?): Zone? {
        fun seen(range: IntRange) = range.mapNotNull { pose?.getOrNull(it) }.filter { it.visibility > VisibilityGate.MIN_VISIBILITY }
        val eyes = seen(EYE_POINTS)
        if (eyes.isEmpty()) {
            return person?.let { clamp(Zone(it.left, it.top, it.right, it.top + (it.bottom - it.top) * HEAD_FRACTION)) }
        }
        val across = eyes + seen(EAR_POINTS)
        val eyeY = eyes.map { it.y }.average().toFloat()
        val width = across.maxOf { it.x } - across.minOf { it.x }
        val mouth = seen(MOUTH_POINTS)
        val nose = seen(NOSE_POINTS)
        val measured =
            when {
                mouth.isNotEmpty() -> mouth.map { it.y }.average().toFloat() - eyeY
                nose.isNotEmpty() -> 2 * (nose.first().y - eyeY)
                else -> 0f
            }
        val unit = max(if (measured > 0f) measured else width * UNIT_OF_WIDTH, MIN_SIDE / 2)
        val pad = max(width * PAD_X, (MIN_SIDE - width).coerceAtLeast(0f) / 2)
        return clamp(Zone(across.minOf { it.x } - pad, eyeY - unit * ABOVE, across.maxOf { it.x } + pad, eyeY + unit * BELOW))
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
