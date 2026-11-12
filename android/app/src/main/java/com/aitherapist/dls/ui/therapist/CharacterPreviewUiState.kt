package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.CharacterVariant
import com.aitherapist.dls.catalog.ReactionClip
import com.aitherapist.dls.catalog.StepClip

/** Character preview screen state (AD-13). */
sealed interface CharacterPreviewUiState {
    data object Loading : CharacterPreviewUiState

    data object Empty : CharacterPreviewUiState

    data class Content(
        val variants: List<String>,
        val variant: CharacterVariant,
        /** Selected eating task ID, or null when the variant has no step clips. */
        val taskId: String?,
        val selection: ClipSelection?,
        val stillFrames: Boolean = false,
        /** The selected clip's asset failed to load. */
        val clipFailed: Boolean = false
    ) : CharacterPreviewUiState {
        val steps: List<StepClip> get() = variant.tasks.firstOrNull { it.id == taskId }?.steps.orEmpty()
    }

    data class Error(val reason: String) : CharacterPreviewUiState
}

sealed interface ClipSelection {
    data class Reaction(val clip: ReactionClip) : ClipSelection

    data class Step(val clip: StepClip) : ClipSelection
}

sealed interface CharacterPreviewEvent {
    data class SelectVariant(val id: String) : CharacterPreviewEvent

    data class SelectReaction(val state: String) : CharacterPreviewEvent

    data class SelectTask(val id: String) : CharacterPreviewEvent

    data class SelectStep(val id: String) : CharacterPreviewEvent

    data class SetStillFrames(val on: Boolean) : CharacterPreviewEvent

    data object ClipFailed : CharacterPreviewEvent

    data object Retry : CharacterPreviewEvent
}
