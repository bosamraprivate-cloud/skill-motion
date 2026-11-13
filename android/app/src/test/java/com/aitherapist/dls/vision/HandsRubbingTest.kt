package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.catalog.Step
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic hand series for HandVerifier / handsRubbing (Story 3.2 matrix). Thresholds are test fixtures. */
class HandsRubbingTest {
    private val check = Check.HandsRubbing(maxWristRatio = 0.6, minMotionRatio = 0.05, holdSec = 2.0)
    private val step = Step("test.step.01", Method.HAND, check)
    private val verifier = HandVerifier { 0L }

    /** Shoulder width 0.2, so close < 0.12 and motion > 0.01. */
    private fun pose(): List<LandmarkPoint> {
        val points = MutableList(33) { LandmarkPoint(0.5f, 0.5f, 0.9f) }
        points[PoseLandmarkIndex.LEFT_SHOULDER] = LandmarkPoint(0.6f, 0.5f, 0.9f)
        points[PoseLandmarkIndex.RIGHT_SHOULDER] = LandmarkPoint(0.4f, 0.5f, 0.9f)
        return points
    }

    /** A hand whose wrist sits at (x, y) and whose middle MCP is [size] above it. */
    private fun hand(x: Double, y: Double, size: Float = 0.2f): List<LandmarkPoint> = List(21) { i ->
        if (i == 9) LandmarkPoint(x.toFloat(), y.toFloat() - size) else LandmarkPoint(x.toFloat(), y.toFloat())
    }

    /**
     * Frames every 50 ms to [endMs]. The second wrist circles ([radius]) around a point [base] to the right
     * of the first wrist at [hz] while [moving]; [swapOrder] alternates the list order of the hands.
     */
    private fun series(
        endMs: Long = 3000L,
        base: Double = 0.06,
        radius: Double = 0.02,
        hz: Double = 1.0,
        withPose: Boolean = true,
        handCount: Int = 2,
        handSize: Float = 0.2f,
        swapOrder: Boolean = false,
        moving: (Long) -> Boolean
    ): List<LandmarkFrame> = (0L..endMs step 50L).map { t ->
        val phase = if (moving(t)) 2 * PI * hz * t / 1000.0 else 0.0
        val first = hand(0.5, 0.6, handSize)
        val second = hand(0.5 + base + radius * cos(phase), 0.6 + radius * sin(phase), handSize)
        val pair = if (swapOrder && (t / 50L) % 2L == 1L) listOf(second, first) else listOf(first, second)
        val hands = if (handCount > 2) pair + listOf(hand(0.2, 0.2, handSize)) else pair.take(handCount)
        LandmarkFrame(t, if (withPose) pose() else null, hands)
    }

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
    fun `rubbing close hands gives done`() {
        // The hold runs from the first full 0.5 s motion window, so 2.5 s of rubbing yields a 2.0 s hold.
        assertVerdict(Outcome.DONE, VerdictReason.CHECK_PASSED, verdict(series { it <= 2500L }))
    }

    @Test
    fun `2 Hz rubbing passes although window end points coincide`() {
        assertVerdict(Outcome.DONE, VerdictReason.CHECK_PASSED, verdict(series(hz = 2.0) { true }))
    }

    @Test
    fun `hands crossing and swapping list order keep identity`() {
        // The second wrist circles around the first one (crossing its x and y) while the list order flips every frame.
        assertVerdict(Outcome.DONE, VerdictReason.CHECK_PASSED, verdict(series(base = 0.0, radius = 0.04, swapOrder = true) { true }))
    }

    @Test
    fun `still close hands give not done`() {
        assertVerdict(Outcome.NOT_DONE, VerdictReason.CHECK_FAILED, verdict(series { false }))
    }

    @Test
    fun `rubbing 1_5 s then still gives not done`() {
        assertVerdict(Outcome.NOT_DONE, VerdictReason.CHECK_FAILED, verdict(series { it <= 1500L }))
    }

