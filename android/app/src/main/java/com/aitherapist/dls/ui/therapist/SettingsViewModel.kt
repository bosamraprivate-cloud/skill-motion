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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Therapist settings: change the PIN (current + new twice), edit the proxy URL, replace the secret (never shown). */
@HiltViewModel
class SettingsViewModel
internal constructor(private val settings: TherapistSettings, private val io: CoroutineDispatcher) : ViewModel() {
    @Inject constructor(settings: TherapistSettings) : this(settings, Dispatchers.IO)

    private val state = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: SettingsEvent) {
        if (event == SettingsEvent.Retry) {
            if (state.value is SettingsUiState.Error) load()
            return
        }
        val current = state.value as? SettingsUiState.Content ?: return
        when (event) {
            is SettingsEvent.CurrentPinChanged -> edit {
                it.copy(currentPin = PinRules.sanitize(event.value), pinError = null, pinChanged = false)
            }
            is SettingsEvent.NewPinChanged -> edit { it.copy(newPin = PinRules.sanitize(event.value), pinError = null, pinChanged = false) }
            is SettingsEvent.ConfirmPinChanged -> edit {
                it.copy(confirmPin = PinRules.sanitize(event.value), pinError = null, pinChanged = false)
            }
            SettingsEvent.ChangePin -> if (current.canChangePin && PinRules.isValid(current.newPin)) changePin(current)
            is SettingsEvent.ProxyUrlChanged -> edit { it.copy(proxyUrl = event.value, proxySaved = false) }
            SettingsEvent.SaveProxyUrl -> if (current.canSaveProxy && isValidProxyUrl(current.proxyUrl)) saveProxy(current)
            is SettingsEvent.SecretChanged -> edit { it.copy(newSecret = event.value, secretSaved = false) }
            SettingsEvent.SaveSecret -> if (current.canSaveSecret) saveSecret(current)
            SettingsEvent.Retry -> Unit
        }
    }

    private fun edit(change: (SettingsUiState.Content) -> SettingsUiState.Content) {
        state.update { if (it is SettingsUiState.Content) change(it) else it }
    }

    private fun load() {
        state.value = SettingsUiState.Loading
        guarded {
            val (url, hasSecret) = withContext(io) { settings.proxyUrl().orEmpty() to settings.hasSharedSecret() }
            state.value = SettingsUiState.Content(proxyUrl = url, secretSet = hasSecret)
        }
    }

    private fun changePin(current: SettingsUiState.Content) {
        edit { it.copy(busy = true) }
        guarded {
            val error =
                withContext(io) {
                    when {
                        !settings.verifyPin(current.currentPin) -> PinChangeError.WRONG_CURRENT
                        current.newPin != current.confirmPin -> PinChangeError.MISMATCH
                        else -> {
                            settings.setPin(current.newPin)
                            null
                        }
                    }
                }
            edit {
                when (error) {
                    null -> it.copy(currentPin = "", newPin = "", confirmPin = "", pinError = null, pinChanged = true, busy = false)
                    PinChangeError.WRONG_CURRENT -> it.copy(currentPin = "", pinError = error, busy = false)
                    PinChangeError.MISMATCH -> it.copy(newPin = "", confirmPin = "", pinError = error, busy = false)
                }
            }
        }
    }

    private fun saveProxy(current: SettingsUiState.Content) {
        edit { it.copy(busy = true) }
        guarded {
            val url = current.proxyUrl.trim()
            withContext(io) { settings.setProxyUrl(url) }
            edit { it.copy(proxyUrl = url, proxySaved = true, busy = false) }
        }
    }

    private fun saveSecret(current: SettingsUiState.Content) {
        edit { it.copy(busy = true) }
        guarded {
            withContext(io) { settings.setSharedSecret(current.newSecret.trim()) }
            edit { it.copy(newSecret = "", secretSet = true, secretSaved = true, busy = false) }
        }
    }

    /** Never crash; the reason is the exception type only (messages may carry data). */
    private fun guarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                state.value = SettingsUiState.Error(e::class.simpleName ?: "unknown")
            }
        }
    }
}
