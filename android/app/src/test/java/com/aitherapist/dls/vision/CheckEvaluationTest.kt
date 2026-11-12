package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.catalog.Step
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic-landmark tests for PoseVerifier (matrix rows of Story 1.3). Thresholds are test fixtures. */
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
        val landmarks = listOf(0L, 400L, 800L).map { LandmarkFrame(it, pose(leftWrist = near)) }
        assertEquals(Outcome.NOT_DONE, verdict(landmarks).outcome)
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
        assertVerdict(Outcome.NOT_DONE, VerdictReason.CHECK_FAILED, verdict(landmarks))
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
    fun `unsupported check type gives not judgeable`() {
        val angle =
            Check.AngleAbove(LandmarkRef.Lm("LEFT_SHOULDER"), LandmarkRef.Lm("LEFT_ELBOW"), LandmarkRef.Lm("LEFT_WRIST"), 90.0, 0.3)
        val v = verdict(frames { true }, step.copy(check = angle))
        assertEquals(Outcome.CANNOT_TELL, v.outcome)
        assertEquals(VerdictReason.NOT_JUDGEABLE, v.reason)
    }

    @Test
    fun `verdict serialises lower snake`() {
        val json = kotlinx.serialization.json.Json.encodeToString(Verdict.serializer(), Verdict(Outcome.NOT_DONE, "x", latencyMs = 1))
        assertEquals("""{"outcome":"not_done","reason":"x","latencyMs":1}""", json)
    }
}
