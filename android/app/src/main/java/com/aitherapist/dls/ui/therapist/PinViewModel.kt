package com.aitherapist.dls.ui.therapist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.data.PinAttempts
import com.aitherapist.dls.data.TherapistSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PIN gate before therapist mode. A wrong PIN clears the field, no hint. After [MAX_FAILURES] wrong PINs in a row the
 * pad waits [LOCKOUT_MS] with a calm countdown; the end time is persisted so killing the app does not skip the wait.
 * A right PIN resets the counter.
 */
@HiltViewModel
class PinViewModel
internal constructor(
    private val settings: TherapistSettings,
    private val io: CoroutineDispatcher,
    /** Wall-clock ms (the lockout end is persisted, so it must survive a restart). */
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    @Inject constructor(settings: TherapistSettings) : this(settings, Dispatchers.IO)

    private val state = MutableStateFlow<PinUiState>(PinUiState.Content())
    val uiState: StateFlow<PinUiState> = state.asStateFlow()
    private var countdown: Job? = null

    init {
        viewModelScope.launch {
            val until = try {
                withContext(io) { settings.pinAttempts() }.lockedUntil
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                0L
            }
            startCountdown(until)
        }
    }

    fun onEvent(event: PinEvent) {
        if (event == PinEvent.Retry) {
            if (state.value is PinUiState.Error) state.value = PinUiState.Content()
            return
        }
        val current = state.value as? PinUiState.Content ?: return
        if (current.checking || current.locked) return
        when (event) {
            is PinEvent.Digit ->
                if (event.digit in '0'..'9' && current.entered.length < PinRules.MAX_LENGTH) {
                    state.value = current.copy(entered = current.entered + event.digit, wrong = false)
                }
            PinEvent.Delete -> state.value = current.copy(entered = current.entered.dropLast(1))
            PinEvent.Submit -> submit(current)
            PinEvent.Retry -> Unit
        }
    }

    private fun submit(current: PinUiState.Content) {
        if (!current.canSubmit) return
        state.value = current.copy(checking = true)
        viewModelScope.launch {
            try {
                if (withContext(io) { settings.verifyPin(current.entered) }) {
                    withContext(io) { settings.setPinAttempts(PinAttempts()) }
                    state.value = PinUiState.Unlocked
                } else {
                    val failures = withContext(io) { settings.pinAttempts() }.failures + 1
                    if (failures >= MAX_FAILURES) {
                        val until = clock() + LOCKOUT_MS
                        withContext(io) { settings.setPinAttempts(PinAttempts(0, until)) }
                        state.value = PinUiState.Content(wrong = true)
                        startCountdown(until)
                    } else {
                        withContext(io) { settings.setPinAttempts(PinAttempts(failures, 0L)) }
                        state.value = PinUiState.Content(wrong = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                state.value = PinUiState.Error(e::class.simpleName ?: "unknown")
            }
        }
    }

    /** Ticks the countdown to [until]; a clock set back never stretches the wait past [LOCKOUT_MS]. */
    private fun startCountdown(until: Long) {
        countdown?.cancel()
        if (until <= clock()) return
        countdown = viewModelScope.launch {
            val end = minOf(until, clock() + LOCKOUT_MS)
            while (true) {
                val left = end - clock()
                if (left <= 0) break
                val seconds = ((left + TICK_MS - 1) / TICK_MS).toInt()
                (state.value as? PinUiState.Content)?.let { state.value = it.copy(entered = "", lockedSeconds = seconds) }
                delay(minOf(left, TICK_MS))
            }
            (state.value as? PinUiState.Content)?.let { state.value = it.copy(lockedSeconds = 0, wrong = false) }
        }
    }

    internal companion object {
        const val MAX_FAILURES = 5
        const val LOCKOUT_MS = 30_000L
        private const val TICK_MS = 1_000L
    }
}
