package com.aitherapist.dls.data

/** Therapist-facing settings (PIN gate and proxy), implemented by [SecureSettings]. Calls do disk and Keystore work. */
interface TherapistSettings {
    suspend fun hasPin(): Boolean

    /** Hashes [pin] with [PinHasher] and stores only the hash, salt and iterations. */
    suspend fun setPin(pin: String)

    suspend fun verifyPin(pin: String): Boolean

    /** Persisted PIN lockout: consecutive wrong PINs and the wall-clock ms the lockout ends (0 = none). */
    suspend fun pinAttempts(): PinAttempts

    suspend fun setPinAttempts(attempts: PinAttempts)

    suspend fun proxyUrl(): String?

    suspend fun setProxyUrl(url: String)

    suspend fun sharedSecret(): String?

    suspend fun setSharedSecret(secret: String)

    suspend fun hasSharedSecret(): Boolean
}

/** Consecutive wrong PINs since the last right one, and when the current lockout ends (wall-clock ms, 0 = none). */
data class PinAttempts(val failures: Int = 0, val lockedUntil: Long = 0L)
