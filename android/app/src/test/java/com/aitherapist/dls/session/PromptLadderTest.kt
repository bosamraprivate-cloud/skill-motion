package com.aitherapist.dls.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PromptLadderTest {
    @Test
    fun `help at level 1 goes to 2 with same attempt`() {
        assertEquals(PromptState(2, true, 2), PromptLadder.onHelp(PromptState(1, false, 2)))
    }

    @Test
    fun `help at level 3 stays 3`() {
        assertEquals(PromptState(3, true, 1), PromptLadder.onHelp(PromptState(3, false, 1)))
    }

    @Test
    fun `repeat changes nothing`() {
        listOf(PromptState(), PromptState(2, true, 3), PromptState(3, false, 1)).forEach {
            assertEquals(it, PromptLadder.onRepeat(it))
        }
    }

    @Test
    fun `invalid states are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PromptState(level = 0) }
        assertThrows(IllegalArgumentException::class.java) { PromptState(level = 4) }
        assertThrows(IllegalArgumentException::class.java) { PromptState(attempt = 0) }
    }
}
