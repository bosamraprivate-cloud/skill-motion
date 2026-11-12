package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.BuildConfig
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

/** First-run setup: welcome, PIN twice, proxy URL and shared secret, then everything is saved at once. */
@HiltViewModel
class SetupViewModel
internal constructor(
    private val settings: TherapistSettings,
    private val io: CoroutineDispatcher,
    prefillUrl: String,
    prefillSecret: String
) : ViewModel() {
    // Debug builds prefill from local.properties; release BuildConfig values are empty.
    @Inject constructor(settings: TherapistSettings) :
        this(settings, Dispatchers.IO, BuildConfig.PROXY_URL, BuildConfig.SHARED_SECRET)

    private var last = SetupUiState.Content(proxyUrl = prefillUrl, secret = prefillSecret)
    private val state = MutableStateFlow<SetupUiState>(last)
    val uiState: StateFlow<SetupUiState> = state.asStateFlow()

    fun onEvent(event: SetupEvent) {
        if (event == SetupEvent.Retry) {
            if (state.value is SetupUiState.Error) state.value = last.copy(saving = false)
            return
        }
        val current = state.value as? SetupUiState.Content ?: return
        if (current.saving) return
        when (event) {
            is SetupEvent.PinChanged -> {
                val value = PinRules.sanitize(event.value)
                state.value =
                    when (current.step) {
                        SetupStep.PIN -> current.copy(pin = value, pinMismatch = false)
                        SetupStep.CONFIRM_PIN -> current.copy(confirmPin = value, pinMismatch = false)
                        else -> current
                    }
            }
            is SetupEvent.Digit -> if (event.digit in '0'..'9') onEvent(SetupEvent.PinChanged(activePin(current) + event.digit))
            SetupEvent.Delete -> onEvent(SetupEvent.PinChanged(activePin(current).dropLast(1)))
            is SetupEvent.ProxyUrlChanged -> state.value = current.copy(proxyUrl = event.value)
            is SetupEvent.SecretChanged -> state.value = current.copy(secret = event.value)
            SetupEvent.Continue -> onContinue(current)
            SetupEvent.Back -> state.value = back(current)
            SetupEvent.Retry -> Unit
        }
    }

    private fun activePin(current: SetupUiState.Content): String = if (current.step ==
        SetupStep.CONFIRM_PIN
    ) {
        current.confirmPin
    } else {
        current.pin
    }

    private fun onContinue(current: SetupUiState.Content) {
        if (!current.canContinue) return
        if (current.step == SetupStep.PROXY) {
            // Mark saving before launching: the save may finish (Done/Error) before launch returns.
            state.value = current.copy(saving = true)
            save(current)
            return
        }
        state.value =
            when (current.step) {
                SetupStep.WELCOME -> current.copy(step = SetupStep.PIN)
                SetupStep.PIN -> current.copy(step = SetupStep.CONFIRM_PIN, confirmPin = "")
                SetupStep.CONFIRM_PIN ->
                    if (current.confirmPin == current.pin) {
                        current.copy(step = SetupStep.PROXY)
                    } else {
                        current.copy(confirmPin = "", pinMismatch = true)
                    }
                SetupStep.PROXY -> current
            }
    }

    private fun back(current: SetupUiState.Content): SetupUiState.Content = when (current.step) {
        SetupStep.WELCOME -> current
        SetupStep.PIN -> current.copy(step = SetupStep.WELCOME, pin = "", pinMismatch = false)
        SetupStep.CONFIRM_PIN, SetupStep.PROXY -> current.copy(step = SetupStep.PIN, pin = "", confirmPin = "", pinMismatch = false)
    }

    private fun save(current: SetupUiState.Content) {
        last = current
        viewModelScope.launch {
            state.value =
                try {
                    withContext(io) {
                        settings.setProxyUrl(current.proxyUrl.trim())
                        settings.setSharedSecret(current.secret.trim())
                        // The PIN goes last: once it exists the app treats setup as complete.
                        settings.setPin(current.pin)
                    }
                    SetupUiState.Done
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    SetupUiState.Error(e::class.simpleName ?: "unknown")
                }
        }
    }
}
