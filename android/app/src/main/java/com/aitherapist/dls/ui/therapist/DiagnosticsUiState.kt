package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.vision.FpsRunResult
import com.aitherapist.dls.vision.InferenceDelegate
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.Outcome

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
        val run: RunState,
        /** Debug-only spike attempt; null in release builds or when no spike step is bundled. */
        val attempt: AttemptState? = null,
        /** Debug-only spike step IDs the user can pick from. */
        val spikeSteps: List<String> = emptyList(),
        /** Debug-only proxy reachability probe (FR-1). */
        val probe: ProbeState? = null
    ) : DiagnosticsUiState

    data class Error(val reason: String) : DiagnosticsUiState
}

sealed interface RunState {
    data object Idle : RunState

    data class Running(val elapsedSec: Int, val totalSec: Int) : RunState

    data class Done(val result: FpsRunResult) : RunState
}

/** Debug-only local verdict attempt on the bundled spike step (Story 1.3, NFR-1). */
sealed interface AttemptState {
    val stepId: String

    data class Idle(override val stepId: String) : AttemptState

    data class Recording(override val stepId: String, val remainingSec: Int) : AttemptState

    data class Done(
        override val stepId: String,
        val outcome: Outcome,
        val reason: String,
        /** Wait after window close for the last frames to pair (AD-3). */
        val pairingWaitMs: Long,
        /** Verifier evaluation time; NFR-1 budget is pairingWaitMs + evaluationMs. */
        val evaluationMs: Long,
        val modelId: String? = null,
        val promptVersion: String? = null
    ) : AttemptState
}

/** Proxy reachability probe: reachable when `GET /verify` answered 405. */
sealed interface ProbeState {
    data object Running : ProbeState

    data class Done(val reachable: Boolean, val roundTripMs: Long, val detail: String) : ProbeState
}

sealed interface DiagnosticsEvent {
    data object TryStep : DiagnosticsEvent

    data class SelectStep(val stepId: String) : DiagnosticsEvent

    data object Probe : DiagnosticsEvent

    data class PermissionResult(val granted: Boolean) : DiagnosticsEvent

    data object StartRun : DiagnosticsEvent

    data object Retry : DiagnosticsEvent
}
