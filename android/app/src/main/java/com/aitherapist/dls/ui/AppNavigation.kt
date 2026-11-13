package com.aitherapist.dls.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.child.ChildModeHost
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.sample.SampleRoute
import com.aitherapist.dls.ui.therapist.CharacterPreviewRoute
import com.aitherapist.dls.ui.therapist.PinRoute
import com.aitherapist.dls.ui.therapist.SetupRoute
import com.aitherapist.dls.ui.therapist.TherapistShell
import kotlinx.serialization.Serializable

/** First-run setup (no PIN stored yet). */
@Serializable
data object SetupKey : NavKey

/** PIN gate shown on every app start once setup is done. */
@Serializable
data object PinKey : NavKey

/** Therapist shell with the start-side rail (reachable only through setup or the PIN gate). */
@Serializable
data object ShellKey : NavKey

/** Typed navigation key for the reference sample screen (AD-13). */
@Serializable
data object SampleKey : NavKey

/** Typed navigation key for the therapist character clip preview (Story 2.3). */
@Serializable
data object CharacterPreviewKey : NavKey

/** Locked child mode: the step screen for one assessment run (Story 2.14). */
@Serializable
data class ChildModeKey(val childId: String, val taskId: String) : NavKey

/** PIN screen above the step screen; a right PIN leaves child mode. */
@Serializable
data object ChildPinKey : NavKey

/** Navigation 3 root: Setup or Pin first, then the therapist shell; the back stack is held by [AppGateViewModel]. */
@Composable
fun AppNavigation(modifier: Modifier = Modifier, gate: AppGateViewModel = hiltViewModel()) {
    val gateState by gate.gateState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    DisposableEffect(gate) {
        // Process-wide stop (not activity stop) so only real backgrounding re-locks therapist mode.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) gate.onAppBackgrounded() }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    when (val current = gateState) {
        GateState.Loading -> {
            Box(modifier.fillMaxSize())
            return
        }
        is GateState.Error -> {
            ErrorState(stringResource(R.string.gate_error, current.reason), onRetry = gate::retry, modifier = modifier.fillMaxSize())
            return
        }
        GateState.Ready -> Unit
    }
    NavDisplay(
        backStack = gate.backStack,
        modifier = modifier,
        onBack = { if (!gate.back()) activity?.moveTaskToBack(true) },
        entryDecorators =
        listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider =
        entryProvider {
            entry<SetupKey> { SetupRoute(onFinished = gate::enterShell) }
            entry<PinKey> { PinRoute(onUnlocked = gate::enterShell) }
            entry<ShellKey> {
                TherapistShell(
                    onOpenSample = { gate.open(SampleKey) },
                    onOpenCharacterPreview = { gate.open(CharacterPreviewKey) },
                    onOpenChildMode = gate::openChildMode
                )
            }
            entry<SampleKey> { SampleRoute() }
            entry<CharacterPreviewKey> { CharacterPreviewRoute() }
            entry<ChildModeKey> { key -> ChildModeHost(childId = key.childId, taskId = key.taskId, onAdultCorner = gate::openChildPin) }
            entry<ChildPinKey> { PinRoute(onUnlocked = gate::leaveChildMode) }
        }
    )
}
