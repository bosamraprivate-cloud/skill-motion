package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** PIN gate before therapist mode. A wrong PIN clears the field; no lockout and no hint. */
@HiltViewModel
class PinViewModel
internal constructor(private val settings: TherapistSettings, private val io: CoroutineDispatcher) : ViewModel() {
    @Inject constructor(settings: TherapistSettings) : this(settings, Dispatchers.IO)

    private val state = MutableStateFlow<PinUiState>(PinUiState.Content())
    val uiState: StateFlow<PinUiState> = state.asStateFlow()

    fun onEvent(event: PinEvent) {
        if (event == PinEvent.Retry) {
            if (state.value is PinUiState.Error) state.value = PinUiState.Content()
            return
        }
        val current = state.value as? PinUiState.Content ?: return
        if (current.checking) return
        when (event) {
            is PinEvent.Digit ->
                if (event.digit in '0'..'9' && current.entered.length < PinRules.MAX_LENGTH) {
                    state.value = current.copy(entered = current.entered + event.digit, wrong = false)
                }
            PinEvent.Delete -> state.value = current.copy(entered = current.entered.dropLast(1))
            PinEvent.Submit -> submit(current)
            PinEvent.Retry -> Unit
        }
    }

    private fun submit(current: PinUiState.Content) {
        if (!current.canSubmit) return
        state.value = current.copy(checking = true)
        viewModelScope.launch {
            state.value =
                try {
                    if (withContext(io) { settings.verifyPin(current.entered) }) {
                        PinUiState.Unlocked
                    } else {
                        PinUiState.Content(wrong = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    PinUiState.Error(e::class.simpleName ?: "unknown")
                }
        }
    }
}
