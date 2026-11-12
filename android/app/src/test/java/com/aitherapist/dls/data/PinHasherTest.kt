package com.aitherapist.dls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun `same pin hashed twice gets different salts and hashes`() {
        val a = PinHasher.hash("1234")
        val b = PinHasher.hash("1234")
        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.hash.contentEquals(b.hash))
    }

    @Test
    fun `verify accepts the right pin and rejects a wrong one`() {
        val stored = PinHasher.hash("482915")
        assertTrue(PinHasher.verify("482915", stored))
        assertFalse(PinHasher.verify("482916", stored))
        assertFalse(PinHasher.verify("", stored))
    }

    @Test
    fun `iterations are stored with the hash and used to verify`() {
        val stored = PinHasher.hash("1234", iterations = 1_000)
        assertEquals(1_000, stored.iterations)
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.hash("1234").iterations)
        assertTrue(PinHasher.verify("1234", stored))
        assertFalse(PinHasher.verify("1234", PinHash(stored.hash, stored.salt, 2_000)))
    }

    @Test
    fun `hash is 256 bits and salt 128 bits`() {
        val stored = PinHasher.hash("1234", iterations = 1_000)
        assertEquals(32, stored.hash.size)
        assertEquals(16, stored.salt.size)
    }
}
