package com.aitherapist.dls.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test
    fun `enums are stored lower_snake`() {
        assertEquals("not_done", c.fromVerdict(AttemptVerdict.NOT_DONE))
        assertEquals("cannot_tell", c.fromVerdict(AttemptVerdict.CANNOT_TELL))
        assertEquals(listOf("full", "calm", "off"), EffectLevel.entries.map(c::fromEffectLevel))
        assertEquals(listOf("boy", "girl"), ChildCharacter.entries.map(c::fromCharacter))
        assertEquals("abandoned", c.fromSessionState(SessionState.ABANDONED))
        assertEquals("replay", c.fromSessionKind(SessionKind.REPLAY))
        assertEquals("none", c.fromVerifier(VerifierKind.NONE))
    }

    @Test
    fun `enums round trip`() {
        AttemptVerdict.entries.forEach { assertEquals(it, c.toVerdict(c.fromVerdict(it))) }
        EffectLevel.entries.forEach { assertEquals(it, c.toEffectLevel(c.fromEffectLevel(it))) }
        ChildCharacter.entries.forEach { assertEquals(it, c.toCharacter(c.fromCharacter(it))) }
        SessionState.entries.forEach { assertEquals(it, c.toSessionState(c.fromSessionState(it))) }
        SessionKind.entries.forEach { assertEquals(it, c.toSessionKind(c.fromSessionKind(it))) }
        VerifierKind.entries.forEach { assertEquals(it, c.toVerifier(c.fromVerifier(it))) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown enum value fails`() {
        c.toVerdict("NOT_DONE")
    }

    @Test
    fun `frame paths are a JSON array`() {
        val paths = listOf("frames/s/a-0.bin", "frames/s/a-1.bin")
        assertEquals("[\"frames/s/a-0.bin\",\"frames/s/a-1.bin\"]", c.fromPaths(paths))
        assertEquals(paths, c.toPaths(c.fromPaths(paths)))
        assertEquals(emptyList<String>(), c.toPaths("[]"))
    }
}
