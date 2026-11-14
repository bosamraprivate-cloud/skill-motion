package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.data.ResultsExport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the results export is (Story 5.6). */
enum class ExportStatus { IDLE, WORKING, DONE, FAILED }

/** Settings -> Evaluation -> Export results: writes `results.csv` to the picked document. A cancelled picker writes nothing. */
@HiltViewModel
class ResultsExportViewModel
internal constructor(private val export: ResultsExport, private val io: CoroutineDispatcher) : ViewModel() {
    @Inject constructor(export: ResultsExport) : this(export, Dispatchers.IO)

    private val state = MutableStateFlow(ExportStatus.IDLE)
    val status: StateFlow<ExportStatus> = state.asStateFlow()

    /** [uri] is the picked document, or null when the picker was cancelled (nothing is written). */
    fun onPicked(uri: String?) {
        if (uri == null || state.value == ExportStatus.WORKING) return
        state.value = ExportStatus.WORKING
        viewModelScope.launch {
            state.value =
                try {
                    withContext(io) { export.exportTo(uri) }
                    ExportStatus.DONE
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    // OutOfMemoryError included: the therapist sees a calm message, never a crash.
                    @Suppress("TooGenericExceptionCaught") e: Throwable
                ) {
                    ExportStatus.FAILED
                }
        }
    }

    /** Leaving the screen clears a DONE or FAILED message. */
    fun reset() {
        if (state.value != ExportStatus.WORKING) state.value = ExportStatus.IDLE
    }
}
