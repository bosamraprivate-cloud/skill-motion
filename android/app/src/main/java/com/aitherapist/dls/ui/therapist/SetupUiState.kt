package com.aitherapist.dls.ui.therapist

enum class SetupStep { WELCOME, PIN, CONFIRM_PIN, PROXY }

sealed interface SetupUiState {
    data class Content(
        val step: SetupStep = SetupStep.WELCOME,
        val pin: String = "",
        val confirmPin: String = "",
        val pinMismatch: Boolean = false,
        val proxyUrl: String = "",
        val secret: String = "",
        val saving: Boolean = false
    ) : SetupUiState {
        val proxyUrlValid: Boolean get() = isValidProxyUrl(proxyUrl)

        val canContinue: Boolean
            get() =
                when (step) {
                    SetupStep.WELCOME -> true
                    SetupStep.PIN -> PinRules.isValid(pin)
                    SetupStep.CONFIRM_PIN -> PinRules.isValid(confirmPin)
                    SetupStep.PROXY -> proxyUrlValid && secret.isNotBlank() && !saving
                }

        // PIN and secret are typed into masked fields; never print them.
        override fun toString(): String = "Content(step=$step, pinMismatch=$pinMismatch, saving=$saving)"
    }

    /** Setup is saved; the app moves on to the therapist shell. */
    data object Done : SetupUiState

    data class Error(val reason: String) : SetupUiState
}

sealed interface SetupEvent {
    data class PinChanged(val value: String) : SetupEvent

    data class Digit(val digit: Char) : SetupEvent

    data object Delete : SetupEvent

    data class ProxyUrlChanged(val value: String) : SetupEvent

    data class SecretChanged(val value: String) : SetupEvent

    data object Continue : SetupEvent

    data object Back : SetupEvent

    data object Retry : SetupEvent
}
