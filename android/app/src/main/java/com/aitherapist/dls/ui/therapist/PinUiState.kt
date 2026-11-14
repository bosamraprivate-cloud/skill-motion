package com.aitherapist.dls.ui.therapist

sealed interface PinUiState {
    /** [lockedSeconds] > 0: too many wrong PINs; the pad waits that many seconds (calm countdown). */
    data class Content(val entered: String = "", val wrong: Boolean = false, val checking: Boolean = false, val lockedSeconds: Int = 0) :
        PinUiState {
        val locked: Boolean get() = lockedSeconds > 0

        val canSubmit: Boolean get() = PinRules.isValid(entered) && !checking && !locked

        override fun toString(): String =
            "Content(length=${entered.length}, wrong=$wrong, checking=$checking, lockedSeconds=$lockedSeconds)"
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
