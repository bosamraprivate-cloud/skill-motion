package com.aitherapist.dls.ui.sample

/** Reference state shape for every screen (AD-13). */
sealed interface SampleUiState {
    data object Loading : SampleUiState

    data object Empty : SampleUiState

    data class Content(val items: List<String>) : SampleUiState

    data class Error(val reason: String) : SampleUiState
}

sealed interface SampleEvent {
    data object Retry : SampleEvent
}
