package com.aitherapist.dls.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.aitherapist.dls.data.TherapistSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The post-PIN session summary: [childId]'s sessions of the visit that just ended (may be empty). */
data class SummaryRequest(val childId: String, val sessionIds: List<String>)

/** Start-up state of the gate: still reading settings, ready to show the back stack, or the read failed. */
sealed interface GateState {
    data object Loading : GateState

    data object Ready : GateState

    data class Error(val reason: String) : GateState
}

/**
 * Decides the start of the app (setup without a PIN, otherwise the PIN gate) and owns the back stack.
 * The stack lives only in this ViewModel, never in saved state, so after process death the PIN is asked again.
 */
@HiltViewModel
class AppGateViewModel
internal constructor(
    private val settings: TherapistSettings,
    private val io: CoroutineDispatcher,
    /** Monotonic clock in ms that keeps counting while the screen is off (injectable for tests). */
    private val clock: () -> Long = SystemClock::elapsedRealtime
) : ViewModel() {
    @Inject constructor(settings: TherapistSettings) : this(settings, Dispatchers.IO)

    val backStack: SnapshotStateList<NavKey> = mutableStateListOf()
    private val state = MutableStateFlow<GateState>(GateState.Loading)
    val gateState: StateFlow<GateState> = state.asStateFlow()

    init {
        load()
    }

    fun retry() {
        if (state.value is GateState.Error) load()
    }

    private fun load() {
        state.value = GateState.Loading
        viewModelScope.launch {
            state.value =
                try {
                    val hasPin = withContext(io) { settings.hasPin() }
                    backStack.clear()
                    backStack.add(if (hasPin) PinKey else SetupKey)
                    GateState.Ready
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    GateState.Error(e::class.simpleName ?: "unknown")
                }
        }
    }

    /** Setup finished or PIN right: replace the gate with the shell so Back cannot return to it. */
    fun enterShell() {
        backStack.clear()
        backStack.add(ShellKey)
    }

    fun open(key: NavKey) {
        backStack.add(key)
    }

    /** Child mode is on (step screen or its PIN exit on the stack). */
    val childModeActive: Boolean get() = backStack.any { it is ChildModeKey || it is TrainingRunKey }

    /** Hand-off pinning (screen pinning attempt before child mode starts); read as Compose state. */
    var pinning: PinningState by mutableStateOf(PinningState.Idle)
        private set

    /** The adult chose "Start without lock" after pinning failed: this visit runs unpinned (no new prompt). */
    private var unpinnedVisit by mutableStateOf(false)

    /** The activity should hold screen pinning: child mode on (unless started unpinned), or a hand-off is asking for it. */
    val lockWanted: Boolean get() = (childModeActive && !unpinnedVisit) || pinning == PinningState.Requesting

    /** The framing check (or training hand-off) [key] waiting for the pinning result, if any. */
    var pendingHandOff: NavKey? by mutableStateOf(null)
        private set

    /**
     * "Hand to child" on [key] ([FramingCheckKey] or [TrainingKey]): pinning is tried first; the activity reports back
     * with [onPinningResult]. Child mode starts on success; on failure the adult sees a notice with retry.
     */
    fun requestHandOff(key: NavKey) {
        if (backStack.lastOrNull() != key || pinning == PinningState.Requesting) return
        pendingHandOff = key
        pinning = PinningState.Requesting
    }

    /** The activity's pinning attempt ended: [locked] true starts child mode; false shows the adult notice. */
    fun onPinningResult(locked: Boolean) {
        if (pinning != PinningState.Requesting) return
        val key = pendingHandOff ?: return
        if (locked) completeHandOff(key) else pinning = PinningState.Failed
    }

    /** Notice "Start without lock": child mode still starts, without screen pinning for this visit. */
    fun handOffUnpinned() {
        val key = pendingHandOff ?: return
        if (pinning != PinningState.Failed) return
        unpinnedVisit = true
        completeHandOff(key)
    }

    private fun completeHandOff(key: NavKey) {
        pinning = PinningState.Idle
        pendingHandOff = null
        when (key) {
            is FramingCheckKey -> handToChild(key)
            is TrainingKey -> handToTraining(key)
        }
    }

    /** The child step screen is on top: hardware keys mean Done there. */
    val childStepOnTop: Boolean get() = backStack.lastOrNull().let { it is ChildModeKey || it is TrainingRunKey }

    /** Enters locked child mode for [childId] on [taskId] (Story 2.14); [thenTaskId] runs after it (Story 2.16). */
    fun openChildMode(childId: String, taskId: String, thenTaskId: String? = null) {
        if (childModeActive) return // a double tap must not push two runs
        visitSessions.clear()
        backStack.add(ChildModeKey(childId, taskId, thenTaskId = thenTaskId))
    }

    /**
     * The run [current] finished its last step: its chained task replaces it in place as a new session, so child
     * mode (screen pinning) stays on. [next] defaults to the key's chain; a continued session passes its saved
     * `nextTaskId` (Story 4.1). Ignored when [current] is not on top or nothing is chained.
     */
    fun nextChildTask(current: ChildModeKey, next: String? = current.thenTaskId) {
        next ?: return
        if (backStack.lastOrNull() != current) return
        // Pinning stays on; the chained task shows its framing check inside child mode (skipped if content-only).
        backStack[backStack.lastIndex] = ChildModeKey(current.childId, next, framingFirst = true)
    }

    /**
     * Story 3.4: the adult framing check before child mode (new run or continued session). Screen pinning starts only
     * on [handToChild]; Back pops it back to the profile.
     */
    fun openFramingCheck(childId: String, taskId: String, thenTaskId: String? = null, sessionId: String? = null) {
        if (childModeActive || backStack.lastOrNull() is FramingCheckKey) return
        backStack.add(FramingCheckKey(childId, taskId, sessionId, thenTaskId))
    }

    /** "Hand to child" (or Start anyway, or a content-only task): the framing check becomes child mode in place. */
    fun handToChild(key: FramingCheckKey) {
        if (backStack.lastOrNull() != key) return
        visitSessions.clear()
        backStack[backStack.lastIndex] = ChildModeKey(key.childId, key.taskId, key.sessionId, key.thenTaskId)
    }

    /** Training hand-off from the plan (Story 4.3); refused (false) with no activity or while a training screen is on top. */
    fun openTraining(childId: String, activityIds: List<String>): Boolean {
        if (activityIds.isEmpty() || childModeActive || backStack.lastOrNull() is TrainingKey) return false
        backStack.add(TrainingKey(childId, activityIds))
        return true
    }

    /** Story 4.6: after the training framing check (or its skip) the child gets the tablet; pinning starts here. */
    fun handToTraining(key: TrainingKey, runId: String = UUID.randomUUID().toString()) {
        if (backStack.lastOrNull() != key) return
        backStack[backStack.lastIndex] = TrainingRunKey(key.childId, key.activityIds, runId)
    }

    /** Adult pause sheet Exit: the PIN screen above the (paused) step screen. */
    fun openChildPin() {
        if (childStepOnTop) backStack.add(ChildPinKey)
    }

    /** Right PIN in child mode: leave child mode back to the therapist screen below it. */
    fun leaveChildMode() {
        val index = backStack.indexOfFirst { it is ChildModeKey || it is TrainingRunKey }
        if (index < 0) return
        // After an assessment visit the therapist first sees the summary of exactly this visit's sessions (agreed IA).
        (backStack[index] as? ChildModeKey)?.let { summary.value = SummaryRequest(it.childId, visitSessions.toList()) }
        visitSessions.clear()
        while (backStack.size > index) backStack.removeAt(backStack.lastIndex)
        unpinnedVisit = false
    }

    private val summary = MutableStateFlow<SummaryRequest?>(null)

    /** The summary the shell should show after leaving child mode; null when none. */
    val pendingSummary: StateFlow<SummaryRequest?> = summary.asStateFlow()

    /** Assessment sessions of the current child-mode visit, in run order (a jacket chain adds the second). */
    private val visitSessions = mutableListOf<String>()

    /** Child mode reports the session it runs (new or continued); kept for the post-PIN summary. */
    fun onChildSession(sessionId: String) {
        if (childModeActive && sessionId !in visitSessions) visitSessions += sessionId
    }

    /** The shell showed (or closed) the pending summary. */
    fun consumeSummary() {
        summary.value = null
    }

    /** Pops one entry; returns false at the root so the caller can move the task to the back. Blocked on the child step screen. */
    fun back(): Boolean {
        if (childStepOnTop) return true
        if (backStack.size <= 1) return false
        if (backStack.last() == pendingHandOff) {
            pendingHandOff = null
            pinning = PinningState.Idle
        }
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    @Volatile private var externalReturnExpected = false

    /** The app is about to open system Settings (camera permission) and come back: the next stop does not lock. */
    fun expectExternalReturn() {
        externalReturnExpected = true
    }

    /** When child mode went to the background (clock ms); null when it is in the foreground. */
    private var childBackgroundedAt: Long? = null

    /**
     * App went to the background. Therapist mode needs the PIN again at once (FR-2); setup in progress is kept. Child
     * mode only notes the time: [onAppForegrounded] decides (short screen-off returns to the same child screen).
     */
    fun onAppBackgrounded() {
        if (externalReturnExpected) {
            externalReturnExpected = false
            return
        }
        if (state.value != GateState.Ready || backStack.firstOrNull() != ShellKey) return
        if (childModeActive) {
            childBackgroundedAt = clock()
        } else {
            lockToPin()
        }
    }

    /**
     * App is back in the foreground. Child mode back within [CHILD_GRACE_MS]: the same child screen stays (its attempt
     * restarts like Resume). Later: the PIN, and the torn-down step screen saves the session `paused` (Continue card).
     */
    fun onAppForegrounded() {
        val since = childBackgroundedAt ?: return
        childBackgroundedAt = null
        if (!childModeActive) return
        if (clock() - since > CHILD_GRACE_MS) {
            visitSessions.clear()
            unpinnedVisit = false
            lockToPin()
        }
    }

    private fun lockToPin() {
        pendingHandOff = null
        pinning = PinningState.Idle
        backStack.clear()
        backStack.add(PinKey)
    }

    companion object {
        /** Child mode survives a screen-off or background this long (owner decision 2026-11-14). */
        const val CHILD_GRACE_MS = 60_000L
    }
}

/** Screen pinning attempt at hand-off: none, waiting for the system, or refused/failed (adult notice shown). */
enum class PinningState { Idle, Requesting, Failed }
