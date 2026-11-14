package com.aitherapist.dls

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import com.aitherapist.dls.ui.AppGateViewModel
import com.aitherapist.dls.ui.AppNavigation
import com.aitherapist.dls.ui.PinningState
import com.aitherapist.dls.ui.child.ChildModeKeys
import com.aitherapist.dls.ui.theme.DlsTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var childKeys: ChildModeKeys

    // Same instance AppNavigation gets from hiltViewModel() (the activity is the root ViewModelStoreOwner).
    private val gate: AppGateViewModel by viewModels()

    // The app is Egyptian Arabic (RTL) whatever the device language is.
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { setLocale(APP_LOCALE) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DlsTheme {
                AppNavigation(gate = gate)
            }
        }
        // Screen pinning while child mode is on (step screen or its PIN exit) or a hand-off asks for it; off on leave.
        lifecycleScope.launch {
            snapshotFlow { gate.lockWanted }.distinctUntilChanged().collect { active -> setLockTask(active) }
        }
        // Hand-off: report whether pinning actually took hold, so the adult sees a notice when it did not.
        lifecycleScope.launch {
            snapshotFlow { gate.pinning }.distinctUntilChanged().collectLatest { pinning ->
                if (pinning == PinningState.Requesting) gate.onPinningResult(awaitPinned())
            }
        }
    }

    /** Polls the lock-task state; the system pinning prompt may need a moment (or be declined). */
    private suspend fun awaitPinned(): Boolean {
        val manager = getSystemService(ActivityManager::class.java) ?: return false
        repeat(PIN_POLLS) {
            if (manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return true
            delay(PIN_POLL_MS)
        }
        return manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun setLockTask(active: Boolean): Boolean = try {
        if (active) startLockTask() else stopLockTask()
        true
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.w(TAG, "lock task ${if (active) "start" else "stop"} failed: ${e::class.simpleName}")
        false
    }

    /** On the child step screen any key press (Bluetooth clicker included) except volume/power is Done; Back does nothing there. */
    // Lint flags the public Activity.dispatchKeyEvent override through ComponentActivity's androidx.core overload
    // (a known false positive); overriding the platform method is supported.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!gate.childStepOnTop || event.keyCode in PASS_THROUGH_KEYS) return super.dispatchKeyEvent(event)
        if (event.keyCode != KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            childKeys.press()
        }
        return true
    }

    private companion object {
        const val TAG = "DlsMain"
        const val PIN_POLLS = 24
        const val PIN_POLL_MS = 250L

        /** Volume and power keep their system meaning; every other key is Done. */
        val PASS_THROUGH_KEYS =
            setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER)
        val APP_LOCALE: Locale = Locale.forLanguageTag("ar-EG")
    }
}
