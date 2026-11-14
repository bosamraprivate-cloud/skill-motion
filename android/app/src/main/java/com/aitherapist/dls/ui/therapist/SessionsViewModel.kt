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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sessions tab and Overview quick stats of one child: read-only, recomputed on every load. */
@HiltViewModel
class SessionsViewModel
internal constructor(
    private val history: SessionHistory,
    private val catalog: CatalogRepository,
    private val io: CoroutineDispatcher
) : ViewModel() {
    @Inject constructor(history: SessionHistory, catalog: CatalogRepository) : this(history, catalog, Dispatchers.IO)

    private val state = MutableStateFlow<SessionsUiState>(SessionsUiState.Loading)
    val uiState: StateFlow<SessionsUiState> = state.asStateFlow()

    private var childId: String? = null
    private var job: Job? = null

    /** Loads (again) for [childId]; old content stays visible while it reloads. */
    fun load(childId: String) {
        this.childId = childId
        job?.cancel()
        if (state.value !is SessionsUiState.Content) state.value = SessionsUiState.Loading
        job =
            viewModelScope.launch {
                state.value =
                    try {
                        withContext(io) {
                            val sessions = history.assessmentSessions(childId)
                            val rewards = history.rewards(childId)
                            val training = history.trainingResults(childId)
                            // Names only: a missing catalogue still lists the history with raw ids.
                            val cat = catalog.load().getOrNull()
                            SessionsUiState.Content(
                                buildSessionList(sessions, training, rewards, cat),
                                buildOverviewStats(sessions, rewards)
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception
                    ) {
                        SessionsUiState.Error(e::class.simpleName ?: "unknown") // type only, never child data
                    }
            }
    }

    fun retry() {
        childId?.let {
            state.value = SessionsUiState.Loading
            load(it)
        }
    }
}
