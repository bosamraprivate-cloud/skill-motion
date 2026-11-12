package com.aitherapist.dls.ui.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class SampleViewModel
@Inject
constructor(private val repository: SampleRepository) : ViewModel() {
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SampleUiState> =
        attempt
            .flatMapLatest {
                repository
                    .items()
                    .map<List<String>, SampleUiState> { items ->
                        if (items.isEmpty()) SampleUiState.Empty else SampleUiState.Content(items)
                    }.onStart { emit(SampleUiState.Loading) }
                    // Never crash. The reason is the exception type only, never its message (may carry data).
                    .catch { error -> emit(SampleUiState.Error(error::class.simpleName ?: "unknown")) }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SampleUiState.Loading
            )

    fun onEvent(event: SampleEvent) {
        when (event) {
            SampleEvent.Retry -> attempt.update { it + 1 }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
