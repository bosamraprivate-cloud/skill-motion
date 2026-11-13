package com.aitherapist.dls.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The [AudioPlayer] contract the step screen relies on, checked on the fake used by its tests. */
class AudioPlayerContractTest {
    @Test
    fun `finished sound calls back once`() {
        val player = FakeAudioPlayer()
        var ended = 0
        assertTrue(player.play("a.mp3") { ended++ })
        player.finish()
        player.finish()
        assertEquals(1, ended)
    }

    @Test
    fun `stop and a new play drop the old callback`() {
        val player = FakeAudioPlayer()
        var first = 0
        var second = 0
        player.play("a.mp3") { first++ }
        player.play("b.mp3") { second++ }
        player.finish()
        assertEquals(0, first)
        assertEquals(1, second)
        player.play("c.mp3") { first++ }
        player.stop()
        player.finish()
        assertEquals(0, first)
    }

    @Test
    fun `missing file returns false and never calls back`() {
        val player = FakeAudioPlayer(available = { false })
        var ended = 0
        assertFalse(player.play("missing.mp3") { ended++ })
        player.finish()
        assertEquals(0, ended)
        assertFalse(player.playing)
    }
}
