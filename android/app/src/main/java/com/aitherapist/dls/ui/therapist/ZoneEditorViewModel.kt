package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.data.Zone
import com.aitherapist.dls.data.ZoneStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Draw, redraw and clear setup zones; each change is saved at once (normalised, un-mirrored). */
@HiltViewModel
class ZoneEditorViewModel internal constructor(private val store: ZoneStore, private val io: CoroutineDispatcher) : ViewModel() {
    @Inject constructor(store: ZoneStore) : this(store, Dispatchers.IO)

    private val state = MutableStateFlow<ZoneEditorUiState>(ZoneEditorUiState.Loading)
    val uiState: StateFlow<ZoneEditorUiState> = state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: ZoneEditorEvent) {
        if (event == ZoneEditorEvent.Retry) {
            if (state.value is ZoneEditorUiState.Error) load()
            return
        }
        val current = state.value as? ZoneEditorUiState.Content ?: return
        when (event) {
            is ZoneEditorEvent.Select ->
                if (event.name in ZONE_NAMES) edit { it.copy(selected = event.name, tooSmall = false, saved = false) }
            is ZoneEditorEvent.Drawn -> {
                if (current.busy) return
                val zone = ZoneMath.fromDisplay(event.x1, event.y1, event.x2, event.y2)
                if (zone ==
                    null
                ) {
                    edit { it.copy(tooSmall = true, saved = false, saveFailed = false) }
                } else {
                    save(
                        current.zones + (current.selected to zone)
                    )
                }
            }
            is ZoneEditorEvent.Clear -> if (!current.busy && event.name in current.zones) save(current.zones - event.name)
            ZoneEditorEvent.RetrySave -> if (!current.busy && current.saveFailed) save(current.zones)
            ZoneEditorEvent.Retry -> Unit
        }
    }

    private fun edit(change: (ZoneEditorUiState.Content) -> ZoneEditorUiState.Content) {
        state.update { if (it is ZoneEditorUiState.Content) change(it) else it }
    }

    private fun load() {
        state.value = ZoneEditorUiState.Loading
        guarded {
            val zones = withContext(io) { store.zones() }.filterKeys { it in ZONE_NAMES }
            state.value = ZoneEditorUiState.Content(zones = zones)
        }
    }

    private fun save(zones: Map<String, Zone>) {
        // Show the drawn zones at once; on failure they stay so the save can be retried.
        edit { it.copy(zones = zones, busy = true, tooSmall = false, saved = false, saveFailed = false) }
        viewModelScope.launch {
            val ok =
                try {
                    withContext(io) { store.setZones(zones) }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    false
                }
            edit { it.copy(busy = false, saved = ok, saveFailed = !ok) }
        }
    }

    /** Never crash; the reason is the exception type only. */
    private fun guarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                state.value = ZoneEditorUiState.Error(e::class.simpleName ?: "unknown")
            }
        }
    }
}
