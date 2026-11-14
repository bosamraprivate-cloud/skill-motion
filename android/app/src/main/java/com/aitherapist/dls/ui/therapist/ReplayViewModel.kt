package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.replay.ClipInput
import com.aitherapist.dls.replay.ReplayRun
import com.aitherapist.dls.replay.ReplayService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Replay screen state: the shown run (latest stored, or the last import / re-score), a busy flag and an error code. */
data class ReplayUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val run: ReplayRun? = null,
    val error: String? = null,
    /** Front-camera recordings: flip frames before landmarks. */
    val mirrored: Boolean = false
)

sealed interface ReplayEvent {
    data class Import(val inputs: List<ClipInput>) : ReplayEvent

    data object Rescore : ReplayEvent

    data class SetMirrored(val mirrored: Boolean) : ReplayEvent

    /** Writes the latest replay session's CSV to the stream [open] gives (null: nothing written). */
    data class Export(val open: () -> java.io.OutputStream?) : ReplayEvent
}

/** Replay & evaluation (Story 3.7, debug builds only). Errors are shown by exception type name only. */
@HiltViewModel
class ReplayViewModel
internal constructor(private val service: ReplayService, private val io: CoroutineDispatcher) : ViewModel() {
    @Inject constructor(service: ReplayService) : this(service, Dispatchers.Default)

    private val state = MutableStateFlow(ReplayUiState())
    val uiState: StateFlow<ReplayUiState> = state.asStateFlow()

    init {
        run(initial = true) { service.latest() }
    }

    fun onEvent(event: ReplayEvent) {
        if (event is ReplayEvent.SetMirrored) {
            state.update { it.copy(mirrored = event.mirrored) }
            return
        }
        if (state.value.busy || state.value.loading) return
        val mirrored = state.value.mirrored
        when (event) {
            is ReplayEvent.Import -> if (event.inputs.isNotEmpty()) run { service.import(event.inputs, mirrored) }
            ReplayEvent.Rescore -> run { service.rescore(mirrored) }
            is ReplayEvent.SetMirrored -> Unit
            is ReplayEvent.Export -> export(event.open)
        }
    }

    private fun export(open: () -> java.io.OutputStream?) {
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(io) {
                    val csv = service.exportCsv() ?: return@withContext
                    open()?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                state.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                state.update { it.copy(busy = false, error = e::class.simpleName ?: "error") }
            }
        }
    }

    private fun run(initial: Boolean = false, block: suspend () -> ReplayRun) {
        state.update { it.copy(busy = !initial, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(io) { block() }
                state.update { it.copy(loading = false, busy = false, run = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                state.update { it.copy(loading = false, busy = false, error = e::class.simpleName ?: "error") }
            }
        }
    }
}
