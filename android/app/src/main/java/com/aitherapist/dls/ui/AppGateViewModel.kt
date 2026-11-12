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

    /** Pops one entry; returns false at the root so the caller can move the task to the back. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** App went to the background: therapist mode needs the PIN again (FR-2). Setup in progress is kept. */
    fun onAppBackgrounded() {
        if (state.value == GateState.Ready && backStack.firstOrNull() == ShellKey) {
            backStack.clear()
            backStack.add(PinKey)
        }
    }
}
