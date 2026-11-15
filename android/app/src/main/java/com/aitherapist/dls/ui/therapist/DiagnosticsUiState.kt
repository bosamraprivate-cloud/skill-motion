package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.CannotTellCounts
import com.aitherapist.dls.vision.FpsRunResult
import com.aitherapist.dls.vision.InferenceDelegate
import com.aitherapist.dls.vision.LandmarkGroup
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.Outcome
import com.aitherapist.dls.vision.ProxyHealth

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
        /** Rolling whole-percent visibility per landmark group (Story 5.5). */
        val groups: Map<LandmarkGroup, Int> = emptyMap(),
        /** Width / height of the upright analysis frame; preview and overlay share a box of this aspect. */
        val frameAspect: Float,
        val posePoints: List<LandmarkPoint>,
        val handPoints: List<List<LandmarkPoint>>,
        val run: RunState,
        /** Debug-only spike attempt; null in release builds or when no spike step is bundled. */
        val attempt: AttemptState? = null,
        /** Debug-only spike step IDs the user can pick from. */
        val spikeSteps: List<String> = emptyList()
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

/** Proxy and data health, shown whatever the camera state (Story 5.5). */
data class HealthState(val proxy: ProxyCheck = ProxyCheck.Running, val rate: RateState = RateState.Loading)

/** Proxy health ping: runs on open and on "Check again" only. */
sealed interface ProxyCheck {
    data object Running : ProxyCheck

    data class Done(val health: ProxyHealth, val roundTripMs: Long) : ProxyCheck
}

/** Cannot-tell rate over saved assessment attempts (verifier `none` excluded). */
sealed interface RateState {
    data object Loading : RateState

    data class Ready(val counts: CannotTellCounts) : RateState

    data object Unavailable : RateState
}

sealed interface DiagnosticsEvent {
    data object TryStep : DiagnosticsEvent

    data class SelectStep(val stepId: String) : DiagnosticsEvent

    /** Re-runs the proxy ping and reloads the cannot-tell rate. */
    data object CheckAgain : DiagnosticsEvent

    data class PermissionResult(val granted: Boolean) : DiagnosticsEvent

    data object StartRun : DiagnosticsEvent

    data object Retry : DiagnosticsEvent
}
