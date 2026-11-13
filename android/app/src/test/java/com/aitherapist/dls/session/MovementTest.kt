package com.aitherapist.dls.session

import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.PoseLandmarkIndex.LEFT_ELBOW
import com.aitherapist.dls.vision.PoseLandmarkIndex.LEFT_SHOULDER
import com.aitherapist.dls.vision.PoseLandmarkIndex.LEFT_WRIST
import com.aitherapist.dls.vision.PoseLandmarkIndex.RIGHT_ELBOW
import com.aitherapist.dls.vision.PoseLandmarkIndex.RIGHT_SHOULDER
import com.aitherapist.dls.vision.PoseLandmarkIndex.RIGHT_WRIST
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementTest {
    // Shoulder width 0.5 -> threshold 0.015.
    private fun sample(
        tMs: Long,
        jointX: Float,
        jointVis: Float = 1f,
        shoulders: Pair<Float, Float>? = 0.25f to 0.75f,
        joint: Int = LEFT_WRIST
    ): PoseSample {
        val joints = mutableMapOf(joint to LandmarkPoint(jointX, 0.5f, jointVis))
        shoulders?.let {
            joints[LEFT_SHOULDER] = LandmarkPoint(it.second, 0.3f)
            joints[RIGHT_SHOULDER] = LandmarkPoint(it.first, 0.3f)
        }
        return PoseSample(tMs, joints)
    }

    // Shoulder width 12.5 makes 0.03 x width = 0.375 exactly representable, as is 0.5 -> 0.875.
    private val wide = 0f to 12.5f

    @Test
    fun `displacement exactly at threshold is not movement`() {
        assertFalse(Movement.moved(listOf(sample(0, 0.5f, shoulders = wide), sample(400, 0.875f, shoulders = wide)), 400))
    }

    @Test
    fun `displacement of 0_031 x width is movement`() {
        assertTrue(Movement.moved(listOf(sample(0, 0.5f, shoulders = wide), sample(400, 0.8875f, shoulders = wide)), 400))
    }

    @Test
    fun `every tracked joint counts as movement`() {
        listOf(LEFT_WRIST, RIGHT_WRIST, LEFT_ELBOW, RIGHT_ELBOW).forEach { joint ->
            assertTrue("joint $joint", Movement.moved(listOf(sample(0, 0.5f, joint = joint), sample(400, 0.6f, joint = joint)), 400))
        }
    }

    @Test
    fun `samples older than half a second are ignored`() {
        assertFalse(Movement.moved(listOf(sample(0, 0.1f), sample(600, 0.5f), sample(700, 0.5f)), 700))
    }

    @Test
    fun `unknown shoulder width means not moving`() {
        assertFalse(Movement.moved(listOf(sample(0, 0.1f, shoulders = null), sample(400, 0.9f, shoulders = null)), 400))
    }

    @Test
    fun `zero shoulder width means not moving`() {
        assertFalse(Movement.moved(listOf(sample(0, 0.1f, shoulders = 0.5f to 0.5f), sample(400, 0.9f, shoulders = 0.5f to 0.5f)), 400))
    }

    @Test
    fun `low visibility joint is ignored`() {
        assertFalse(Movement.moved(listOf(sample(0, 0.5f), sample(400, 0.9f, jointVis = 0.3f)), 400))
    }

    @Test
    fun `no samples means not moving`() {
        assertFalse(Movement.moved(emptyList(), 0))
    }

    @Test
    fun `hand wrist motion counts as movement while the body is still`() {
        fun withHand(t: Long, x: Float) = sample(t, 0.5f).copy(hands = listOf(listOf(LandmarkPoint(x, 0.6f))))
        assertTrue(Movement.moved(listOf(withHand(0, 0.40f), withHand(100, 0.45f)), 100))
        assertFalse(Movement.moved(listOf(withHand(0, 0.40f), withHand(100, 0.40f)), 100))
    }
}
