package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.data.ChildEntity
import com.aitherapist.dls.data.ChildProfiles
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Therapist "Try step screen" picker (Story 2.14): one child and one task from the catalogue. */
sealed interface TryStepUiState {
    data object Loading : TryStepUiState

    data class Error(val reason: String) : TryStepUiState

    data class Content(val children: List<ChildEntity>, val taskIds: List<String>, val childId: String?, val taskId: String?) :
        TryStepUiState {
        val canStart: Boolean get() = childId != null && taskId != null
    }
}

sealed interface TryStepEvent {
    data class SelectChild(val id: String) : TryStepEvent

    data class SelectTask(val id: String) : TryStepEvent
}

@HiltViewModel
class TryStepViewModel
@Inject
constructor(private val children: ChildProfiles, private val catalog: CatalogRepository) : ViewModel() {
    private val state = MutableStateFlow<TryStepUiState>(TryStepUiState.Loading)
    val uiState: StateFlow<TryStepUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value =
                try {
                    val kids = children.observeChildren().first()
                    val tasks = catalog.load().getOrThrow().tasks.map { it.id }
                    TryStepUiState.Content(kids, tasks, kids.firstOrNull()?.id, tasks.firstOrNull())
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    TryStepUiState.Error(e::class.simpleName ?: "unknown")
                }
        }
    }

    fun onEvent(event: TryStepEvent) {
        state.update { s ->
            if (s !is TryStepUiState.Content) return@update s
            when (event) {
                is TryStepEvent.SelectChild -> if (s.children.any { it.id == event.id }) s.copy(childId = event.id) else s
                is TryStepEvent.SelectTask -> if (event.id in s.taskIds) s.copy(taskId = event.id) else s
            }
        }
    }
}
