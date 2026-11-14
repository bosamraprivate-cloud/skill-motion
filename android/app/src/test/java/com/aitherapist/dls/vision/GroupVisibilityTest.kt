package com.aitherapist.dls.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupVisibilityTest {
    private fun pose(visible: Set<Int>) = List(33) { LandmarkPoint(0.5f, 0.5f, if (it in visible) 0.9f else 0.1f) }

    @Test
    fun `groups are seen only when all their landmarks are visible`() {
        val g = GroupVisibility(window = 4)
        val upper = setOf(0, 9, 10, 11, 12)
        g.onPose(pose(upper))
        g.onPose(pose(upper + setOf(23, 24)))
        g.onPose(pose(setOf(11)))
        g.onPose(null)
        g.onHands(listOf(listOf(LandmarkPoint(0f, 0f))))
        g.onHands(emptyList())
        val p = g.percents()
        assertEquals(50, p[LandmarkGroup.HEAD])
        assertEquals(50, p[LandmarkGroup.SHOULDERS])
        assertEquals(0, p[LandmarkGroup.ARMS])
        assertEquals(25, p[LandmarkGroup.HIPS])
        assertEquals(50, p[LandmarkGroup.HANDS])
    }

    @Test
    fun `window keeps only the latest results`() {
        val g = GroupVisibility(window = 2)
        g.onPose(null)
        g.onPose(pose(setOf(11, 12)))
        g.onPose(pose(setOf(11, 12)))
        assertEquals(100, g.percents()[LandmarkGroup.SHOULDERS])
        g.clear()
        assertEquals(0, g.percents()[LandmarkGroup.SHOULDERS])
    }

    @Test
    fun `levels by percent`() {
        assertEquals(VisibilityLevel.CLEAR, GroupVisibility.level(80))
        assertEquals(VisibilityLevel.PARTLY, GroupVisibility.level(40))
        assertEquals(VisibilityLevel.NOT_SEEN, GroupVisibility.level(39))
    }
}
