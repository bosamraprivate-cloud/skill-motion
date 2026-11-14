package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.data.AssessmentResults
import com.aitherapist.dls.data.ChildProfiles
import com.aitherapist.dls.data.SessionHistory
import com.aitherapist.dls.data.SessionResult
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

/** Story 5.3: compare two finished assessments of the same task (read-only, recomputed on every load). */
@HiltViewModel
class CompareViewModel
internal constructor(
    private val results: AssessmentResults,
    private val catalog: CatalogRepository,
    private val children: ChildProfiles,
    private val io: CoroutineDispatcher
) : ViewModel() {
    @Inject constructor(results: AssessmentResults, catalog: CatalogRepository, children: ChildProfiles) :
        this(results, catalog, children, Dispatchers.IO)

    private val state = MutableStateFlow<CompareUiState>(CompareUiState.Loading)
    val uiState: StateFlow<CompareUiState> = state.asStateFlow()

    private var childId: String? = null
    private var job: Job? = null
    private var loaded: List<SessionResult> = emptyList()
    private var loadedCatalog: Catalog? = null

    fun load(childId: String) {
        this.childId = childId
        job?.cancel()
        val previous = state.value as? CompareUiState.Content
        if (previous == null) state.value = CompareUiState.Loading
        job =
            viewModelScope.launch {
                try {
                    val (rows, cat) = withContext(io) {
                        // The hidden eval child (or a missing one) has nothing to compare.
                        val listed = children.get(childId)?.isEval == false
                        (if (listed) results.results(childId) else emptyList()) to catalog.load().getOrThrow()
                    }
                    loaded = rows
                    loadedCatalog = cat
                    val sessions = comparableSessions(rows, cat)
                    state.value =
                        if (sessions.isEmpty()) {
                            CompareUiState.NotEnough
                        } else {
                            select(sessions, previous?.firstId?.takeIf { id -> sessions.any { it.id == id } }, previous?.secondId)
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.value = CompareUiState.Error(e::class.simpleName ?: "unknown") // type only, never child data
                }
            }
    }

    fun selectFirst(id: String) {
        val current = state.value as? CompareUiState.Content ?: return
        state.value = select(current.sessions, id, null)
    }

    fun selectSecond(id: String) {
        val current = state.value as? CompareUiState.Content ?: return
        state.value = select(current.sessions, current.firstId, id)
    }

    fun retry() {
        childId?.let {
            state.value = CompareUiState.Loading
            load(it)
        }
    }

    private fun select(sessions: List<CompareSession>, firstId: String?, secondId: String?): CompareUiState.Content {
        val choices = secondChoices(sessions, firstId)
        val second = secondId?.takeIf { id -> choices.any { it.id == id } }
        val cat = loadedCatalog
        val a = loaded.firstOrNull { it.session.id == firstId }
        val b = loaded.firstOrNull { it.session.id == second }
        val result = if (a != null && b != null && cat != null) compareResult(a, b, cat) else null
        return CompareUiState.Content(sessions, firstId, choices, second, result)
    }
}

/** Story 5.4: saved training results of the child, newest first (read-only). */
@HiltViewModel
class TrainingHistoryViewModel
internal constructor(
    private val history: SessionHistory,
    private val catalog: CatalogRepository,
    private val io: CoroutineDispatcher
) : ViewModel() {
    @Inject constructor(history: SessionHistory, catalog: CatalogRepository) : this(history, catalog, Dispatchers.IO)

    private val state = MutableStateFlow<TrainingHistoryUiState>(TrainingHistoryUiState.Loading)
    val uiState: StateFlow<TrainingHistoryUiState> = state.asStateFlow()

    private var childId: String? = null
    private var job: Job? = null

    fun load(childId: String) {
        this.childId = childId
        job?.cancel()
        if (state.value !is TrainingHistoryUiState.Content) state.value = TrainingHistoryUiState.Loading
        job =
            viewModelScope.launch {
                state.value =
                    try {
                        val rows =
                            withContext(io) {
                                val saved = history.trainingResults(childId)
                                // Titles are a nicety: an unreadable catalogue still lists the rows by activity id.
                                if (saved.isEmpty()) emptyList() else trainingRows(saved, catalog.load().getOrNull())
                            }
                        if (rows.isEmpty()) TrainingHistoryUiState.Empty else TrainingHistoryUiState.Content(rows)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        TrainingHistoryUiState.Error(e::class.simpleName ?: "unknown") // type only, never child data
                    }
            }
    }

    fun retry() {
        childId?.let {
            state.value = TrainingHistoryUiState.Loading
            load(it)
        }
    }
}
