package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.catalog.Step
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic-landmark tests for PoseVerifier and CheckEvaluator (matrix rows of Stories 1.3 and 3.1). Thresholds are test fixtures. */
class CheckEvaluationTest {
    private val drinkCheck =
        Check.DistanceBelow(
            a = LandmarkRef.Lm("WRIST"),
            b = LandmarkRef.Mid(listOf("MOUTH_LEFT", "MOUTH_RIGHT")),
            ratio = 0.6,
            holdSec = 0.3,
            side = Side.EITHER
        )
    private val step = Step("test.step.01", Method.POSE, drinkCheck)
    private val verifier = PoseVerifier { 0L }

    private val near = LandmarkPoint(0.5f, 0.38f, 0.9f) // 0.4 shoulder widths from the mouth midpoint
    private val far = LandmarkPoint(0.5f, 0.8f, 0.9f) // 2.5 shoulder widths

    private fun pose(
        leftWrist: LandmarkPoint = far,
        rightWrist: LandmarkPoint = far,
        visibility: Float = 0.9f,
        rightShoulder: LandmarkPoint = LandmarkPoint(0.4f, 0.5f, visibility)
    ): List<LandmarkPoint> {
        val points = MutableList(33) { LandmarkPoint(0.5f, 0.5f, visibility) }
        points[PoseLandmarkIndex.LEFT_SHOULDER] = LandmarkPoint(0.6f, 0.5f, visibility)
        points[PoseLandmarkIndex.RIGHT_SHOULDER] = rightShoulder
        points[PoseLandmarkIndex.MOUTH_LEFT] = LandmarkPoint(0.52f, 0.3f, visibility)
        points[PoseLandmarkIndex.MOUTH_RIGHT] = LandmarkPoint(0.48f, 0.3f, visibility)
        points[PoseLandmarkIndex.LEFT_WRIST] = leftWrist.copy(visibility = visibility)
        points[PoseLandmarkIndex.RIGHT_WRIST] = rightWrist.copy(visibility = visibility)
        return points
    }

    /** Frames every 25 ms over 1 s; [nearAt] selects the timestamps where the left wrist is near. */
    private fun frames(nearAt: (Long) -> Boolean): List<LandmarkFrame> =
        (0L..1000L step 25L).map { t -> LandmarkFrame(t, pose(leftWrist = if (nearAt(t)) near else far)) }

    private fun verdict(landmarks: List<LandmarkFrame>, s: Step = step): Verdict {
        var result: Verdict? = null
        runTest { result = verifier.verify(s, emptyList(), landmarks, VerifyContext()) }
        return result!!
    }

    private fun assertVerdict(outcome: Outcome, reason: String, v: Verdict) {
        assertEquals(outcome, v.outcome)
        assertEquals(reason, v.reason)
    }

    @Test
    fun `hold met for 0_3 s gives done`() {
        val v = verdict(frames { it in 300L..600L })
        assertEquals(Outcome.DONE, v.outcome)
        assertEquals(VerdictReason.CHECK_PASSED, v.reason)
    }

    @Test
    fun `hold of 0_2 s gives not done`() {
        assertEquals(Outcome.NOT_DONE, verdict(frames { it in 300L..500L }).outcome)
    }

    @Test
    fun `broken hold is not cumulative`() {
        assertEquals(Outcome.NOT_DONE, verdict(frames { it in 100L..300L || it in 400L..600L }).outcome)
    }

    @Test
    fun `either side passes with only the right wrist`() {
        val landmarks = (0L..1000L step 25L).map { LandmarkFrame(it, pose(rightWrist = near)) }
        assertEquals(Outcome.DONE, verdict(landmarks).outcome)
    }

    @Test
    fun `left side only ignores the right wrist`() {
        val leftOnly = step.copy(check = drinkCheck.copy(side = Side.LEFT))
        val landmarks = (0L..1000L step 25L).map { LandmarkFrame(it, pose(rightWrist = near)) }
        assertEquals(Outcome.NOT_DONE, verdict(landmarks, leftOnly).outcome)
    }

