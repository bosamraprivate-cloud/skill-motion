package com.aitherapist.dls.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = File(context.filesDir, "datastore/test-${UUID.randomUUID()}.preferences_pb")
    private val keys = KeyManager("dls_test_settings")

    @After
    fun tearDown() {
        file.delete()
        keys.deleteKey()
    }

    private fun TestScope.settings() = SecureSettings(PreferenceDataStoreFactory.create(scope = backgroundScope) { file }, keys)

    @Test
    fun valuesRoundTripAndSecretsAreEncryptedOnDisk() = runTest(UnconfinedTestDispatcher()) {
        val s = settings()
        assertNull(s.pin())
        assertNull(s.proxyUrl())
        assertNull(s.sharedSecret())
        assertEquals(emptyMap<String, Zone>(), s.zones())

        val secret = "secret-value-123"
        val zones = mapOf("mat" to Zone(0.1f, 0.2f, 0.5f, 0.9f), "sink" to Zone(0f, 0f, 1f, 1f))
        s.setPin(PinHash(byteArrayOf(9, 8, 7), byteArrayOf(1, 2)))
        s.setProxyUrl("https://proxy.example")
        s.setSharedSecret(secret)
        s.setZones(zones)
        val passphrase = s.databasePassphrase()

        val pin = s.pin()!!
        assertArrayEquals(byteArrayOf(9, 8, 7), pin.hash)
        assertArrayEquals(byteArrayOf(1, 2), pin.salt)
        assertEquals("https://proxy.example", s.proxyUrl())
        assertEquals(secret, s.sharedSecret())
        assertEquals(zones, s.zones())
        assertArrayEquals(passphrase, s.databasePassphrase())

        val raw = file.readBytes()
        val text = String(raw, Charsets.ISO_8859_1)
        assertFalse("secret stored in clear", text.contains(secret))
        assertFalse("secret stored as plain base64", text.contains(SecureSettings.encode(secret.toByteArray())))
        assertFalse("passphrase stored as plain base64", text.contains(SecureSettings.encode(passphrase)))
    }

    @Test
    fun pinIsStoredHashedWithIterationsAndVerifies() = runTest(UnconfinedTestDispatcher()) {
        val s = settings()
        assertFalse(s.hasPin())
        assertFalse(s.verifyPin("1234"))
        assertFalse(s.hasSharedSecret())
        s.setPin("482915")
        assertTrue(s.hasPin())
        assertEquals(PinHasher.DEFAULT_ITERATIONS, s.pin()!!.iterations)
        assertTrue(s.verifyPin("482915"))
        assertFalse(s.verifyPin("482916"))
        assertFalse("PIN stored in clear", String(file.readBytes(), Charsets.ISO_8859_1).contains("482915"))
        s.setSharedSecret("x")
        assertTrue(s.hasSharedSecret())
    }

    @Test
    fun invalidZonesAreRejected() = runTest(UnconfinedTestDispatcher()) {
        val s = settings()
        listOf(Zone(-0.1f, 0f, 0.5f, 0.5f), Zone(0f, 0f, 1.1f, 1f), Zone(0.5f, 0f, 0.5f, 1f), Zone(0f, 0.6f, 1f, 0.2f)).forEach { z ->
            try {
                s.setZones(mapOf("mat" to z))
                org.junit.Assert.fail("Invalid zone accepted")
            } catch (expected: IllegalArgumentException) {
                // rejected
            }
        }
        assertEquals(emptyMap<String, Zone>(), s.zones())
    }
}
