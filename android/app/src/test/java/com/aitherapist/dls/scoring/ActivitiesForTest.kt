package com.aitherapist.dls.scoring

import com.aitherapist.dls.catalog.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitiesForTest {
    private fun act(plan: String, domain: String, n: Int) =
        Activity("$plan.$domain.0$n", plan, domain, n, "t", "ar", "g", "goal", "how", "tools", "crit", "src")

    private val activities =
        listOf("mild", "moderate", "severe").flatMap { p ->
            listOf("rom", "coord", "hand", "midline", "planning").flatMap { d -> listOf(act(p, d, 1), act(p, d, 2)) }
        }

    @Test
    fun `mixed levels give severe first then mild and nothing for a strength`() {
        val plan =
            RulesEngine.activitiesFor(
                linkedMapOf("Hand skills" to PlanLevel.MILD, "Range of motion" to PlanLevel.STRENGTH, "Motor planning" to PlanLevel.SEVERE),
                activities
            )
        assertEquals(listOf("Motor planning" to PlanLevel.SEVERE, "Hand skills" to PlanLevel.MILD), plan.map { it.domain to it.planLevel })
        assertEquals(listOf("severe.planning.01", "severe.planning.02"), plan[0].activities.map { it.id })
        assertEquals(listOf("mild.hand.01", "mild.hand.02"), plan[1].activities.map { it.id })
    }

    @Test
    fun `ties follow catalogue domain order and moderate sits in the middle`() {
        val plan =
            RulesEngine.activitiesFor(
                linkedMapOf(
                    "Midline crossing" to PlanLevel.MODERATE,
                    "Visual-motor coordination" to PlanLevel.MODERATE,
                    "Range of motion" to PlanLevel.MILD,
                    "Hand skills" to PlanLevel.SEVERE
                ),
                activities
            )
        assertEquals(listOf("Hand skills", "Visual-motor coordination", "Midline crossing", "Range of motion"), plan.map { it.domain })
        assertEquals("moderate.coord.01", plan[1].activities.first().id)
    }

    @Test
    fun `ties follow the domain order of the same plan level`() {
        val custom = listOf(act("mild", "hand", 1), act("mild", "rom", 1), act("severe", "rom", 1), act("severe", "hand", 1))
        val mild = RulesEngine.activitiesFor(linkedMapOf("Range of motion" to PlanLevel.MILD, "Hand skills" to PlanLevel.MILD), custom)
        assertEquals(listOf("Hand skills", "Range of motion"), mild.map { it.domain })
        val severe = RulesEngine.activitiesFor(
            linkedMapOf("Hand skills" to PlanLevel.SEVERE, "Range of motion" to PlanLevel.SEVERE),
            custom
        )
        assertEquals(listOf("Range of motion", "Hand skills"), severe.map { it.domain })
    }

    @Test
    fun `all strengths and unknown domains give no plan`() {
        assertTrue(RulesEngine.activitiesFor(mapOf("Hand skills" to PlanLevel.STRENGTH, "Other" to PlanLevel.SEVERE), activities).isEmpty())
        assertTrue(RulesEngine.activitiesFor(emptyMap(), activities).isEmpty())
    }

    @Test
    fun `plan codes and domain codes`() {
        assertNull(RulesEngine.planCode(PlanLevel.STRENGTH))
        assertEquals("mild", RulesEngine.planCode(PlanLevel.MILD))
        assertEquals("moderate", RulesEngine.planCode(PlanLevel.MODERATE))
        assertEquals("severe", RulesEngine.planCode(PlanLevel.SEVERE))
        assertEquals("coord", RulesEngine.activityDomain(RulesEngine.VISUAL_MOTOR_COORDINATION))
        assertNull(RulesEngine.activityDomain("Other"))
    }
}
