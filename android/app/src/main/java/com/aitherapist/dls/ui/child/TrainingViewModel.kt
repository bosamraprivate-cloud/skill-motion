package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.audio.AudioPlayer
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.Landmarker
import com.aitherapist.dls.data.TrainingResultStore
import com.aitherapist.dls.rewards.RewardRecorder
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.training.TrainingRunner
import com.aitherapist.dls.training.TrainingState
import com.aitherapist.dls.vision.AttemptCapture
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Training screen state (Story 4.6); child mode never shows a spinner, so Loading is a blank calm canvas. */
sealed interface TrainingUiState {
    data object Loading : TrainingUiState

    /** The catalogue failed or no picked activity exists: a calm message and the adult way out. */
    data object Empty : TrainingUiState

    /** [doneReady] is false while the instruction plays (or the 1.5 s fallback runs). */
    data class Content(val training: TrainingState, val doneReady: Boolean) : TrainingUiState
}

/**
 * Drives one training run (Story 4.6): builds the [TrainingRunner] over the picked activities in order, feeds camera
 * landmarks for counted activities (camera only while a counted activity runs unpaused), ticks the timer, plays the
 * instruction audio, and finishes an activity at the criterion or on Done.
 */
@HiltViewModel
class TrainingViewModel
internal constructor(
    private val catalog: CatalogRepository,
    private val results: TrainingResultStore,
    private val rewards: RewardRecorder,
    private val audio: AudioPlayer,
    private val clock: Clock,
    private val capture: AttemptCapture,
    private val keys: ChildModeKeys,
    /** Warning sink (exception type only, never child data); injectable so JVM tests never touch android.util.Log. */
    private val warn: (String) -> Unit
) : ViewModel() {
    @Inject constructor(
        catalog: CatalogRepository,
        results: TrainingResultStore,
        rewards: RewardRecorder,
        audio: AudioPlayer,
        clock: Clock,
        capture: AttemptCapture,
        keys: ChildModeKeys
    ) : this(catalog, results, rewards, audio, clock, capture, keys, { Log.w(TAG, it) })

    private val state = MutableStateFlow<TrainingUiState>(TrainingUiState.Loading)
    val uiState: StateFlow<TrainingUiState> = state.asStateFlow()
    private val camera = MutableStateFlow(false)
    val cameraWanted: StateFlow<Boolean> = camera.asStateFlow()
    private val sheet = MutableStateFlow(false)
    val adultSheet: StateFlow<Boolean> = sheet.asStateFlow()
    private val cameraFailed = MutableStateFlow(false)
    val cameraUnavailable: StateFlow<Boolean> = cameraFailed.asStateFlow()

    private var runner: TrainingRunner? = null
    private var started = false
    private var active = true
    private var doneReady = false
    private var promptedIndex = -1
    private var finishing = false
    private var promptJob: Job? = null
    private var audioPlaying = false

    /** [runId] is stable for this run (kept in the nav key) so result ids survive a restore. */
    fun start(childId: String, activityIds: List<String>, runId: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            val loaded =
                try {
                    catalog.load().getOrThrow()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    state.value = TrainingUiState.Empty
                    return@launch
                }
            val byId = loaded.activities.associateBy { it.id }
            val picked = activityIds.mapNotNull { byId[it] }
            if (picked.isEmpty()) {
                state.value = TrainingUiState.Empty
                return@launch
            }
            val r = TrainingRunner(childId, picked, results, rewards, clock::nowMs, runId)
            runner = r
            try {
                r.start()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                warn("training restore failed: ${e::class.simpleName}")
                state.value = TrainingUiState.Empty
                return@launch
            }
            launch { r.current.collect(::onState) }
            launch {
                capture.samples.collect { s ->
                    s.frame.image?.release()
                    r.feed(s.landmarks)
                }
            }
            launch { capture.failed.collect { cameraFailed.value = it } }
            launch { keys.events.collect { done() } }
            launch {
                while (isActive) {
                    delay(TICK_MS)
                    r.tick()
                }
            }
        }
    }

    private fun onState(t: TrainingState) {
        if (!t.ended && t.index != promptedIndex) {
            promptedIndex = t.index
            prompt(t)
        }
        if (t.ended) {
            audio.stop()
            promptJob?.cancel()
        }
        publish(t)
        updateCamera(t)
        if (t.counted && t.targetReached && !t.paused) done()
    }

    private fun publish(t: TrainingState? = runner?.current?.value) {
        if (t != null) state.value = TrainingUiState.Content(t, doneReady)
    }

    /** Instruction audio when present; Done unlocks when it ends or after the 1.5 s fallback. */
    private fun prompt(t: TrainingState) {
        doneReady = false
        promptJob?.cancel()
        audio.stop()
        val path = t.activity?.audio?.normal
        val index = t.index
        val playing =
            path != null &&
                active &&
                audio.play(path) {
                    viewModelScope.launch {
                        audioPlaying = false
                        unlock(index)
                    }
                }
        audioPlaying = playing
        if (!playing) {
            promptJob = viewModelScope.launch {
                delay(FALLBACK_MS)
                unlock(index)
            }
        }
    }

    private fun unlock(index: Int) {
        if (runner?.current?.value?.index != index) return
        doneReady = true
        publish()
    }

    private fun updateCamera(t: TrainingState) {
        val wanted = active && t.counted && !t.paused && !t.ended
        camera.value = wanted
        capture.configure(if (wanted) setOf(Landmarker.POSE) else emptySet())
    }

    /** Camera analysis thread: handed to the capture, never kept. */
    fun onFrame(bitmap: Bitmap, timestampMs: Long) = capture.onFrame(bitmap, timestampMs)

    fun onCameraError(@Suppress("UNUSED_PARAMETER") reason: String) {
        cameraFailed.value = true
    }

    /** Done (button, Bluetooth key, or criterion reached): saves the activity once and moves on. */
    fun done() {
        val r = runner ?: return
        val t = r.current.value
        val criterionReached = t.counted && t.targetReached
        if (finishing || t.ended || t.paused || sheet.value || (!doneReady && !criterionReached)) return
        finishing = true
        viewModelScope.launch {
            try {
                r.finish()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                // The activity stays on screen; Done (or the criterion check) retries the same deterministic row id.
                warn("training save failed: ${e::class.simpleName}")
            } finally {
                finishing = false
            }
        }
    }

    fun setActive(value: Boolean) {
        active = value
        if (!value) {
            audio.stop()
            audioPlaying = false
            promptJob?.cancel()
        } else {
            ensureUnlock()
        }
        runner?.current?.value?.let(::updateCamera)
    }

    /** Done is still locked but its audio was stopped (pause, background): re-run the 1.5 s fallback. */
    private fun ensureUnlock() {
        val t = runner?.current?.value ?: return
        if (doneReady || t.ended || promptJob?.isActive == true || audioPlaying) return
        val index = t.index
        promptJob = viewModelScope.launch {
            delay(FALLBACK_MS)
            unlock(index)
        }
    }

    /** Re-checks a criterion reached while the sheet was open. */
    private fun recheckTarget() {
        val t = runner?.current?.value ?: return
        if (t.counted && t.targetReached && !t.paused) done()
    }

    fun openAdultSheet() {
        sheet.value = true
    }

    fun dismissAdultSheet() {
        sheet.value = false
        recheckTarget()
    }

    fun pause() {
        runner?.pause()
        audio.stop()
        audioPlaying = false
        promptJob?.cancel()
    }

    fun resume() {
        runner?.resume()
        sheet.value = false
        ensureUnlock()
        recheckTarget()
    }

    /** Adult exit through the PIN: the current activity is interrupted and writes nothing. */
    fun exit() {
        sheet.value = false
        runner?.interrupt()
        audio.stop()
        capture.configure(emptySet())
    }

    override fun onCleared() {
        audio.stop()
        capture.close()
    }

    private companion object {
        const val TAG = "DlsTraining"
        const val TICK_MS = 250L
        const val FALLBACK_MS = 1_500L
    }
}
