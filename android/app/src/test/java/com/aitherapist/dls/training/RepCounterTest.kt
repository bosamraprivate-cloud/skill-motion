package com.aitherapist.dls.training

import com.aitherapist.dls.catalog.ActivitiesFile
import com.aitherapist.dls.catalog.CatalogJson
import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.Criterion
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.PoseLandmarkIndex as I
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RepCounterTest {
    private val lm = { n: String -> LandmarkRef.Lm(n) }
    private val above = Check.AngleAbove(lm("ELBOW"), lm("SHOULDER"), lm("HIP"), 120.0, 0.0, Side.EITHER)
    private val below = Check.AngleBelow(lm("ELBOW"), lm("SHOULDER"), lm("HIP"), 60.0, 0.0, Side.EITHER)

    private var ts = 0L

    /** Pose with the ELBOW-SHOULDER-HIP angle [left]/[right] degrees (null = side invisible); wrists at [wristGap]. */
    private fun pose(left: Double?, right: Double? = left, wristGap: Float = 0.3f): List<LandmarkPoint> {
        val p = MutableList(33) { LandmarkPoint(0.5f, 0.5f, 0f) }
        fun arm(angle: Double?, sh: Int, el: Int, hip: Int, x: Float) {
            val v = if (angle == null) 0f else 1f
            val a = Math.toRadians(angle ?: 0.0)
            p[sh] = LandmarkPoint(x, 0.4f, 1f)
            p[hip] = LandmarkPoint(x, 0.7f, v)
            p[el] = LandmarkPoint(x + (0.2 * sin(a)).toFloat(), 0.4f + (0.2 * cos(a)).toFloat(), v)
        }
        arm(left, I.LEFT_SHOULDER, I.LEFT_ELBOW, I.LEFT_HIP, 0.6f)
        arm(right, I.RIGHT_SHOULDER, I.RIGHT_ELBOW, I.RIGHT_HIP, 0.4f)
        p[I.LEFT_WRIST] = LandmarkPoint(0.5f + wristGap / 2, 0.5f, 1f)
        p[I.RIGHT_WRIST] = LandmarkPoint(0.5f - wristGap / 2, 0.5f, 1f)
        return p
    }

    private fun RepCounter.run(frames: Int, pose: List<LandmarkPoint>?, stepMs: Long = 100) = repeat(frames) {
        feed(LandmarkFrame(ts, pose))
        ts += stepMs
    }

    private fun reps(check: Check, target: Int = 10, perSide: Boolean = false) =
        RepCounter(Criterion.Reps(check, target, perSide, 60.0, 120.0))

    private fun RepCounter.rep(up: Double, down: Double, upFrames: Int = 5) {
        run(upFrames, pose(up))
        run(5, pose(down))
    }

    @Test
    fun `ten reps count ten and target reached keeps counting`() {
        val c = reps(above)
        c.run(3, pose(30.0))
        repeat(10) { c.rep(150.0, 30.0) }
        assertEquals(10, c.count)
        assertTrue(c.targetReached)
        c.rep(150.0, 30.0)
        assertEquals(11, c.count)
    }

    @Test
    fun `short blip is ignored`() {
        val c = reps(above)
        c.rep(150.0, 30.0, upFrames = 2)
        assertEquals(0, c.count)
    }

    @Test
    fun `noise between thresholds never counts`() {
        val c = reps(above)
        repeat(20) {
            c.run(3, pose(70.0))
            c.run(3, pose(110.0))
        }
        assertEquals(0, c.count)
        assertFalse(c.targetReached)
    }

    @Test
    fun `angleBelow counts below low then above high`() {
        val c = reps(below)
        repeat(3) { c.rep(30.0, 150.0) }
        assertEquals(3, c.count)
        val wrongWay = reps(below)
        wrongWay.run(5, pose(150.0))
        assertEquals(0, wrongWay.count)
    }

    @Test
    fun `per side counts left and right separately`() {
        val c = reps(above, perSide = true)
        repeat(10) { i ->
            val r = if (i < 7) 150.0 else 30.0
            c.run(5, pose(150.0, r))
            c.run(5, pose(30.0, 30.0))
        }
        assertEquals(10, c.counts[Side.LEFT])
        assertEquals(7, c.counts[Side.RIGHT])
        assertFalse(c.targetReached)
    }

    @Test
    fun `hold resets on break and reaches target`() {
        val c = RepCounter(Criterion.Hold(above, 5.0, false, 60.0, 120.0))
        c.run(41, pose(150.0))
        assertEquals(4.0, c.bestHoldSec[Side.LEFT]!!, 1e-9)
        assertFalse(c.targetReached)
        c.run(1, pose(90.0))
        assertEquals(0.0, c.holdSec[Side.LEFT]!!, 1e-9)
        c.run(61, pose(150.0))
        assertEquals(6.0, c.holdSec[Side.LEFT]!!, 1e-9)
        assertEquals(6.0, c.bestHoldSec[Side.LEFT]!!, 1e-9)
        assertTrue(c.targetReached)
    }

    @Test
    fun `invisible frames are ignored but a long gap resets the phase`() {
        val c = reps(above)
        c.run(5, pose(150.0))
        ts -= 50
        c.run(1, pose(null), stepMs = 50)
        c.run(1, null, stepMs = 50)
        c.run(1, emptyList(), stepMs = 50)
        c.run(5, pose(30.0))
        assertEquals(1, c.count)
        c.run(5, pose(150.0))
        c.run(10, pose(null))
        c.run(5, pose(30.0))
        assertEquals(1, c.count)
    }

    @Test
    fun `distanceBelow and pointAbove measure in shoulder widths`() {
        val counter = RepCounter(Criterion.Reps(Check.DistanceBelow(lm("LEFT_WRIST"), lm("RIGHT_WRIST"), 0.5, 0.0), 1, false, 0.5, 1.5))
        counter.run(5, pose(0.0, wristGap = 0.05f))
        counter.run(5, pose(0.0, wristGap = 0.4f))
        assertEquals(1, counter.count)
        assertTrue(counter.targetReached)
        val p = RepCounter(Criterion.Reps(Check.PointAbove(lm("ELBOW"), lm("SHOULDER"), 0.5, 0.0, Side.LEFT), 1, false, 0.1, 0.5))
        p.run(5, pose(180.0))
        p.run(5, pose(0.0))
        assertEquals(1, p.count)
    }

    @Test
    fun `decodes catalogue counter and rejects bad criteria`() {
        val text =
            """{"kind":"reps","check":{"type":"angleAbove","a":{"lm":"ELBOW"},"vertex":{"lm":"SHOULDER"},"b":{"lm":"HIP"},""" +
                """"deg":120,"holdSec":0,"side":"either"},"target":10,"perSide":true,"low":60,"high":120}"""
        val c = RepCounter(CatalogJson.decodeCriterion(text))
        assertEquals(setOf(Side.LEFT, Side.RIGHT), c.counts.keys)
        assertThrows(IllegalArgumentException::class.java) { RepCounter(Criterion.Reps(above, 1, false, null, 120.0)) }
        assertThrows(IllegalArgumentException::class.java) { RepCounter(Criterion.Reps(above, 1, false, 60.0, null)) }
        assertThrows(IllegalArgumentException::class.java) { RepCounter(Criterion.Reps(above, 1, false, 120.0, 60.0)) }
        assertThrows(IllegalArgumentException::class.java) { RepCounter(Criterion.Reps(Check.Question("q"), 1, false, 1.0, 2.0)) }
    }

    @Test
    fun `middle band blip is dropped and a brisk rep counts`() {
        val c = reps(above)
        c.run(1, pose(150.0))
        c.run(5, pose(90.0))
        c.run(5, pose(30.0))
        assertEquals(0, c.count)
        c.run(6, pose(150.0), stepMs = 50)
        c.run(3, pose(90.0), stepMs = 50)
        c.run(1, pose(30.0), stepMs = 50)
        assertEquals(1, c.count)
    }

    @Test
    fun `frames not newer than the last are ignored`() {
        val c = reps(above)
        c.run(5, pose(150.0))
        ts -= 100
        c.run(1, pose(30.0))
        assertEquals(0, c.count)
    }

    @Test
    fun `either side tracks each arm independently and reports the best`() {
        val c = reps(above)
        repeat(3) {
            c.run(5, pose(150.0, 150.0))
            c.run(5, pose(150.0, 30.0))
        }
        assertEquals(3, c.count)
        val alt = reps(above)
        repeat(3) {
            alt.run(5, pose(150.0, 30.0))
            alt.run(5, pose(30.0, 30.0))
            alt.run(5, pose(30.0, 150.0))
            alt.run(5, pose(30.0, 30.0))
        }
        assertEquals(3, alt.count)
        assertEquals(3, alt.counts[Side.LEFT])
        assertEquals(3, alt.counts[Side.RIGHT])
    }

    @Test
    fun `check threshold must equal the start threshold`() {
        assertThrows(IllegalArgumentException::class.java) { RepCounter(Criterion.Reps(above, 1, false, 60.0, 130.0)) }
        assertThrows(IllegalArgumentException::class.java) { RepCounter(Criterion.Reps(below, 1, false, 50.0, 120.0)) }
    }

    @Test
    fun `NaN coordinates and zero shoulder width are ignored`() {
        val c = reps(above)
        c.run(5, pose(150.0))
        c.run(1, pose(150.0).toMutableList().also { it[I.LEFT_ELBOW] = LandmarkPoint(Float.NaN, Float.NaN, 1f) }, stepMs = 50)
        c.run(
            1,
            pose(150.0).toMutableList().also {
                it[I.LEFT_ELBOW] = it[I.LEFT_SHOULDER]
                it[I.RIGHT_ELBOW] = it[I.RIGHT_SHOULDER]
            },
            stepMs = 50
        )
        c.run(5, pose(30.0))
        assertEquals(1, c.count)
        val d = RepCounter(Criterion.Reps(Check.DistanceBelow(lm("LEFT_WRIST"), lm("RIGHT_WRIST"), 0.5, 0.0), 1, false, 0.5, 1.5))
        val zeroWidth = pose(0.0, wristGap = 0.4f).toMutableList().also { it[I.RIGHT_SHOULDER] = it[I.LEFT_SHOULDER] }
        d.run(5, pose(0.0, wristGap = 0.05f))
        d.run(1, zeroWidth, stepMs = 50)
        assertEquals(0, d.count)
        d.run(5, pose(0.0, wristGap = 0.4f))
        assertEquals(1, d.count)
    }

    @Test
    fun `hold gap over MAX_GAP_MS resets the current hold`() {
        val c = RepCounter(Criterion.Hold(above, 5.0, false, 60.0, 120.0))
        c.run(21, pose(150.0))
        assertEquals(2.0, c.holdSec[Side.LEFT]!!, 1e-9)
        ts += 200
        c.run(1, pose(150.0))
        assertEquals(0.0, c.holdSec[Side.LEFT]!!, 1e-9)
        assertEquals(2.0, c.bestHoldSec[Side.LEFT]!!, 1e-9)
    }

    @Test
    fun `per side hold needs both sides`() {
        val c = RepCounter(Criterion.Hold(above, 5.0, true, 60.0, 120.0))
        c.run(30, pose(150.0, 150.0))
        c.run(31, pose(150.0, 30.0))
        assertEquals(6.0, c.bestHoldSec[Side.LEFT]!!, 1e-9)
        assertEquals(2.9, c.bestHoldSec[Side.RIGHT]!!, 1e-9)
        assertFalse(c.targetReached)
    }

    private fun catalogCounter(id: String): Criterion {
        val text = File("src/main/assets/catalog/activities.json").readText()
        val activities = CatalogJson.json.decodeFromString(ActivitiesFile.serializer(), text).activities
        return requireNotNull(activities.single { it.id == id }.counter)
    }

    /** Pose with the SHOULDER-ELBOW-WRIST angle per side. */
    private fun elbowPose(left: Double, right: Double): List<LandmarkPoint> {
        val p = MutableList(33) { LandmarkPoint(0.5f, 0.5f, 0f) }
        fun arm(angle: Double, sh: Int, el: Int, wr: Int, x: Float) {
            val a = Math.toRadians(angle)
            p[sh] = LandmarkPoint(x, 0.4f, 1f)
            p[el] = LandmarkPoint(x, 0.6f, 1f)
            p[wr] = LandmarkPoint(x + (0.2 * sin(a)).toFloat(), 0.6f - (0.2 * cos(a)).toFloat(), 1f)
        }
        arm(left, I.LEFT_SHOULDER, I.LEFT_ELBOW, I.LEFT_WRIST, 0.6f)
        arm(right, I.RIGHT_SHOULDER, I.RIGHT_ELBOW, I.RIGHT_WRIST, 0.4f)
        return p
    }

    @Test
    fun `real catalogue counters count synthetic reps`() {
        val open = RepCounter(catalogCounter("moderate.rom.02"))
        repeat(10) {
            open.run(5, pose(0.0, wristGap = 0.05f))
            open.run(5, pose(0.0, wristGap = 0.4f))
        }
        assertEquals(10, open.count)
        assertTrue(open.targetReached)
        val elbow = RepCounter(catalogCounter("moderate.rom.03"))
        elbow.run(3, elbowPose(170.0, 170.0))
        repeat(10) {
            elbow.run(5, elbowPose(30.0, 30.0))
            elbow.run(5, elbowPose(170.0, 170.0))
        }
        assertEquals(10, elbow.counts[Side.LEFT])
        assertEquals(10, elbow.counts[Side.RIGHT])
        assertTrue(elbow.targetReached)
    }
}
