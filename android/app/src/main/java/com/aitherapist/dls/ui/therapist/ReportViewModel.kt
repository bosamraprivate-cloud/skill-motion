package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.data.AssessmentResults
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

/** Functional report of one child (Story 4.2): recomputed from the latest finished assessment per task on every load. */
@HiltViewModel
class ReportViewModel
internal constructor(
    private val results: AssessmentResults,
    private val catalog: CatalogRepository,
    private val io: CoroutineDispatcher
) : ViewModel() {
    @Inject constructor(results: AssessmentResults, catalog: CatalogRepository) : this(results, catalog, Dispatchers.IO)

    private val state = MutableStateFlow<ReportUiState>(ReportUiState.Loading)
    val uiState: StateFlow<ReportUiState> = state.asStateFlow()

    private var childId: String? = null
    private var job: Job? = null

    /** Loads (again) for [childId]; called each time the Report tab opens, so the report is never stale. */
    fun load(childId: String) {
        this.childId = childId
        job?.cancel()
        if (state.value !is ReportUiState.Content) state.value = ReportUiState.Loading
        job =
            viewModelScope.launch {
                state.value =
                    try {
                        val tasks =
                            withContext(io) {
                                val latest = results.latestResults(childId)
                                if (latest.isEmpty()) emptyList() else buildReport(latest, catalog.load().getOrThrow())
                            }
                        if (tasks.isEmpty()) ReportUiState.Empty else ReportUiState.Content(tasks)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ReportUiState.Error(e::class.simpleName ?: "unknown") // type only, never child data
                    }
            }
    }

    fun retry() {
        childId?.let {
            state.value = ReportUiState.Loading
            load(it)
        }
    }
}
