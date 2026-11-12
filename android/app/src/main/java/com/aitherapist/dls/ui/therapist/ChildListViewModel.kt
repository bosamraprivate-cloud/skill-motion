package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.data.ChildProfiles
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Children home: the visible (non-eval) children, filtered by a name search. */
@HiltViewModel
class ChildListViewModel
internal constructor(private val children: ChildProfiles, private val io: CoroutineDispatcher) : ViewModel() {
    @Inject constructor(children: ChildProfiles) : this(children, Dispatchers.IO)

    private val query = MutableStateFlow("")
    private val reload = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChildListUiState> =
        reload
            .flatMapLatest {
                val rows =
                    children
                        .observeChildren()
                        .map { list ->
                            list.filterNot { it.isEval }.map { ChildRow(it.id, it.name, it.ageYears, it.characterVariant) }
                        }
                        .flowOn(io)
                combine(rows, query) { all, q ->
                    if (all.isEmpty()) ChildListUiState.Empty else ChildListUiState.Content(q, filterChildren(all, q))
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
