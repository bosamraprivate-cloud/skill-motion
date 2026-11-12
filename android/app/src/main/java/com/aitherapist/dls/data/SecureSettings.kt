package com.aitherapist.dls.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A setup zone as a normalised, un-mirrored rectangle (0..1). */
@Serializable
data class Zone(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** PBKDF2 PIN hash and its salt (the hashing itself lives with the PIN gate). */
class PinHash(val hash: ByteArray, val salt: ByteArray)

/**
 * App-private DataStore settings (design §Encryption). The DB passphrase and the shared secret are stored only
 * encrypted with [KeyManager]; nothing here is ever logged.
 */
class SecureSettings internal constructor(private val store: DataStore<Preferences>, private val keys: KeyManager) {
    private val passphraseLock = Mutex()

    /** The 32-byte SQLCipher passphrase; generated, encrypted and stored on first call. */
    suspend fun databasePassphrase(): ByteArray = passphraseLock.withLock {
        val stored = store.data.first()[PASSPHRASE]
        if (stored != null) {
            keys.decrypt(decode(stored))
        } else {
            val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
            val sealed = encode(keys.encrypt(fresh))
            store.edit { it[PASSPHRASE] = sealed }
            fresh
        }
    }

    suspend fun setPin(pin: PinHash) {
        store.edit {
            it[PIN_HASH] = encode(pin.hash)
            it[PIN_SALT] = encode(pin.salt)
        }
    }

    suspend fun pin(): PinHash? {
        val prefs = store.data.first()
        val hash = prefs[PIN_HASH] ?: return null
        val salt = prefs[PIN_SALT] ?: return null
        return PinHash(decode(hash), decode(salt))
    }

    suspend fun setProxyUrl(url: String) {
        store.edit { it[PROXY_URL] = url }
    }

    suspend fun proxyUrl(): String? = store.data.first()[PROXY_URL]

    suspend fun setSharedSecret(secret: String) {
        val sealed = encode(keys.encrypt(secret.toByteArray(Charsets.UTF_8)))
        store.edit { it[SHARED_SECRET] = sealed }
    }

    suspend fun sharedSecret(): String? = store.data.first()[SHARED_SECRET]?.let { String(keys.decrypt(decode(it)), Charsets.UTF_8) }

    suspend fun setZones(zones: Map<String, Zone>) {
        zones.values.forEach { z ->
            require(listOf(z.left, z.top, z.right, z.bottom).all { it in 0f..1f }) { "Zone outside 0..1" }
            require(z.left < z.right && z.top < z.bottom) { "Zone has no area" }
        }
        store.edit { it[ZONES] = Json.encodeToString(ZONES_SERIALIZER, zones) }
    }

    suspend fun zones(): Map<String, Zone> = store.data.first()[ZONES]?.let { Json.decodeFromString(ZONES_SERIALIZER, it) } ?: emptyMap()

    internal companion object {
        const val PASSPHRASE_BYTES = 32
        val PASSPHRASE = stringPreferencesKey("db_passphrase_enc")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PROXY_URL = stringPreferencesKey("proxy_url")
        val SHARED_SECRET = stringPreferencesKey("shared_secret_enc")
        val ZONES = stringPreferencesKey("zones")
        private val ZONES_SERIALIZER = MapSerializer(String.serializer(), Zone.serializer())

        fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
    }
}
