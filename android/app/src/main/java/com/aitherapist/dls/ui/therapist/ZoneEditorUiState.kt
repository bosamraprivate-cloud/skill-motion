package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.Zone

/** Zone names the therapist can draw; they match the `inZone` zone names allowed by the check schema. */
val ZONE_NAMES: List<String> = listOf("mat", "sink", "place")

sealed interface ZoneEditorUiState {
    data object Loading : ZoneEditorUiState

    data class Content(
        val zones: Map<String, Zone> = emptyMap(),
        val selected: String = ZONE_NAMES.first(),
        val tooSmall: Boolean = false,
        val saved: Boolean = false,
        /** The last save failed; [zones] still holds the drawn zones so the save can be retried. */
        val saveFailed: Boolean = false,
        val busy: Boolean = false
    ) : ZoneEditorUiState

    data class Error(val reason: String) : ZoneEditorUiState
}

sealed interface ZoneEditorEvent {
    data class Select(val name: String) : ZoneEditorEvent

    /** A finished drag on the mirrored preview, in normalised display coordinates. */
    data class Drawn(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : ZoneEditorEvent

    data class Clear(val name: String) : ZoneEditorEvent

    data object Retry : ZoneEditorEvent

    /** Retries saving the zones after a failed save. */
    data object RetrySave : ZoneEditorEvent
}
