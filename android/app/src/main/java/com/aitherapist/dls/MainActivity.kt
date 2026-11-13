package com.aitherapist.dls

import android.annotation.SuppressLint
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
import com.aitherapist.dls.ui.child.ChildModeKeys
import com.aitherapist.dls.ui.theme.DlsTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
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
        // Screen pinning while child mode is on (step screen or its PIN exit); off when the adult leaves.
        lifecycleScope.launch {
            snapshotFlow { gate.childModeActive }.distinctUntilChanged().collect { active -> setLockTask(active) }
        }
    }

    private fun setLockTask(active: Boolean) {
        try {
            if (active) startLockTask() else stopLockTask()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "lock task ${if (active) "start" else "stop"} failed: ${e::class.simpleName}")
        }
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

        /** Volume and power keep their system meaning; every other key is Done. */
        val PASS_THROUGH_KEYS =
            setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER)
        val APP_LOCALE: Locale = Locale.forLanguageTag("ar-EG")
    }
}
