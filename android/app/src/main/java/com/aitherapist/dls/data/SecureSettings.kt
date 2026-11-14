package com.aitherapist.dls.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Draft plan selection per child (Story 4.3): activity ids only, non-secret; cleared when training starts. */
interface PlanDraftStore {
    suspend fun draft(childId: String): List<String>

    /** An empty list clears the child's draft. */
    suspend fun setDraft(childId: String, activityIds: List<String>)
}

/** A setup zone as a normalised, un-mirrored rectangle (0..1). */
@Serializable
data class Zone(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** PBKDF2 PIN hash with its salt and iteration count (see [PinHasher]). */
class PinHash(val hash: ByteArray, val salt: ByteArray, val iterations: Int = PinHasher.DEFAULT_ITERATIONS)

/**
 * App-private DataStore settings (design §Encryption). The DB passphrase and the shared secret are stored only
 * encrypted with [KeyManager]; nothing here is ever logged.
 */
class SecureSettings internal constructor(private val store: DataStore<Preferences>, private val keys: KeyManager) :
    TherapistSettings,
    ZoneStore,
    PlanDraftStore {
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
            it[PIN_ITERATIONS] = pin.iterations
        }
    }

    suspend fun pin(): PinHash? {
        val prefs = store.data.first()
        val hash = prefs[PIN_HASH] ?: return null
        val salt = prefs[PIN_SALT] ?: return null
        return PinHash(decode(hash), decode(salt), prefs[PIN_ITERATIONS]?.takeIf { it > 0 } ?: PinHasher.DEFAULT_ITERATIONS)
    }

    override suspend fun hasPin(): Boolean = pin() != null

    override suspend fun setPin(pin: String) = setPin(PinHasher.hash(pin))

    override suspend fun verifyPin(pin: String): Boolean = pin()?.let { PinHasher.verify(pin, it) } ?: false

    override suspend fun hasSharedSecret(): Boolean = store.data.first()[SHARED_SECRET] != null

    override suspend fun setProxyUrl(url: String) {
        store.edit { it[PROXY_URL] = url }
    }

    override suspend fun proxyUrl(): String? = store.data.first()[PROXY_URL]

    override suspend fun setSharedSecret(secret: String) {
        val sealed = encode(keys.encrypt(secret.toByteArray(Charsets.UTF_8)))
        store.edit { it[SHARED_SECRET] = sealed }
    }

    override suspend fun sharedSecret(): String? = store.data.first()[SHARED_SECRET]?.let {
        String(keys.decrypt(decode(it)), Charsets.UTF_8)
    }

    override suspend fun setZones(zones: Map<String, Zone>) {
        zones.values.forEach { z ->
            require(listOf(z.left, z.top, z.right, z.bottom).all { it in 0f..1f }) { "Zone outside 0..1" }
            require(z.left < z.right && z.top < z.bottom) { "Zone has no area" }
        }
        store.edit { it[ZONES] = Json.encodeToString(ZONES_SERIALIZER, zones) }
    }

    override suspend fun zones(): Map<String, Zone> =
        store.data.first()[ZONES]?.let { Json.decodeFromString(ZONES_SERIALIZER, it) } ?: emptyMap()

    override suspend fun draft(childId: String): List<String> = drafts()[childId].orEmpty()

    override suspend fun setDraft(childId: String, activityIds: List<String>) {
        store.edit { prefs ->
            val all = prefs[PLAN_DRAFTS]?.let { Json.decodeFromString(DRAFTS_SERIALIZER, it) }.orEmpty().toMutableMap()
            if (activityIds.isEmpty()) all.remove(childId) else all[childId] = activityIds
            prefs[PLAN_DRAFTS] = Json.encodeToString(DRAFTS_SERIALIZER, all)
        }
    }

    private suspend fun drafts(): Map<String, List<String>> =
        store.data.first()[PLAN_DRAFTS]?.let { Json.decodeFromString(DRAFTS_SERIALIZER, it) } ?: emptyMap()

    internal companion object {
        val PLAN_DRAFTS = stringPreferencesKey("plan_drafts")
        private val DRAFTS_SERIALIZER = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
        const val PASSPHRASE_BYTES = 32
        val PASSPHRASE = stringPreferencesKey("db_passphrase_enc")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_ITERATIONS = intPreferencesKey("pin_iterations")
        val PROXY_URL = stringPreferencesKey("proxy_url")
        val SHARED_SECRET = stringPreferencesKey("shared_secret_enc")
        val ZONES = stringPreferencesKey("zones")
        private val ZONES_SERIALIZER = MapSerializer(String.serializer(), Zone.serializer())

        fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
    }
}
