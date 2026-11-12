package com.aitherapist.dls.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2WithHmacSHA256 PIN hashing; the iteration count is stored with the hash so it can change later. */
object PinHasher {
    const val DEFAULT_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): PinHash {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return PinHash(derive(pin, salt, iterations), salt, iterations)
    }

    /** Constant-time comparison against the stored hash. */
    fun verify(pin: String, stored: PinHash): Boolean = MessageDigest.isEqual(derive(pin, stored.salt, stored.iterations), stored.hash)

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
