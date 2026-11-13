package com.aitherapist.dls.vision

import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.vision.PoseLandmarkIndex as P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingGateTest {
    private val shoulders = setOf(P.LEFT_SHOULDER, P.RIGHT_SHOULDER)
    private val wrists = setOf(P.LEFT_WRIST, P.RIGHT_WRIST)
    private val hips = setOf(P.LEFT_HIP, P.RIGHT_HIP)
    private val all = (0 until 33).toSet()

    // Each point sits on a corner of a [spread]-sized box (corner = index % 4), so any two diagonal points span it.
    private fun pose(visible: Set<Int>, cx: Float = 0.5f, spread: Float = 0.3f) = List(33) { i ->
        val dx = if (i % 2 == 0) -spread / 2 else spread / 2
        val dy = if (i % 4 < 2) -spread / 2 else spread / 2
        LandmarkPoint(cx + dx, 0.5f + dy, if (i in visible) 0.9f else 0.1f)
    }

    @Test
    fun `content-only task needs nothing`() {
        assertTrue(VisibilityGate.neededLandmarks(listOf(StepDetector(Method.NONE, "table"))).isEmpty)
    }

    @Test
    fun `cloud steps use the framing default`() {
        val q = Check.Question("q")
        assertEquals(
            NeededLandmarks(shoulders + wrists + P.NOSE, 0),
            VisibilityGate.neededLandmarks(listOf(StepDetector(Method.CLOUD, "table", q)))
        )
        assertEquals(
            NeededLandmarks(shoulders + hips + wrists, 0),
            VisibilityGate.neededLandmarks(listOf(StepDetector(Method.CLOUD, "standing", q)))
        )
        assertEquals(NeededLandmarks(shoulders + wrists, 2), VisibilityGate.neededLandmarks(listOf(StepDetector(Method.CLOUD, "sink", q))))
        assertEquals(Framing.STANDING, Framing.of(null))
        assertEquals(Framing.STANDING, Framing.of("bogus"))
    }

    @Test
    fun `local checks contribute their landmarks, both sides for either`() {
        val dist = Check.DistanceBelow(LandmarkRef.Lm("WRIST"), LandmarkRef.Mid(listOf("MOUTH_LEFT", "MOUTH_RIGHT")), 0.6, 0.3, Side.EITHER)
        val zone = Check.InZone(LandmarkRef.Mid(listOf("LEFT_HIP", "RIGHT_HIP")), "sink", 1.0)
        val needed =
            VisibilityGate.neededLandmarks(
                listOf(StepDetector(Method.NONE), StepDetector(Method.POSE, "table", dist), StepDetector(Method.POSE, "table", zone))
            )
        assertEquals(shoulders + wrists + setOf(P.MOUTH_LEFT, P.MOUTH_RIGHT) + hips, needed.pose)
        assertEquals(0, needed.hands)
    }

    @Test
    fun `other check types and hand methods`() {
        val angle = Check.AngleAbove(LandmarkRef.Lm("SHOULDER"), LandmarkRef.Lm("ELBOW"), LandmarkRef.Lm("WRIST"), 90.0, 0.0, Side.LEFT)
        val below = Check.AngleBelow(LandmarkRef.Lm("SHOULDER"), LandmarkRef.Lm("ELBOW"), LandmarkRef.Lm("WRIST"), 90.0, 0.0, Side.RIGHT)
        val above = Check.PointAbove(LandmarkRef.Lm("LEFT_WRIST"), LandmarkRef.Lm("NOSE"), 0.1, 0.0)
        val far = Check.DistanceAbove(LandmarkRef.Lm("LEFT_WRIST"), LandmarkRef.Lm("NOSE"), 1.0, 0.0)
        val n1 =
            VisibilityGate.neededLandmarks(
                listOf(
                    StepDetector(Method.POSE, check = angle),
                    StepDetector(Method.POSE, check = below),
                    StepDetector(Method.HAND, check = far)
                )
            )
        assertEquals(shoulders + setOf(P.LEFT_ELBOW, P.RIGHT_ELBOW, P.NOSE) + wrists, n1.pose)
        assertEquals(1, n1.hands)
        val n2 =
            VisibilityGate.neededLandmarks(
                listOf(StepDetector(Method.POSE, check = above), StepDetector(Method.HAND, check = Check.HandsRubbing(0.6, 0.05, 2.0)))
            )
        assertEquals(shoulders + wrists + P.NOSE, n2.pose)
        assertEquals(2, n2.hands)
        assertEquals(emptySet<Int>(), VisibilityGate.neededLandmarks(listOf(StepDetector(Method.POSE))).pose)
    }

    @Test
    fun `framed needs pose landmarks and hands`() {
        val needed = NeededLandmarks(shoulders + wrists, 2)
        val hand = listOf(LandmarkPoint(0.5f, 0.5f))
        assertTrue(VisibilityGate.framed(LandmarkFrame(0, pose(all), listOf(hand, hand)), needed))
        assertFalse(VisibilityGate.framed(LandmarkFrame(0, pose(all), listOf(hand)), needed))
        assertFalse(VisibilityGate.framed(LandmarkFrame(0, pose(all - P.LEFT_WRIST), listOf(hand, hand)), needed))
        assertFalse(VisibilityGate.framed(LandmarkFrame(0, null), needed))
    }

    @Test
    fun `guidance is geometric`() {
        val needed = NeededLandmarks(shoulders + wrists, 0)
        fun g(frame: LandmarkFrame, n: NeededLandmarks = needed) = VisibilityGate.guidance(frame, n)
        assertEquals(FramingGuidance.NO_PERSON, g(LandmarkFrame(0, null)))
        assertEquals(FramingGuidance.NO_PERSON, g(LandmarkFrame(0, pose(emptySet()))))
        assertEquals(FramingGuidance.OK, g(LandmarkFrame(0, pose(all))))
        assertEquals(FramingGuidance.STEP_BACK, g(LandmarkFrame(0, pose(all, spread = 0.99f))))
        assertEquals(FramingGuidance.STEP_BACK, g(LandmarkFrame(0, pose(all, spread = 0.93f))))
        assertEquals(FramingGuidance.MOVE_CLOSER, g(LandmarkFrame(0, pose(all, spread = 0.1f))))
        // Un-mirrored x 0.8 shows at 0.2 in the mirrored preview (left of centre): move right.
        assertEquals(FramingGuidance.MOVE_RIGHT, g(LandmarkFrame(0, pose(all, cx = 0.8f))))
        assertEquals(FramingGuidance.MOVE_LEFT, g(LandmarkFrame(0, pose(all, cx = 0.2f))))
        assertEquals(FramingGuidance.SHOW_HANDS, g(LandmarkFrame(0, pose(all - wrists))))
        assertEquals(FramingGuidance.STEP_BACK, g(LandmarkFrame(0, pose(all - P.LEFT_SHOULDER))))
        assertEquals(FramingGuidance.SHOW_HANDS, g(LandmarkFrame(0, pose(all)), NeededLandmarks(shoulders, 1)))
    }

    @Test
    fun `hold needs one continuous second`() {
        val hold = VisibilityGate.Hold()
        assertFalse(hold.update(true, 0))
        assertFalse(hold.update(true, 250))
        assertFalse("flicker resets", hold.update(false, 500))
        for (t in 700L..1_500L step 200) assertFalse(hold.update(true, t))
        assertTrue(hold.update(true, 1_700))
    }

    @Test
    fun `hold resets after a gap longer than 300 ms`() {
        val hold = VisibilityGate.Hold()
        for (t in 0L..900L step 300) assertFalse(hold.update(true, t))
        assertFalse("gap of 400 ms restarts", hold.update(true, 1_300))
        for (t in 1_500L..2_100L step 200) assertFalse(hold.update(true, t))
        assertTrue(hold.update(true, 2_300))
    }

    @Test
    fun `table framing ignores predicted points below the frame`() {
        val needed = VisibilityGate.framingDefault(Framing.TABLE)
        val base = pose(all, spread = 0.4f).toMutableList()
        // Hips are predicted far below the frame with high visibility; they are not needed and must not widen the box.
        base[P.LEFT_HIP] = LandmarkPoint(0.4f, 1.6f, 0.95f)
        base[P.RIGHT_HIP] = LandmarkPoint(0.6f, 1.6f, 0.95f)
        assertEquals(FramingGuidance.OK, VisibilityGate.guidance(LandmarkFrame(0, base), needed))
        // A needed wrist predicted outside the frame counts as missing, not as the box edge.
        base[P.LEFT_WRIST] = LandmarkPoint(0.5f, 1.2f, 0.95f)
        assertEquals(FramingGuidance.SHOW_HANDS, VisibilityGate.guidance(LandmarkFrame(0, base), needed))
    }
}
