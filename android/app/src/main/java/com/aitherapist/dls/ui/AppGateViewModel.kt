package com.aitherapist.dls.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.aitherapist.dls.data.TherapistSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
internal constructor(private val settings: TherapistSettings, private val io: CoroutineDispatcher) : ViewModel() {
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

    /** Child mode is on (step screen or its PIN exit on the stack): screen pinning stays active. */
    val childModeActive: Boolean get() = backStack.any { it is ChildModeKey }

    /** The child step screen is on top: hardware keys mean Done there. */
    val childStepOnTop: Boolean get() = backStack.lastOrNull() is ChildModeKey

    /** Enters locked child mode for [childId] on [taskId] (Story 2.14); [thenTaskId] runs after it (Story 2.16). */
    fun openChildMode(childId: String, taskId: String, thenTaskId: String? = null) {
        if (childModeActive) return // a double tap must not push two runs
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
        backStack[backStack.lastIndex] = ChildModeKey(key.childId, key.taskId, key.sessionId, key.thenTaskId)
    }

    /** Adult pause sheet Exit: the PIN screen above the (paused) step screen. */
    fun openChildPin() {
        if (childStepOnTop) backStack.add(ChildPinKey)
    }

    /** Right PIN in child mode: leave child mode back to the therapist screen below it. */
    fun leaveChildMode() {
        val index = backStack.indexOfFirst { it is ChildModeKey }
        if (index < 0) return
        while (backStack.size > index) backStack.removeAt(backStack.lastIndex)
    }

    /** Pops one entry; returns false at the root so the caller can move the task to the back. Blocked on the child step screen. */
    fun back(): Boolean {
        if (childStepOnTop) return true
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    @Volatile private var externalReturnExpected = false

    /** The app is about to open system Settings (camera permission) and come back: the next stop does not lock. */
    fun expectExternalReturn() {
        externalReturnExpected = true
    }

    /** App went to the background: therapist mode needs the PIN again (FR-2). Setup in progress is kept. */
    fun onAppBackgrounded() {
        if (externalReturnExpected) {
            externalReturnExpected = false
            return
        }
        if (state.value == GateState.Ready && backStack.firstOrNull() == ShellKey) {
            backStack.clear()
            backStack.add(PinKey)
        }
    }
}
