package com.aitherapist.dls.ui.child

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugVerdictTraceTest {
    @Test
    fun `a saved attempt line becomes a strip entry`() {
        val v = DebugVerdictTrace.parse("step=eat.a.01 try=1 level=1 verdict=CANNOT_TELL reason=no_zone verifier=pose frames=3 ms=40", 7)
        assertEquals(DebugVerdict("eat.a.01", "cannot_tell", "no_zone", "pose", 7), v)
    }

    @Test
    fun `replay and malformed lines are ignored`() {
        assertNull(DebugVerdictTrace.parse("replay step=a try=1 level=1 verdict=DONE reason=x", 1))
        assertNull(DebugVerdictTrace.parse("step=a try=1", 1))
    }
}
