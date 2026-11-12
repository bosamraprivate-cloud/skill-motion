package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.vision.FpsRunResult
import com.aitherapist.dls.vision.InferenceDelegate
import com.aitherapist.dls.vision.LandmarkPoint

/** Diagnostics screen state (AD-13). */
sealed interface DiagnosticsUiState {
    data object Loading : DiagnosticsUiState

    /** Camera permission not granted yet; the screen asks for it. */
    data object PermissionRequired : DiagnosticsUiState

    data object PermissionDenied : DiagnosticsUiState

    data class Content(
        val delegate: InferenceDelegate,
        val poseFps: Double,
        val handFps: Double,
        val combinedFps: Double,
        val visiblePercent: Int,
        /** Width / height of the upright analysis frame; preview and overlay share a box of this aspect. */
        val frameAspect: Float,
        val posePoints: List<LandmarkPoint>,
        val handPoints: List<List<LandmarkPoint>>,
        val run: RunState
    ) : DiagnosticsUiState

    data class Error(val reason: String) : DiagnosticsUiState
}

sealed interface RunState {
    data object Idle : RunState

    data class Running(val elapsedSec: Int, val totalSec: Int) : RunState

    data class Done(val result: FpsRunResult) : RunState
}

sealed interface DiagnosticsEvent {
    data class PermissionResult(val granted: Boolean) : DiagnosticsEvent

    data object StartRun : DiagnosticsEvent

    data object Retry : DiagnosticsEvent
}
