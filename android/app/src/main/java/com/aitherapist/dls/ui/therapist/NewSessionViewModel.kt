package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.AssetPresence
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.TaskReadiness
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Take off, put on or both halves of a garment's take-off/put-on pair; entry order is the dialog order. */
enum class PairChoice { OFF, ON, BOTH }

/**
 * One pickable task. A task `<base>.on` whose sibling `<base>.off` exists becomes one option with [offId] set,
 * offering on, off or both (UX "New session dialog": jacket on/off/both). Ids come from the catalogue only.
 */
data class TaskOption(
    val id: String,
    val nameAr: String,
    val area: String,
    val offId: String? = null,
    val offNameAr: String? = null,
    /** Character variant id -> still of the task's first step (the tile picture); empty when media is missing. */
    val pictures: Map<String, String> = emptyMap(),
    /** Variants for which the (on) task has every step clip in the APK. */
    val readyVariants: Set<String> = emptySet(),
    /** Variants for which the paired off task has every step clip in the APK. */
    val offReadyVariants: Set<String> = emptySet()
) {
    val isPair: Boolean get() = offId != null

    private fun onReady(variant: String?) = if (variant == null) readyVariants.isNotEmpty() else variant in readyVariants

    private fun offReady(variant: String?) =
        offId != null && if (variant == null) offReadyVariants.isNotEmpty() else variant in offReadyVariants

    /** Selectable for [variant] (null = the child's character is unknown: any variant counts). */
    fun isReady(variant: String?): Boolean = onReady(variant) || offReady(variant)

    /** Whether [choice] can be started for [variant]; a single task only knows [PairChoice.ON]. */
    fun choiceReady(choice: PairChoice, variant: String?): Boolean = when (choice) {
        PairChoice.ON -> onReady(variant)
        PairChoice.OFF -> offReady(variant)
        PairChoice.BOTH -> onReady(variant) && offReady(variant)
    }

    /** First choice offered on selection: both halves when both are ready, else the ready half. */
    fun defaultChoice(variant: String?): PairChoice = when {
        !isPair -> PairChoice.ON
        choiceReady(PairChoice.BOTH, variant) -> PairChoice.BOTH
        onReady(variant) -> PairChoice.ON
        else -> PairChoice.OFF
    }

    /** The run(s) to start: the first task id and the task to chain after it completes, if any. */
    fun plan(choice: PairChoice): Pair<String, String?> {
        val off = offId ?: return id to null
        return when (choice) {
            PairChoice.ON -> id to null
            PairChoice.OFF -> off to null
            PairChoice.BOTH -> off to id // owners: take off first, then put on
        }
    }

    companion object {
        private const val ON_SUFFIX = ".on"
        private const val OFF_SUFFIX = ".off"

        /** Catalogue order; an off task paired with its on task is folded into the on task's option. */
        fun from(tasks: List<CatalogTask>, ready: (CatalogTask) -> Set<String> = { emptySet() }): List<TaskOption> {
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
                val pictures = firstStills(task)
                if (off != null) {
                    folded += off.id
                    options += TaskOption(task.id, task.nameAr, task.area, off.id, off.nameAr, pictures, ready(task), ready(off))
                } else {
                    options += TaskOption(task.id, task.nameAr, task.area, pictures = pictures, readyVariants = ready(task))
                }
            }
            return options.filterNot { it.id in folded }
        }

        private fun firstStills(task: CatalogTask): Map<String, String> =
            task.steps.firstOrNull()?.media.orEmpty().mapNotNull { (variant, media) -> media?.let { variant to it.still } }.toMap()
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

        /**
         * Options grouped by area for [variant], ready tasks first: areas with a ready task come before areas with
         * none (those still show, as "coming soon"), and inside an area ready tasks come first. Otherwise catalogue order.
         */
        fun byAreaReadyFirst(variant: String?): List<Pair<String, List<TaskOption>>> = byArea
            .map { (area, list) -> area to list.sortedBy { !it.isReady(variant) } }
            .sortedBy { (_, list) -> list.none { it.isReady(variant) } }
    }
}

sealed interface NewSessionEvent {
    /** Select [id]; refused when the task is not ready for the child's character [variant]. */
    data class Select(val id: String, val variant: String? = null) : NewSessionEvent

    data class Choose(val choice: PairChoice) : NewSessionEvent
}

/** Therapist "New session" picker (Story 2.16): one task from the catalogue, or jacket on/off/both. */
@HiltViewModel
class NewSessionViewModel
internal constructor(
    private val catalog: CatalogRepository,
    private val readiness: (CatalogTask) -> Set<String>,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    @Inject constructor(catalog: CatalogRepository, assets: AssetPresence) :
        this(catalog, { task -> TaskReadiness.readyVariants(task, assets) })

    private val state = MutableStateFlow<NewSessionUiState>(NewSessionUiState.Loading)
    val uiState: StateFlow<NewSessionUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value =
                try {
                    // Asset listing for readiness runs off the main thread (once; the presence checker caches it).
                    val loaded = catalog.load().getOrThrow()
                    NewSessionUiState.Content(withContext(io) { TaskOption.from(loaded.tasks, readiness) }, areaNames = loaded.areaNames)
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
                is NewSessionEvent.Select -> {
                    val option = s.options.firstOrNull { it.id == event.id }
                    if (option != null && option.isReady(event.variant)) {
                        s.copy(selectedId = event.id, choice = option.defaultChoice(event.variant))
                    } else {
                        s
                    }
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
