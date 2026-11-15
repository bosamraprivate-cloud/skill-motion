package com.aitherapist.dls

import com.aitherapist.dls.ui.FramingCheckKey
import com.aitherapist.dls.ui.ShellKey
import com.aitherapist.dls.ui.TrainingKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationRuleTest {
    @Test
    fun `child mode holds the orientation whatever is on top`() {
        assertTrue(keepsOrientation(childModeActive = true, top = ShellKey))
        assertTrue(keepsOrientation(childModeActive = true, top = null))
    }

    @Test
    fun `framing checks hold the orientation before child mode starts`() {
        assertTrue(keepsOrientation(childModeActive = false, top = FramingCheckKey("c", "t")))
        assertTrue(keepsOrientation(childModeActive = false, top = TrainingKey("c", listOf("a"))))
    }

    @Test
    fun `therapist screens rotate freely`() {
        assertFalse(keepsOrientation(childModeActive = false, top = ShellKey))
        assertFalse(keepsOrientation(childModeActive = false, top = null))
    }
}
