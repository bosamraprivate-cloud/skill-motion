package com.aitherapist.dls.ui.therapist

enum class PinChangeError { WRONG_CURRENT, MISMATCH }

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(
        val currentPin: String = "",
        val newPin: String = "",
        val confirmPin: String = "",
        val pinError: PinChangeError? = null,
        val pinChanged: Boolean = false,
        val proxyUrl: String = "",
        val proxySaved: Boolean = false,
        val secretSet: Boolean = false,
        val newSecret: String = "",
        val secretSaved: Boolean = false,
        val busy: Boolean = false
    ) : SettingsUiState {
        val proxyUrlValid: Boolean get() = isValidProxyUrl(proxyUrl)
        val canChangePin: Boolean
            get() = PinRules.isValid(currentPin) && PinRules.isValid(newPin) && PinRules.isValid(confirmPin) && !busy
        val canSaveProxy: Boolean get() = proxyUrlValid && !busy
        val canSaveSecret: Boolean get() = newSecret.isNotBlank() && !busy

        // PINs and the new secret are masked input; never print them.
        override fun toString(): String = "Content(pinError=$pinError, pinChanged=$pinChanged, secretSet=$secretSet, busy=$busy)"
    }

    data class Error(val reason: String) : SettingsUiState
}

sealed interface SettingsEvent {
    data class CurrentPinChanged(val value: String) : SettingsEvent

    data class NewPinChanged(val value: String) : SettingsEvent

    data class ConfirmPinChanged(val value: String) : SettingsEvent

    data object ChangePin : SettingsEvent

    data class ProxyUrlChanged(val value: String) : SettingsEvent

    data object SaveProxyUrl : SettingsEvent

    data class SecretChanged(val value: String) : SettingsEvent

    data object SaveSecret : SettingsEvent

    data object Retry : SettingsEvent
}