    @Test
    fun `low visibility gives cannot tell out of frame`() {
        val landmarks = (0L..1000L step 25L).map { LandmarkFrame(it, pose(leftWrist = near, visibility = 0.5f)) }
        val v = verdict(landmarks)
        assertEquals(Outcome.CANNOT_TELL, v.outcome)
        assertEquals(VerdictReason.OUT_OF_FRAME, v.reason)
    }

    @Test
    fun `unpaired frames count as not visible`() {
        val landmarks = (0L..1000L step 25L).map { t -> LandmarkFrame(t, if (t < 600) null else pose(leftWrist = near)) }
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(landmarks))
    }

    @Test
    fun `no frames gives out of frame`() {
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(emptyList()))
    }

    @Test
    fun `sparse passing frames are not a continuous hold`() {
        // Gaps above MAX_GAP_MS are unseen time, so the hold cannot be ruled out either.
        val landmarks = listOf(0L, 400L, 800L).map { LandmarkFrame(it, pose(leftWrist = near)) }
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(landmarks))
    }

    @Test
    fun `hold on a side that failed the gate does not count`() {
        // Right side visible all window; left wrist near but visible in only a few frames.
        val landmarks =
            (0L..1000L step 25L).map { t ->
                val p = pose(leftWrist = near).toMutableList()
                if (t > 300) p[PoseLandmarkIndex.LEFT_WRIST] = p[PoseLandmarkIndex.LEFT_WRIST].copy(visibility = 0.1f)
                LandmarkFrame(t, p)
            }
        // The hidden side is doubt, not failure.
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(landmarks))
    }

    @Test
    fun `unseen stretch as long as the hold gives cannot tell`() {
        val landmarks = (0L..1000L step 25L).map { t -> LandmarkFrame(t, if (t in 400L..750L) null else pose()) }
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(landmarks))
    }

    @Test
    fun `short unseen stretch still gives not done`() {
        val landmarks = (0L..1000L step 25L).map { t -> LandmarkFrame(t, if (t in 400L..500L) null else pose()) }
        assertVerdict(Outcome.NOT_DONE, VerdictReason.CHECK_FAILED, verdict(landmarks))
    }

    @Test
    fun `zero shoulder width frame does not pass`() {
        val coincident = LandmarkPoint(0.6f, 0.5f, 0.9f)
        // Every fourth frame has coincident shoulders, which breaks every near-wrist run below the hold.
        val landmarks =
            (0L..1000L step 25L).map { t ->
                LandmarkFrame(t, if (t % 100L == 0L) pose(leftWrist = near, rightShoulder = coincident) else pose(leftWrist = near))
            }
        assertVerdict(Outcome.NOT_DONE, VerdictReason.CHECK_FAILED, verdict(landmarks))
    }

    @Test
    fun `no measurable shoulder width gives not judgeable`() {
        val coincident = LandmarkPoint(0.6f, 0.5f, 0.9f)
        val landmarks = (0L..1000L step 25L).map { LandmarkFrame(it, pose(leftWrist = near, rightShoulder = coincident)) }
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.NOT_JUDGEABLE, verdict(landmarks))
    }

    @Test
    fun `evaluator exception gives cannot tell not judgeable`() {
        val bad = step.copy(check = drinkCheck.copy(a = LandmarkRef.Lm("NOT_A_LANDMARK"), side = Side.LEFT))
        val v = verdict(frames { true }, bad)
        assertEquals(Outcome.CANNOT_TELL, v.outcome)
        assertEquals(VerdictReason.NOT_JUDGEABLE, v.reason)
    }

    @Test
    fun `truncated pose list gives cannot tell not judgeable`() {
        val landmarks = (0L..1000L step 25L).map { LandmarkFrame(it, pose(leftWrist = near).take(5)) }
        // Missing indices read as not visible, so the gate stops before evaluation.
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(landmarks))
    }

    @Test
    fun `cloud and missing checks give not judgeable`() {
        val question = step.copy(check = Check.Question("q"))
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.NOT_JUDGEABLE, verdict(frames { true }, question))
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.NOT_JUDGEABLE, verdict(frames { true }, step.copy(check = null)))
    }

    // ---- All body check types (Story 3.1). Thresholds are test fixtures, not clinical values. ----

    private val shoulder = LandmarkRef.Lm("SHOULDER")
    private val elbow = LandmarkRef.Lm("ELBOW")
    private val wrist = LandmarkRef.Lm("WRIST")
    private val mouth = LandmarkRef.Mid(listOf("MOUTH_LEFT", "MOUTH_RIGHT"))
    private val zones = mapOf("mat" to Zone(0.3f, 0.1f, 0.7f, 0.25f))

    /**
     * Shoulders [width] apart at y 0.5, elbows straight above them at y 0.35. A straight arm has the wrist
     * at y 0.2 (180 deg at the elbow, inside the mat zone); a bent arm has it out sideways at y 0.35 (90 deg).
     */
    private fun arm(leftStraight: Boolean, rightStraight: Boolean, visibility: Float, width: Float): List<LandmarkPoint> {
        val points = MutableList(33) { LandmarkPoint(0.5f, 0.5f, visibility) }
        points[PoseLandmarkIndex.LEFT_SHOULDER] = LandmarkPoint(0.5f + width / 2, 0.5f, visibility)
        points[PoseLandmarkIndex.RIGHT_SHOULDER] = LandmarkPoint(0.5f - width / 2, 0.5f, visibility)
        points[PoseLandmarkIndex.MOUTH_LEFT] = LandmarkPoint(0.52f, 0.3f, visibility)
        points[PoseLandmarkIndex.MOUTH_RIGHT] = LandmarkPoint(0.48f, 0.3f, visibility)
        points[PoseLandmarkIndex.LEFT_ELBOW] = LandmarkPoint(0.6f, 0.35f, visibility)
        points[PoseLandmarkIndex.RIGHT_ELBOW] = LandmarkPoint(0.4f, 0.35f, visibility)
        points[PoseLandmarkIndex.LEFT_WRIST] =
            if (leftStraight) LandmarkPoint(0.6f, 0.2f, visibility) else LandmarkPoint(0.75f, 0.35f, visibility)
        points[PoseLandmarkIndex.RIGHT_WRIST] =
            if (rightStraight) LandmarkPoint(0.4f, 0.2f, visibility) else LandmarkPoint(0.25f, 0.35f, visibility)
        return points
    }

    /** [idleStraight] sets the pose of the arm not driven by [straightAt]. */
    private fun armFrames(
        straightAt: (Long) -> Boolean,
        right: Boolean = false,
        visibility: Float = 0.9f,
        width: Float = 0.2f,
        idleStraight: Boolean = false
    ) = (0L..1000L step 25L).map { t ->
        val on = straightAt(t)
        val left = if (right) idleStraight else on
        val rightArm = if (right) on else idleStraight
        LandmarkFrame(t, arm(leftStraight = left, rightStraight = rightArm, visibility = visibility, width = width))
    }

    /** Every check passes on a straight arm and fails on a bent one, except `angleBelow` (the reverse). */
    private fun bodyChecks(side: Side?): Map<String, Check> = mapOf(
        "angleAbove" to Check.AngleAbove(shoulder, elbow, wrist, 150.0, 0.3, side), // 180 vs 90
        "angleBelow" to Check.AngleBelow(shoulder, elbow, wrist, 150.0, 0.3, side), // 90 passes
        "pointAbove" to Check.PointAbove(wrist, shoulder, 1.0, 0.3, side), // 1.5 vs 0.75 widths
        "distanceBelow" to Check.DistanceBelow(wrist, mouth, 0.8, 0.3, side), // 0.71 vs 1.27 widths
        "distanceAbove" to Check.DistanceAbove(wrist, shoulder, 1.4, 0.3, side), // 1.5 vs 1.06 widths
        "inZone" to Check.InZone(wrist, "mat", 0.3, side)
    )

    /** Pose timeline where [type]'s condition is met exactly when [met] is true. */
    private fun metAt(type: String, met: (Long) -> Boolean): (Long) -> Boolean = if (type == "angleBelow") { t -> !met(t) } else met

    private fun bodyVerdict(check: Check, landmarks: List<LandmarkFrame>, z: Map<String, Zone> = zones): Verdict {
        var result: Verdict? = null
        runTest { result = verifier.verify(step.copy(check = check), emptyList(), landmarks, VerifyContext(z)) }
        return result!!
    }

    @Test
    fun `each type passes when held`() {
        bodyChecks(Side.EITHER).forEach { (type, check) ->
            val v = bodyVerdict(check, armFrames(metAt(type) { it in 300L..650L }, idleStraight = type == "angleBelow"))
            assertEquals(type, Outcome.DONE to VerdictReason.CHECK_PASSED, v.outcome to v.reason)
        }
    }

    @Test
    fun `each type fails when never met`() {
        bodyChecks(Side.EITHER).forEach { (type, check) ->
            val v = bodyVerdict(check, armFrames(metAt(type) { false }, idleStraight = type == "angleBelow"))
            assertEquals(type, Outcome.NOT_DONE to VerdictReason.CHECK_FAILED, v.outcome to v.reason)
        }
    }

    @Test
    fun `each type with a broken hold gives not done`() {
        bodyChecks(Side.EITHER).forEach { (type, check) ->
            val v = bodyVerdict(check, armFrames(metAt(type) { it in 100L..300L || it in 400L..600L }, idleStraight = type == "angleBelow"))
            assertEquals(type, Outcome.NOT_DONE, v.outcome)
        }
    }

    @Test
    fun `each type passes on either side with only the right arm`() {
        bodyChecks(Side.EITHER).filterKeys { it != "angleBelow" }.forEach { (type, check) ->
            assertEquals(type, Outcome.DONE, bodyVerdict(check, armFrames({ true }, right = true)).outcome)
        }
        // angleBelow: only the right arm bent (left straight).
        val leftStraight = (0L..1000L step 25L).map { LandmarkFrame(it, arm(true, false, 0.9f, 0.2f)) }
        assertEquals(Outcome.DONE, bodyVerdict(bodyChecks(Side.EITHER).getValue("angleBelow"), leftStraight).outcome)
    }

    @Test
    fun `left side check ignores the right arm`() {
        bodyChecks(Side.LEFT).filterKeys { it != "angleBelow" }.forEach { (type, check) ->
            assertEquals(type, Outcome.NOT_DONE, bodyVerdict(check, armFrames({ true }, right = true)).outcome)
        }
        val rightBentOnly = (0L..1000L step 25L).map { LandmarkFrame(it, arm(true, false, 0.9f, 0.2f)) }
        assertEquals(Outcome.NOT_DONE, bodyVerdict(bodyChecks(Side.LEFT).getValue("angleBelow"), rightBentOnly).outcome)
    }

    @Test
    fun `sided names work without side`() {
        val check = Check.PointAbove(LandmarkRef.Lm("LEFT_WRIST"), LandmarkRef.Lm("LEFT_SHOULDER"), 1.0, 0.3)
        assertEquals(Outcome.DONE, bodyVerdict(check, armFrames({ true })).outcome)
    }

    @Test
    fun `each type out of frame gives cannot tell`() {
        bodyChecks(Side.EITHER).forEach { (type, check) ->
            val v = bodyVerdict(check, armFrames({ true }, visibility = 0.2f))
            assertEquals(type, Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME, v.outcome to v.reason)
        }
    }

    @Test
    fun `width based types with no shoulder width give not judgeable`() {
        bodyChecks(Side.EITHER).filterKeys { it in setOf("pointAbove", "distanceBelow", "distanceAbove") }.forEach { (type, check) ->
            val v = bodyVerdict(check, armFrames({ true }, width = 0f))
            assertEquals(type, Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE, v.outcome to v.reason)
        }
    }

    @Test
    fun `zero length angle arm gives not judgeable`() {
        val check = Check.AngleAbove(wrist, wrist, shoulder, 90.0, 0.3, Side.LEFT)
        val v = bodyVerdict(check, armFrames({ true }))
        assertEquals(Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE, v.outcome to v.reason)
    }

    @Test
    fun `missing zone gives no zone`() {
        val v = bodyVerdict(Check.InZone(wrist, "sink", 0.3, Side.EITHER), armFrames({ true }))
        assertEquals(Outcome.CANNOT_TELL to VerdictReason.NO_ZONE, v.outcome to v.reason)
        val none = bodyVerdict(Check.InZone(wrist, "mat", 0.3, Side.EITHER), armFrames({ true }), emptyMap())
        assertEquals(VerdictReason.NO_ZONE, none.reason)
    }

    @Test
    fun `evaluator can be called directly`() {
        val result = CheckEvaluator.evaluate(bodyChecks(Side.EITHER).getValue("inZone"), armFrames({ true }), zones)
        assertEquals(Outcome.DONE to VerdictReason.CHECK_PASSED, result)
    }

    // ---- Review patches: doubt per side, non-finite values, gaps, zones, zero hold. ----

    private val inZoneLeft = Check.InZone(wrist, "mat", 0.3, Side.LEFT)

    private fun leftStraightFrames(times: List<Long>, edit: (Long, MutableList<LandmarkPoint>) -> Unit = { _, _ -> }) =
        times.map { t -> LandmarkFrame(t, arm(true, false, 0.9f, 0.2f).toMutableList().also { edit(t, it) }) }

    private val window = (0L..1000L step 25L).toList()

    @Test
    fun `hold of exactly holdSec passes and just under fails`() {
        val check = bodyChecks(Side.LEFT).getValue("pointAbove")
        assertEquals(Outcome.DONE, bodyVerdict(check, armFrames({ it in 300L..600L })).outcome)
        val v = bodyVerdict(check, armFrames({ it in 300L..575L }))
        assertEquals(Outcome.NOT_DONE to VerdictReason.CHECK_FAILED, v.outcome to v.reason)
    }

    @Test
    fun `broken hold reason is check failed`() {
        val v = bodyVerdict(bodyChecks(Side.LEFT).getValue("pointAbove"), armFrames({ it in 100L..300L || it in 400L..600L }))
        assertEquals(Outcome.NOT_DONE to VerdictReason.CHECK_FAILED, v.outcome to v.reason)
    }

    @Test
    fun `either with one side hidden for the whole hold gives cannot tell`() {
        // Right wrist hidden 300..700 (gate still passes on both sides); left arm bent and fully visible.
        val landmarks =
            window.map { t ->
                val p = arm(false, false, 0.9f, 0.2f).toMutableList()
                if (t in 300L..700L) p[PoseLandmarkIndex.RIGHT_WRIST] = p[PoseLandmarkIndex.RIGHT_WRIST].copy(visibility = 0.1f)
                LandmarkFrame(t, p)
            }
        val v = bodyVerdict(bodyChecks(Side.EITHER).getValue("pointAbove"), landmarks)
        assertEquals(Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME, v.outcome to v.reason)
    }

    @Test
    fun `unseen stretch while more than half visible gives out of frame`() {
        val landmarks = window.map { t -> LandmarkFrame(t, if (t in 200L..550L) null else arm(false, false, 0.9f, 0.2f)) }
        val v = bodyVerdict(inZoneLeft, landmarks)
        assertEquals(Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME, v.outcome to v.reason)
    }

    @Test
    fun `unmeasurable stretch as long as the hold gives not judgeable`() {
        val check = bodyChecks(Side.LEFT).getValue("pointAbove")
        val landmarks =
            window.map { t ->
                LandmarkFrame(t, arm(false, false, 0.9f, if (t in 200L..550L) 0f else 0.2f))
            }
        val v = bodyVerdict(check, landmarks)
        assertEquals(Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE, v.outcome to v.reason)
    }

    @Test
    fun `non finite values are unmeasurable not failed`() {
        val nan = LandmarkPoint(Float.NaN, Float.NaN, 0.9f)
        bodyChecks(Side.LEFT).filterKeys { it != "angleBelow" }.forEach { (type, check) ->
            val landmarks = leftStraightFrames(window) { _, p -> p[PoseLandmarkIndex.LEFT_WRIST] = nan }
            val v = bodyVerdict(check, landmarks)
            assertEquals(type, Outcome.CANNOT_TELL to VerdictReason.NOT_JUDGEABLE, v.outcome to v.reason)
        }
    }

    @Test
    fun `missing frame gap counts as unseen`() {
        val times = window.filter { it !in 300L..650L }
        val v = bodyVerdict(inZoneLeft, armFrames({ false }).filter { it.timestampMs in times })
        assertEquals(Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME, v.outcome to v.reason)
    }

    @Test
    fun `window shorter than the hold gives cannot tell`() {
        val v = bodyVerdict(inZoneLeft, armFrames({ false }).filter { it.timestampMs <= 200L })
        assertEquals(Outcome.CANNOT_TELL, v.outcome)
    }

    @Test
    fun `empty series and single frame give cannot tell`() {
        assertEquals(
            Outcome.CANNOT_TELL to VerdictReason.OUT_OF_FRAME,
            bodyVerdict(inZoneLeft, emptyList()).let {
                it.outcome to it.reason
            }
        )
        assertEquals(Outcome.CANNOT_TELL, bodyVerdict(inZoneLeft, armFrames({ false }).take(1)).outcome)
    }

    @Test
    fun `zones are normalised and zero area is no zone`() {
        val flipped = mapOf("mat" to Zone(0.7f, 0.25f, 0.3f, 0.1f))
        assertEquals(Outcome.DONE, bodyVerdict(inZoneLeft, armFrames({ true }), flipped).outcome)
        val flat = mapOf("mat" to Zone(0.3f, 0.2f, 0.7f, 0.2f))
        assertEquals(VerdictReason.NO_ZONE, bodyVerdict(inZoneLeft, armFrames({ true }), flat).reason)
        val nan = mapOf("mat" to Zone(Float.NaN, 0.1f, 0.7f, 0.25f))
        assertEquals(VerdictReason.NO_ZONE, bodyVerdict(inZoneLeft, armFrames({ true }), nan).reason)
    }

    @Test
    fun `zero or non finite hold means one frame`() {
        listOf(0.0, -1.0, Double.NaN).forEach { hold ->
            val check = inZoneLeft.copy(holdSec = hold)
            assertEquals("$hold", Outcome.DONE, bodyVerdict(check, armFrames({ it == 500L })).outcome)
            // A single unseen frame does not make it out of frame.
            val oneHidden = armFrames({ false }).map { if (it.timestampMs == 500L) LandmarkFrame(500L, null) else it }
            val v = bodyVerdict(check, oneHidden)
            assertEquals("$hold", Outcome.NOT_DONE to VerdictReason.CHECK_FAILED, v.outcome to v.reason)
        }
        val unmeasurable = Check.AngleAbove(wrist, wrist, shoulder, 90.0, 0.0, Side.LEFT)
        assertEquals(VerdictReason.NOT_JUDGEABLE, bodyVerdict(unmeasurable, armFrames({ true })).reason)
    }

    @Test
    fun `verdict serialises lower snake`() {
        val json = kotlinx.serialization.json.Json.encodeToString(Verdict.serializer(), Verdict(Outcome.NOT_DONE, "x", latencyMs = 1))
        assertEquals("""{"outcome":"not_done","reason":"x","latencyMs":1}""", json)
    }
}
