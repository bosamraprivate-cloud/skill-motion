package com.aitherapist.dls.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetAudioPlayerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun missingAssetReturnsFalseAndNeverCallsBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val player = AssetAudioPlayer(context)
        var ended = 0
        var started = true
        instrumentation.runOnMainSync { started = player.play("audio/does-not-exist.mp3") { ended++ } }
        assertFalse(started)
        Thread.sleep(CALLBACK_WAIT_MS)
        instrumentation.waitForIdleSync()
        assertEquals(0, ended)
        instrumentation.runOnMainSync { player.stop() }
    }

    private companion object {
        const val CALLBACK_WAIT_MS = 300L
    }
}
