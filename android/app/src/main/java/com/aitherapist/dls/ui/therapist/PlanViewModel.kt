package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.data.AssessmentResults
import com.aitherapist.dls.data.PlanDraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Treatment plan and activity picker (Story 4.3); recomputed on every load. The draft selection is saved per child
 * in [PlanDraftStore] (ids only), so it survives rotation, the PIN lock and process death; it is cleared on start.
 */
@HiltViewModel
class PlanViewModel
internal constructor(
    private val results: AssessmentResults,
    private val catalog: CatalogRepository,
    private val drafts: PlanDraftStore,
    private val io: CoroutineDispatcher
) : ViewModel() {
    @Inject constructor(results: AssessmentResults, catalog: CatalogRepository, drafts: PlanDraftStore) :
        this(results, catalog, drafts, Dispatchers.IO)

    private val state = MutableStateFlow<PlanUiState>(PlanUiState.Loading)
    val uiState: StateFlow<PlanUiState> = state.asStateFlow()

    private var childId: String? = null
    private var job: Job? = null

    /** Loads (again) for [childId]; ids no longer in the plan are dropped from the draft. */
    fun load(childId: String) {
        this.childId = childId
        job?.cancel()
        val previous = state.value as? PlanUiState.Content
        if (previous == null) state.value = PlanUiState.Loading
        job =
            viewModelScope.launch {
                state.value =
                    try {
                        withContext(io) {
                            val latest = results.latestResults(childId)
                            val sections = if (latest.isEmpty()) emptyList() else buildPlan(latest, catalog.load().getOrThrow())
                            when {
                                latest.isEmpty() -> PlanUiState.Empty
                                sections.isEmpty() -> PlanUiState.AllStrengths
                                else -> {
                                    val ids = sections.flatMap { s -> s.activities.map { it.id } }.toSet()
                                    val saved = drafts.draft(childId)
                                    val kept = saved.filter { it in ids }
                                    if (kept.size != saved.size) drafts.setDraft(childId, kept)
                                    PlanUiState.Content(sections, kept.toSet())
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        previous?.copy(reloadError = true) ?: PlanUiState.Error(e::class.simpleName ?: "unknown") // type only
                    }
            }
    }

    /** Checks or unchecks [activityId] (one check per activity) and saves the draft. */
    fun toggle(activityId: String) {
        val content = state.value as? PlanUiState.Content ?: return
        val id = childId ?: return
        val selected = if (activityId in content.selected) content.selected - activityId else content.selected + activityId
        val next = content.copy(selected = selected)
        state.value = next
        viewModelScope.launch { withContext(io) { drafts.setDraft(id, next.selectedInOrder) } }
    }

    /** Training started: the draft is cleared. */
    fun onTrainingStarted() {
        val id = childId ?: return
        (state.value as? PlanUiState.Content)?.let { state.value = it.copy(selected = emptySet()) }
        viewModelScope.launch { withContext(io) { drafts.setDraft(id, emptyList()) } }
    }

    fun retry() {
        childId?.let {
            val content = state.value as? PlanUiState.Content
            state.value = content?.copy(reloadError = false) ?: PlanUiState.Loading
            load(it)
        }
    }
}
