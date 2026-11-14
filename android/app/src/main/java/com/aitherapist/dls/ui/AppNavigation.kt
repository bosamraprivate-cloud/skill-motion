package com.aitherapist.dls.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
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
import com.aitherapist.dls.BuildConfig
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.child.ChildModeHost
import com.aitherapist.dls.ui.child.FramingCheckRoute
import com.aitherapist.dls.ui.child.PinningNotice
import com.aitherapist.dls.ui.child.TrainingRoute
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

/**
 * Locked child mode: one assessment run (Story 2.14), or a continued session [sessionId] (Story 2.15). [thenTaskId]
 * runs next as a second session after this run's last step (jacket "both", Story 2.16).
 */
@Serializable
data class ChildModeKey(
    val childId: String,
    val taskId: String,
    val sessionId: String? = null,
    val thenTaskId: String? = null,
    /** A chained task: show the framing check inside child mode (pinning on) before its start screen. */
    val framingFirst: Boolean = false
) : NavKey

/** Called right before the app opens system Settings, so that hop does not trigger the background PIN lock. */
val LocalExternalHop = staticCompositionLocalOf<() -> Unit> { {} }

/** Adult framing check before child mode (Story 3.4); "Hand to child" replaces it with the matching [ChildModeKey]. */
@Serializable
data class FramingCheckKey(val childId: String, val taskId: String, val sessionId: String? = null, val thenTaskId: String? = null) : NavKey

/** Training hand-off (Story 4.3): the adult framing check for the selected activities (skipped with no counted one). */
@Serializable
data class TrainingKey(val childId: String, val activityIds: List<String>) : NavKey

/** Locked child-mode training run (Story 4.6), entered from [TrainingKey] once the child has the tablet. */
@Serializable
data class TrainingRunKey(val childId: String, val activityIds: List<String>, val runId: String) : NavKey

/** PIN screen above the step screen; a right PIN leaves child mode. */
@Serializable
data object ChildPinKey : NavKey

/** Navigation 3 root: Setup or Pin first, then the therapist shell; the back stack is held by [AppGateViewModel]. */
@Composable
fun AppNavigation(modifier: Modifier = Modifier, gate: AppGateViewModel = hiltViewModel()) {
    val gateState by gate.gateState.collectAsStateWithLifecycle()
    DisposableEffect(gate) {
        // Process-wide stop (not activity stop) so only real backgrounding re-locks therapist mode.
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> gate.onAppBackgrounded()
                    Lifecycle.Event.ON_START -> gate.onAppForegrounded()
                    else -> Unit
                }
            }
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
    CompositionLocalProvider(LocalExternalHop provides gate::expectExternalReturn) { AppNavDisplay(gate, modifier) }
}

@Composable
private fun AppNavDisplay(gate: AppGateViewModel, modifier: Modifier) {
    val activity = LocalActivity.current
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
                val pendingSummary by gate.pendingSummary.collectAsStateWithLifecycle()
                TherapistShell(
                    pendingSummary = pendingSummary,
                    onSummaryShown = gate::consumeSummary,
                    onOpenSample = { gate.open(SampleKey) },
                    onOpenCharacterPreview = { gate.open(CharacterPreviewKey) },
                    onOpenChildMode = { child, task -> gate.openFramingCheck(child, task) },
                    onNewSession = { child, task, then -> gate.openFramingCheck(child, task, thenTaskId = then) },
                    onContinueSession = { child, task, session -> gate.openFramingCheck(child, task, sessionId = session) },
                    onStartTraining = gate::openTraining
                )
            }
            entry<SampleKey> { SampleRoute() }
            // Character preview is a debug-only tool; release builds register no entry for it.
            if (BuildConfig.DEBUG) entry<CharacterPreviewKey> { CharacterPreviewRoute() }
            entry<ChildModeKey> { key ->
                var framingDone by rememberSaveable(key) { mutableStateOf(!key.framingFirst) }
                if (!framingDone) {
                    FramingCheckRoute(taskId = key.taskId, onHandToChild = { framingDone = true })
                } else {
                    ChildModeHost(
                        childId = key.childId,
                        taskId = key.taskId,
                        sessionId = key.sessionId,
                        thenTaskId = key.thenTaskId,
                        onNext = { next -> gate.nextChildTask(key, next) },
                        onSession = gate::onChildSession,
                        onExit = gate::openChildPin
                    )
                }
            }
            entry<FramingCheckKey> { key ->
                HandOffFrame(gate, key) { FramingCheckRoute(taskId = key.taskId, onHandToChild = { gate.requestHandOff(key) }) }
            }
            entry<TrainingKey> { key ->
                HandOffFrame(gate, key) {
                    FramingCheckRoute(taskId = "", activityIds = key.activityIds, onHandToChild = { gate.requestHandOff(key) })
                }
            }
            entry<TrainingRunKey> { key -> TrainingRoute(key.childId, key.activityIds, key.runId, onExit = gate::openChildPin) }
            entry<ChildPinKey> { PinRoute(onUnlocked = gate::leaveChildMode) }
        }
    )
}

/** The adult hand-off screen [content] with the "screen is not locked" notice on top when pinning failed for [key]. */
@Composable
private fun HandOffFrame(gate: AppGateViewModel, key: NavKey, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (gate.pinning == PinningState.Failed && gate.pendingHandOff == key) {
            PinningNotice(
                onRetry = { gate.requestHandOff(key) },
                onStartAnyway = gate::handOffUnpinned,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
