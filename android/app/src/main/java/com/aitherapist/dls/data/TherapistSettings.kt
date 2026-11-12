package com.aitherapist.dls.data

/** Therapist-facing settings (PIN gate and proxy), implemented by [SecureSettings]. Calls do disk and Keystore work. */
interface TherapistSettings {
    suspend fun hasPin(): Boolean

    /** Hashes [pin] with [PinHasher] and stores only the hash, salt and iterations. */
    suspend fun setPin(pin: String)

    suspend fun verifyPin(pin: String): Boolean

    suspend fun proxyUrl(): String?

    suspend fun setProxyUrl(url: String)

    suspend fun sharedSecret(): String?

    suspend fun setSharedSecret(secret: String)

    suspend fun hasSharedSecret(): Boolean
}