    @Test
    fun `moving but far apart gives not done`() {
        assertVerdict(Outcome.NOT_DONE, VerdictReason.CHECK_FAILED, verdict(series(base = 0.3) { true }))
    }

    @Test
    fun `one hand only gives out of frame`() {
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(series(handCount = 1) { true }))
    }

    @Test
    fun `three hands are never paired`() {
        assertEquals(Outcome.CANNOT_TELL, verdict(series(handCount = 3) { true }).outcome)
    }

    @Test
    fun `no shoulders and degenerate hands give not judgeable`() {
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.NOT_JUDGEABLE, verdict(series(withPose = false, handSize = 0f) { true }))
    }

    @Test
    fun `hand size scale can pass`() {
        assertVerdict(Outcome.DONE, VerdictReason.CHECK_PASSED, verdict(series(withPose = false) { true }))
    }

    @Test
    fun `failing under realistic hand size scale is doubt not failure`() {
        // Hand size 0.07 against shoulders 0.2: wrists 0.06 apart look far by hand size, which only counts as unmeasured.
        assertEquals(Outcome.CANNOT_TELL, verdict(series(withPose = false, handSize = 0.07f) { false }).outcome)
    }

    @Test
    fun `gap in the middle of rubbing breaks the hold and is doubt`() {
        val frames = series { true }.filterNot { it.timestampMs in 1400L..1800L }
        assertEquals(Outcome.CANNOT_TELL, verdict(frames).outcome)
    }

    @Test
    fun `single far frame inside rubbing is tolerated`() {
        val frames = series { true }.map { if (it.timestampMs == 1200L) it.copy(hands = listOf(hand(0.5, 0.6), hand(0.9, 0.6))) else it }
        assertVerdict(Outcome.DONE, VerdictReason.CHECK_PASSED, verdict(frames))
    }

    @Test
    fun `hands missing for a stretch longer than hold plus window gives out of frame`() {
        val frames = series(endMs = 6000L) { false }.map { if (it.timestampMs in 1000L..3600L) it.copy(hands = emptyList()) else it }
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(frames))
    }

    @Test
    fun `window shorter than hold gives out of frame`() {
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(series(endMs = 1500L) { false }))
    }

    @Test
    fun `non-finite wrists for a long stretch give not judgeable`() {
        val nan = LandmarkPoint(Float.NaN, Float.NaN)
        val frames = series(endMs = 5000L) { false }.map {
            if (it.timestampMs in 1000L..3500L) it.copy(hands = listOf(List(21) { nan }, List(21) { nan })) else it
        }
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.NOT_JUDGEABLE, verdict(frames))
    }

    @Test
    fun `empty landmarks give out of frame`() {
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.OUT_OF_FRAME, verdict(emptyList()))
    }

    @Test
    fun `body check via HandVerifier takes the body path`() {
        val angle = Check.AngleAbove(LandmarkRef.Lm("SHOULDER"), LandmarkRef.Lm("ELBOW"), LandmarkRef.Lm("WRIST"), 10.0, 0.0, Side.LEFT)
        val frames = listOf(LandmarkFrame(0L, pose()))
        val expected = CheckEvaluator.evaluate(angle, frames, emptyMap())
        val v = verdict(frames, Step("test.step.02", Method.HAND, angle))
        assertEquals(expected, v.outcome to v.reason)
    }

    @Test
    fun `malformed content never throws`() {
        val bad = Check.AngleAbove(LandmarkRef.Lm("NOPE"), LandmarkRef.Lm("ELBOW"), LandmarkRef.Lm("WRIST"), 10.0, 0.0, Side.LEFT)
        val v = verdict(listOf(LandmarkFrame(0L, pose())), Step("test.step.03", Method.HAND, bad))
        assertVerdict(Outcome.CANNOT_TELL, VerdictReason.NOT_JUDGEABLE, v)
    }
}
