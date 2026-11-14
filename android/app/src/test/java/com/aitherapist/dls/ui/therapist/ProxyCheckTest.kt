package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.TherapistSettings
import com.aitherapist.dls.vision.HealthResult
import com.aitherapist.dls.vision.ProxyHealth
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Story 5.5: unreadable settings show "couldn't read settings", never "not set up", and no ping is sent. */
class ProxyCheckTest {
    @Test
    fun `unreadable settings are reported and skip the ping`() = runBlocking {
        var pinged = false
        val broken =
            object : TherapistSettings by FakeTherapistSettings() {
                override suspend fun proxyUrl(): String = throw IOException("keystore")
            }
        val result = proxyCheck(broken) { _, _ -> HealthResult(ProxyHealth.OK, 1).also { pinged = true } }
        assertEquals(ProxyHealth.SETTINGS_UNREADABLE, result.health)
        assertFalse(pinged)
    }

    @Test
    fun `readable settings are passed to the ping`() = runBlocking {
        val result =
            proxyCheck(FakeTherapistSettings(url = "https://p", secret = "s")) { url, secret ->
                assertEquals("https://p" to "s", url to secret)
                HealthResult(ProxyHealth.OK, 420)
            }
        assertEquals(ProxyCheck.Done(ProxyHealth.OK, 420), result)
    }
}
