package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.EffectLevel

/** What finished the form, for the shell to react (select the child, or close the detail pane). */
sealed interface ProfileResult {
    data class Saved(val childId: String) : ProfileResult

    data object Deleted : ProfileResult
}

/** Transient notice under the form after a failed write; the typed input is kept. */
enum class ProfileMessage { SAVE_FAILED, DELETE_FAILED }

sealed interface ChildProfileUiState {
    data object Loading : ChildProfileUiState

    /** Terminal: the child was deleted; no form and no Save. */
    data object Deleted : ChildProfileUiState

    /** [childId] null = adding a new child. [confirmDelete] shows the dialog naming the child. */
    data class Content(
        val childId: String?,
        val form: ChildForm = ChildForm(),
        val errors: ChildFormErrors = ChildFormErrors(),
        val busy: Boolean = false,
        val confirmDelete: Boolean = false,
        val saved: Boolean = false,
        val message: ProfileMessage? = null,
        val result: ProfileResult? = null
    ) : ChildProfileUiState {
        val isNew: Boolean get() = childId == null
    }

    data class Error(val reason: String) : ChildProfileUiState
}

sealed interface ChildProfileEvent {
    data class NameChanged(val value: String) : ChildProfileEvent

    data class CodeChanged(val value: String) : ChildProfileEvent

    data class AgeChanged(val value: String) : ChildProfileEvent

    data class CharacterChanged(val value: ChildCharacter) : ChildProfileEvent

    data class EffectChanged(val value: EffectLevel) : ChildProfileEvent

    data object Save : ChildProfileEvent

    data object RequestDelete : ChildProfileEvent

    data object CancelDelete : ChildProfileEvent

    data object ConfirmDelete : ChildProfileEvent

    data object Retry : ChildProfileEvent
}
