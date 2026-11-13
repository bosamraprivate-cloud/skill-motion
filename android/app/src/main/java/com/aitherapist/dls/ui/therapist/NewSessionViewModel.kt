package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogTask
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** On, off or both halves of a put-on/take-off task pair (the jacket). */
enum class PairChoice { ON, OFF, BOTH }

/**
 * One pickable task. A task `<base>.on` whose sibling `<base>.off` exists becomes one option with [offId] set,
 * offering on, off or both (UX "New session dialog": jacket on/off/both). Ids come from the catalogue only.
 */
data class TaskOption(val id: String, val nameAr: String, val area: String, val offId: String? = null, val offNameAr: String? = null) {
    val isPair: Boolean get() = offId != null

    /** The run(s) to start: the first task id and the task to chain after its walkthrough, if any. */
    fun plan(choice: PairChoice): Pair<String, String?> {
        val off = offId ?: return id to null
        return when (choice) {
            PairChoice.ON -> id to null
            PairChoice.OFF -> off to null
            PairChoice.BOTH -> id to off
        }
    }

    companion object {
        private const val ON_SUFFIX = ".on"
        private const val OFF_SUFFIX = ".off"

        /** Catalogue order; an off task paired with its on task is folded into the on task's option. */
        fun from(tasks: List<CatalogTask>): List<TaskOption> {
            val byId = tasks.associateBy { it.id }
            val folded = mutableSetOf<String>()
            val options = mutableListOf<TaskOption>()
            for (task in tasks) {
                val off = if (task.id.endsWith(ON_SUFFIX)) {
                    byId[task.id.removeSuffix(ON_SUFFIX) + OFF_SUFFIX]?.takeIf {
                        it.area == task.area
                    }
                } else {
                    null
                }
                if (off != null) {
                    folded += off.id
                    options += TaskOption(task.id, task.nameAr, task.area, off.id, off.nameAr)
                } else {
                    options += TaskOption(task.id, task.nameAr, task.area)
                }
            }
            return options.filterNot { it.id in folded }
        }
    }
}

sealed interface NewSessionUiState {
    data object Loading : NewSessionUiState

    data class Error(val reason: String) : NewSessionUiState

    data class Content(
        val options: List<TaskOption>,
        val selectedId: String? = null,
        val choice: PairChoice = PairChoice.ON,
        /** Area id -> Arabic area name from the catalogue. */
        val areaNames: Map<String, String> = emptyMap()
    ) : NewSessionUiState {
        val selected: TaskOption? get() = options.firstOrNull { it.id == selectedId }

        /** Options grouped by area, in catalogue order. */
        val byArea: List<Pair<String, List<TaskOption>>> get() = options.groupBy { it.area }.toList()
    }
}

sealed interface NewSessionEvent {
    data class Select(val id: String) : NewSessionEvent

    data class Choose(val choice: PairChoice) : NewSessionEvent
}

/** Therapist "New session" picker (Story 2.16): one task from the catalogue, or jacket on/off/both. */
@HiltViewModel
class NewSessionViewModel
@Inject
constructor(private val catalog: CatalogRepository) : ViewModel() {
    private val state = MutableStateFlow<NewSessionUiState>(NewSessionUiState.Loading)
    val uiState: StateFlow<NewSessionUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value =
                try {
                    val loaded = catalog.load().getOrThrow()
                    NewSessionUiState.Content(TaskOption.from(loaded.tasks), areaNames = loaded.areaNames)
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    NewSessionUiState.Error(e::class.simpleName ?: "unknown") // type only, no data
                }
        }
    }

    fun onEvent(event: NewSessionEvent) {
        state.update { s ->
            if (s !is NewSessionUiState.Content) return@update s
            when (event) {
                is NewSessionEvent.Select -> if (s.options.any {
                        it.id == event.id
                    }
                ) {
                    s.copy(selectedId = event.id, choice = PairChoice.ON)
                } else {
                    s
                }
                is NewSessionEvent.Choose -> s.copy(choice = event.choice)
            }
        }
    }

    /** The first task and the chained one for the current selection, or null when nothing is selected. */
    fun plan(): Pair<String, String?>? {
        val s = state.value as? NewSessionUiState.Content ?: return null
        return s.selected?.plan(s.choice)
    }
}
