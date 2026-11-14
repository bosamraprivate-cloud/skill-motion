package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.data.SessionHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read-only summary of one assessment session (therapist mode); levels include saved corrections (made in Session detail). */
@HiltViewModel
class SessionSummaryViewModel
internal constructor(
    private val history: SessionHistory,
    private val catalog: CatalogRepository,
    private val io: CoroutineDispatcher
) : ViewModel() {
    @Inject constructor(history: SessionHistory, catalog: CatalogRepository) : this(history, catalog, Dispatchers.IO)

    private val state = MutableStateFlow<SessionSummaryUiState>(SessionSummaryUiState.Loading)
    val uiState: StateFlow<SessionSummaryUiState> = state.asStateFlow()

    private var started = false

    /**
     * Summarises the done sessions among [sessionIds] (this child-mode visit, or one Sessions row), in the given order.
     * Sessions of another child, replay sessions and the eval child are ignored.
     */
    fun load(childId: String, sessionIds: List<String>) {
        if (started) return
        started = true
        viewModelScope.launch {
            state.value =
                try {
                    withContext(io) {
                        val sessions = sessionIds.distinct().mapNotNull { id -> history.session(id)?.takeIf { it.childId == childId } }
                        val done = sessions.filter { historyState(it.state) == HistoryState.DONE }
                        when {
                            sessions.isEmpty() -> SessionSummaryUiState.Empty
                            done.isEmpty() -> SessionSummaryUiState.NotFinished
                            else -> {
                                val rewards = history.rewards(childId)
                                val cat = catalog.load().getOrNull()
                                SessionSummaryUiState.Content(
                                    done.map { buildSessionSummary(it, history.attempts(it.id), history.corrections(it.id), rewards, cat) }
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    SessionSummaryUiState.Error(e::class.simpleName ?: "unknown")
                }
        }
    }
}
