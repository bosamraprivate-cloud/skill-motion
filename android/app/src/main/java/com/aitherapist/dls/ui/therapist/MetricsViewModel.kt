package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.data.MissingChildCodeException
import com.aitherapist.dls.data.ResultsRead
import com.aitherapist.dls.scoring.Metrics
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

sealed interface MetricsUiState {
    val volunteer: Boolean

    data class Loading(override val volunteer: Boolean) : MetricsUiState

    /** No attempt in the chosen sessions. */
    data class Empty(override val volunteer: Boolean) : MetricsUiState

    /** [excluded] counts sessions in neither population (not replay of the eval child, not a live assessment). */
    data class Content(override val volunteer: Boolean, val metrics: Metrics.Result, val excluded: Int = 0) : MetricsUiState

    data class Error(override val volunteer: Boolean, val reason: String) : MetricsUiState

    /** [count] sessions have no child code; nothing is computed until they are fixed. */
    data class MissingCodes(override val volunteer: Boolean, val count: Int) : MetricsUiState
}

/** Accuracy dashboard (Story 5.8): read-only; computes [Metrics] from the export rows in memory. */
@HiltViewModel
class MetricsViewModel
internal constructor(private val results: ResultsRead, private val io: CoroutineDispatcher) : ViewModel() {
    @Inject constructor(results: ResultsRead) : this(results, Dispatchers.IO)

    private val state = MutableStateFlow<MetricsUiState>(MetricsUiState.Loading(volunteer = true))
    val uiState: StateFlow<MetricsUiState> = state.asStateFlow()
    private var job: Job? = null

    init {
        load(volunteer = true)
    }

    fun select(volunteer: Boolean) {
        if (volunteer != state.value.volunteer) load(volunteer)
    }

    fun retry() = load(state.value.volunteer)

    private fun load(volunteer: Boolean) {
        job?.cancel()
        state.value = MetricsUiState.Loading(volunteer)
        job =
            viewModelScope.launch {
                state.value =
                    try {
                        val read = withContext(io) { results.metricsRows(volunteer) }
                        val metrics = withContext(io) { Metrics.compute(read.rows.map { Metrics.Row(it) }) }
                        if (metrics.attempts == 0) {
                            MetricsUiState.Empty(volunteer)
                        } else {
                            MetricsUiState.Content(volunteer, metrics, read.excluded)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: MissingChildCodeException) {
                        MetricsUiState.MissingCodes(volunteer, e.count)
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception
                    ) {
                        MetricsUiState.Error(volunteer, e.javaClass.simpleName)
                    }
            }
    }
}
