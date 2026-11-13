package com.aitherapist.dls.ui.child

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoneDebouncerTest {
    @Test
    fun `second press within one second is ignored and one after it counts`() {
        var now = 10_000L
        val debouncer = DoneDebouncer({ now })
        assertTrue(debouncer.accept())
        now += 999
        assertFalse(debouncer.accept())
        now += 1
        assertTrue(debouncer.accept())
        now += 500
        assertFalse(debouncer.accept())
    }
}
