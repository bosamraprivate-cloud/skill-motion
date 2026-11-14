package com.aitherapist.dls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Story 5.5: cannot-tell rate as a whole percent; no attempts gives null ("No attempts yet"). */
class CannotTellCountsTest {
    @Test
    fun `no attempts has no rate`() {
        assertNull(CannotTellCounts(0, 0).percent)
    }

    @Test
    fun `rate rounds to the nearest percent`() {
        assertEquals(33, CannotTellCounts(3, 1).percent)
        assertEquals(67, CannotTellCounts(3, 2).percent)
        assertEquals(0, CannotTellCounts(5, 0).percent)
        assertEquals(100, CannotTellCounts(4, 4).percent)
    }
}
