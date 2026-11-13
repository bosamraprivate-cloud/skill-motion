package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.data.ChildProfiles
import com.aitherapist.dls.data.ResumableSessions
import com.aitherapist.dls.data.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Children home: the visible (non-eval) children, filtered by a name search. */
@HiltViewModel
class ChildListViewModel
internal constructor(
    private val children: ChildProfiles,
    private val io: CoroutineDispatcher,
    private val sessions: ResumableSessions = NoResumableSessions,
    private val catalog: CatalogRepository? = null
) : ViewModel() {
    @Inject constructor(children: ChildProfiles, sessions: ResumableSessions, catalog: CatalogRepository) :
        this(children, Dispatchers.IO, sessions, catalog)

    /** Task id -> Arabic task name for the Continue cards; empty (ids shown) when the catalogue cannot load. */
    private val taskNames: Flow<Map<String, String>> =
        flow {
            emit(catalog?.load()?.getOrNull()?.tasks?.associate { it.id to it.nameAr }.orEmpty())
        }.catch { emit(emptyMap()) }

    private val query = MutableStateFlow("")
    private val reload = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChildListUiState> =
        reload
            .flatMapLatest {
                val visible = children.observeChildren().map { list -> list.filterNot { it.isEval } }.flowOn(io)
                // A failing session query only hides the Continue cards; the children still show.
                val resumable = sessions.observeResumable().flowOn(io).catch { emit(emptyList()) }
                combine(visible, resumable, query, taskNames) { kids, open, q, names ->
                    if (kids.isEmpty()) return@combine ChildListUiState.Empty
                    val rows = kids.map { ChildRow(it.id, it.name, it.ageYears, it.characterVariant) }
                    // One card per child (newest session); eval child and deleted children never get a card.
                    val byId = kids.associateBy { it.id }
                    val cards =
                        open
                            .distinctBy { it.childId }
                            .mapNotNull { s ->
                                byId[s.childId]?.let {
                                    ContinueRow(
                                        s.id,
                                        it.id,
                                        it.name,
                                        s.taskId,
                                        names[s.taskId] ?: s.taskId
                                    )
                                }
                            }
                    val term = q.trim()
                    ChildListUiState.Content(q, filterChildren(rows, q), cards.filter { it.childName.contains(term, ignoreCase = true) })
                }.catch { e -> emit(ChildListUiState.Error(e::class.simpleName ?: "unknown")) } // type only, no data
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChildListUiState.Loading)

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun retry() {
        reload.value++
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** No interrupted sessions (unit tests that do not care about Continue cards). */
internal object NoResumableSessions : ResumableSessions {
    override fun observeResumable(): Flow<List<SessionEntity>> = flowOf(emptyList())

    override suspend fun session(id: String): SessionEntity? = null
}
