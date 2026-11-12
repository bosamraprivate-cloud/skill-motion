package com.aitherapist.dls.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NextChildCodeTest {
    @Test
    fun `first code is C-001`() {
        assertEquals("C-001", nextChildCode(emptyList()))
    }

    @Test
    fun `next is highest numeric suffix plus one`() {
        assertEquals("C-008", nextChildCode(listOf("C-002", "C-007", "C-003")))
    }

    @Test
    fun `codes outside the pattern are ignored`() {
        assertEquals("C-002", nextChildCode(listOf("AB1", "C-001", "C-X9", "C-", "c-050", "C--5")))
    }

    @Test
    fun `padding grows past 999`() {
        assertEquals("C-1000", nextChildCode(listOf("C-999")))
    }
}
