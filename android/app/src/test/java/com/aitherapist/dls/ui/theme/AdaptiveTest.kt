package com.aitherapist.dls.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveTest {
    @Test
    fun `tablet portrait is portrait`() {
        assertEquals(LayoutMode.Portrait, layoutModeFor(widthDp = 800, heightDp = 1280))
    }

    @Test
    fun `tablet landscape is landscape`() {
        assertEquals(LayoutMode.Landscape, layoutModeFor(widthDp = 1280, heightDp = 800))
    }

    @Test
    fun `square window counts as landscape`() {
        assertEquals(LayoutMode.Landscape, layoutModeFor(widthDp = 900, heightDp = 900))
    }
}
