package com.aitherapist.dls.ui.therapist

sealed interface PinUiState {
    data class Content(val entered: String = "", val wrong: Boolean = false, val checking: Boolean = false) : PinUiState {
        val canSubmit: Boolean get() = PinRules.isValid(entered) && !checking

        override fun toString(): String = "Content(length=${entered.length}, wrong=$wrong, checking=$checking)"
    }

    /** The PIN was right; therapist mode opens. */
    data object Unlocked : PinUiState

    data class Error(val reason: String) : PinUiState
}

sealed interface PinEvent {
    data class Digit(val digit: Char) : PinEvent

    data object Delete : PinEvent

    data object Submit : PinEvent

    data object Retry : PinEvent
}
