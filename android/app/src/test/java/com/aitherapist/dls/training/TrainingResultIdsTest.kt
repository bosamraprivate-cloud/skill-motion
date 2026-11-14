package com.aitherapist.dls.training

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingResultIdsTest {
    @Test
    fun `make and parse round trip`() {
        val id = TrainingResultIds.make("run-1:x", 3)
        assertEquals("run-1:x:3", id)
        assertEquals("run-1:x", TrainingResultIds.runIdOf(id))
        assertEquals(true, id.startsWith(TrainingResultIds.runPrefix("run-1:x")))
    }
}
