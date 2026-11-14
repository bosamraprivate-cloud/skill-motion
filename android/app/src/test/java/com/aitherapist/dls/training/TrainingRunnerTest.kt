package com.aitherapist.dls.training

import com.aitherapist.dls.catalog.Activity
import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.Criterion
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.data.TrainingResultEntity
import com.aitherapist.dls.data.TrainingResultStore
import com.aitherapist.dls.rewards.FakeRewardStore
import com.aitherapist.dls.rewards.RewardRecorder
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.PoseLandmarkIndex as I
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingRunnerTest {
    private val lm = { n: String -> LandmarkRef.Lm(n) }
    private val above = Check.AngleAbove(lm("ELBOW"), lm("SHOULDER"), lm("HIP"), 120.0, 0.0, Side.EITHER)
    private var now = 0L
    private val order = mutableListOf<String>()
    private val rows = mutableListOf<TrainingResultEntity>()
    private val rewardStore = FakeRewardStore(onWrite = { order += "reward" })
    private var addFails = false
    private val store =
        object : TrainingResultStore {
            override suspend fun add(result: TrainingResultEntity) {
                if (addFails) error("disk full")
                order += "row"
                rows += result
            }

            override suspend fun find(id: String) = rows.firstOrNull { it.id == id }
        }

    private fun activity(id: String, counter: Criterion? = null) =
        Activity(id, "moderate", "rom", 1, "t", "ar", "g", "g", "h", "tools", "c", "s", counter = counter)

    private fun runner(vararg activities: Activity) = TrainingRunner(
        childId = "c",
        activities = activities.toList(),
        store = store,
        rewards = RewardRecorder(rewardStore) { 1L },
        clock = { now },
        runId = "run",
        wallClock = { 7L }
    )

    private fun pose(angle: Double): List<LandmarkPoint> {
        val p = MutableList(33) { LandmarkPoint(0.5f, 0.5f, 0f) }
        val a = Math.toRadians(angle)
        for ((sh, el, hip, x) in listOf(
            listOf(I.LEFT_SHOULDER, I.LEFT_ELBOW, I.LEFT_HIP, 6),
            listOf(I.RIGHT_SHOULDER, I.RIGHT_ELBOW, I.RIGHT_HIP, 4)
        )) {
            val xf = x / 10f
            p[sh] = LandmarkPoint(xf, 0.4f, 1f)
            p[hip] = LandmarkPoint(xf, 0.7f, 1f)
            p[el] = LandmarkPoint(xf + (0.2 * sin(a)).toFloat(), 0.4f + (0.2 * cos(a)).toFloat(), 1f)
        }
        return p
    }

    private fun TrainingRunner.frames(n: Int, angle: Double?) = repeat(n) {
        feed(LandmarkFrame(now, angle?.let { pose(it) }))
        now += 100
    }

    @Test
    fun `activities run in order and the end comes after the last`() = runTest {
        val r = runner(activity("a"), activity("b"), activity("c"))
        r.start()
        listOf("a", "b", "c").forEach {
            assertEquals(it, r.current.value.activity?.id)
            now += 5_000
            assertTrue(r.finish())
        }
        assertTrue(r.current.value.ended)
        assertEquals(listOf("a", "b", "c"), rows.map { it.activityId })
        assertEquals(listOf(5, 5, 5), rows.map { it.durationSec })
        assertEquals(listOf(null, null, null), rows.map { it.count })
        assertEquals(listOf("run:0", "run:1", "run:2"), rows.map { it.id })
        assertFalse(r.finish())
    }

    @Test
    fun `counted reps reach the target, save once with the count, then stars`() = runTest {
        val r = runner(activity("m", Criterion.Reps(above, 2, false, 60.0, 120.0)))
        r.start()
        assertTrue(r.current.value.counted)
        repeat(2) {
            r.frames(5, 150.0)
            r.frames(5, 30.0)
        }
        assertEquals(2, r.current.value.count)
        assertTrue(r.current.value.targetReached)
        r.tick()
        assertTrue(r.finish())
        assertFalse(r.finish())
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].count)
        assertTrue(rows[0].criterionMet)
        assertEquals(listOf("row", "reward"), order)
        assertEquals(2, rewardStore.rows.values.single().stars)
    }

    @Test
    fun `no landmarks gives no count, Done saves criterion not met and one star`() = runTest {
        val r = runner(activity("m", Criterion.Hold(above, 5.0, false, 60.0, 120.0)))
        r.start()
        r.frames(20, null)
        assertEquals(0, r.current.value.count)
        r.finish()
        assertEquals(0, rows.single().count)
        assertFalse(rows.single().criterionMet)
        assertEquals(1, rewardStore.rows.values.single().stars)
    }

    @Test
    fun `pause freezes the timer and frames, interrupt writes nothing`() = runTest {
        val r = runner(activity("m", Criterion.Reps(above, 2, false, 60.0, 120.0)))
        r.start()
        now = 1_000
        r.pause()
        r.pause()
        now = 9_000
        r.frames(5, 150.0)
        r.frames(5, 30.0)
        r.tick()
        assertEquals(1_000, r.current.value.elapsedMs)
        assertEquals(0, r.current.value.count)
        r.resume()
        now += 1_000
        r.tick()
        assertEquals(2_000, r.current.value.elapsedMs)
        r.interrupt()
        assertFalse(r.finish())
        assertTrue(rows.isEmpty())
        assertTrue(rewardStore.rows.isEmpty())
    }

    @Test
    fun `invalid counter falls back to guided and empty list is ended`() = runTest {
        val r = runner(activity("x", Criterion.Reps(above, 2, false, 120.0, 60.0)))
        r.start()
        assertFalse(r.current.value.counted)
        r.finish()
        assertNull(rows.single().count)
        val empty = runner()
        empty.start()
        assertTrue(empty.current.value.ended)
        assertFalse(empty.finish())
    }

    @Test
    fun `restored run skips saved activities and re-records their stars once`() = runTest {
        val first = runner(activity("a"), activity("b"), activity("c"))
        first.start()
        first.finish()
        rewardStore.rows.clear() // crash between the row and its reward
        val restored = runner(activity("a"), activity("b"), activity("c"))
        restored.start()
        assertEquals("b", restored.current.value.activity?.id)
        assertEquals(setOf("training:run:0"), rewardStore.rows.keys)
        restored.finish()
        val again = runner(activity("a"), activity("b"), activity("c"))
        again.start()
        assertEquals("c", again.current.value.activity?.id)
        assertEquals(2, rewardStore.rows.size)
        assertEquals(listOf("run:0", "run:1"), rows.map { it.id })
        val done = runner(activity("a"))
        done.start()
        assertTrue(done.current.value.ended)
    }

    @Test
    fun `failed row write keeps the activity and Done retries`() = runTest {
        val r = runner(activity("a"), activity("b"))
        r.start()
        addFails = true
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { r.finish() } }
        assertEquals("a", r.current.value.activity?.id)
        addFails = false
        assertTrue(r.finish())
        assertEquals("b", r.current.value.activity?.id)
        assertEquals(1, rows.size)
    }

    @Test
    fun `failed reward write retries with the same result id and no second row`() = runTest {
        val r = runner(activity("a"))
        r.start()
        rewardStore.fail = true
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { r.finish() } }
        assertFalse(r.current.value.ended)
        rewardStore.fail = false
        assertTrue(r.finish())
        assertEquals(1, rows.size)
        assertEquals(setOf("training:run:0"), rewardStore.rows.keys)
        assertTrue(r.current.value.ended)
    }
}
