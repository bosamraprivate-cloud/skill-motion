package com.aitherapist.dls.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorTest {
    @Test
    fun `front camera x is un-mirrored`() {
        assertEquals(0.8f, Mirror.flipX(0.2f), 1e-6f)
    }

    @Test
    fun `flip is its own inverse`() {
        assertEquals(0.3f, Mirror.flipX(Mirror.flipX(0.3f)), 1e-6f)
    }
}
