package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.PinHash
import com.aitherapist.dls.data.PinHasher
import com.aitherapist.dls.data.TherapistSettings
import java.io.IOException

/** In-memory settings using the real [PinHasher] (low iterations for speed). */
class FakeTherapistSettings(pin: String? = null, var url: String? = null, var secret: String? = null) : TherapistSettings {
    var pinHash: PinHash? = pin?.let { PinHasher.hash(it, ITERATIONS) }
    var failWrites = false
    var failReads = false

    override suspend fun hasPin(): Boolean {
        if (failReads) throw IOException("disk")
        return pinHash != null
    }

    override suspend fun setPin(pin: String) {
        if (failWrites) throw IOException("disk")
        pinHash = PinHasher.hash(pin, ITERATIONS)
    }

    override suspend fun verifyPin(pin: String): Boolean {
        if (failReads) throw IOException("disk")
        return pinHash?.let { PinHasher.verify(pin, it) } ?: false
    }

    override suspend fun proxyUrl(): String? = url

    override suspend fun setProxyUrl(url: String) {
        if (failWrites) throw IOException("disk")
        this.url = url
    }

    override suspend fun sharedSecret(): String? = secret

    override suspend fun setSharedSecret(secret: String) {
        if (failWrites) throw IOException("disk")
        this.secret = secret
    }

    override suspend fun hasSharedSecret(): Boolean = secret != null

    private companion object {
        const val ITERATIONS = 1_000
    }
}
