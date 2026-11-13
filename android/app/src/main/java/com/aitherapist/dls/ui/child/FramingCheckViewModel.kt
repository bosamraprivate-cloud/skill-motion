package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.vision.FpsStream
import com.aitherapist.dls.vision.Framing
import com.aitherapist.dls.vision.FramingGuidance
import com.aitherapist.dls.vision.LandmarkEngine
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.LandmarkResult
import com.aitherapist.dls.vision.Landmarkers
import com.aitherapist.dls.vision.NeededLandmarks
import com.aitherapist.dls.vision.VisibilityGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Adult-facing framing check before a camera-scored task (Story 3.4). Nothing here is scored, stored or sent. */
sealed interface FramingUiState {
    data object Loading : FramingUiState

    /** Content-only task (every step `none`): no framing check; hand over at once. */
    data object Skip : FramingUiState

    /**
     * [framed] turns the outline sage (needed landmarks visible for [VisibilityGate.HOLD_MS]). [canOverride] shows
     * "Start anyway" (10 s without sage, or the engine failed). [engineError] is an exception type name only.
     */
    data class Check(
        val framing: Framing,
        val framed: Boolean = false,
        val guidance: FramingGuidance? = null,
        val canOverride: Boolean = false,
        val engineError: String? = null
    ) : FramingUiState

    /** The catalogue could not load: message plus Start anyway. */
    data class Unavailable(val reason: String) : FramingUiState
}

@HiltViewModel
class FramingCheckViewModel
internal constructor(
    private val catalog: CatalogRepository,
    private val engine: LandmarkEngine,
    private val compute: CoroutineDispatcher
) : ViewModel() {
    @Inject constructor(catalog: CatalogRepository, landmarkers: Landmarkers) : this(catalog, landmarkers, Dispatchers.Default)

    private val state = MutableStateFlow<FramingUiState>(FramingUiState.Loading)
    val uiState: StateFlow<FramingUiState> = state.asStateFlow()

    private var needed = NeededLandmarks(emptySet(), 0)
    private val hold = VisibilityGate.Hold()
    private var loadedTask: String? = null
    private var engineJobs: List<Job> = emptyList()
    private var startJob: Job? = null
    private var stopJob: Job? = null

    @Volatile private var running = false
    private val recentHands = ArrayDeque<LandmarkResult>()

    /** Loads [taskId]'s needed landmarks once; starts the 10 s override timer. */
    fun load(taskId: String) {
        if (loadedTask == taskId) return
        loadedTask = taskId
        viewModelScope.launch {
            val loaded =
                try {
                    catalog.load().getOrThrow()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    state.value = FramingUiState.Unavailable(e::class.simpleName ?: "unknown")
                    return@launch
                }
            val task = loaded.tasks.firstOrNull { it.id == taskId }
            if (task == null) {
                state.value = FramingUiState.Unavailable("unknown task")
                return@launch
            }
            val detectors = task.steps.map { it.detector }
            needed = VisibilityGate.neededLandmarks(detectors)
            if (needed.isEmpty) {
                state.value = FramingUiState.Skip
                return@launch
            }
            state.value = FramingUiState.Check(Framing.of(detectors.firstNotNullOfOrNull { it.framing }))
            delay(OVERRIDE_AFTER_MS)
            edit { it.copy(canOverride = true) }
        }
    }

    /** Starts the landmarkers (lifecycle start with camera permission). */
    fun start() {
        if (running) return
        running = true
        val previousStop = stopJob
        startJob =
            viewModelScope.launch {
                previousStop?.join()
                try {
                    withContext(compute) {
                        engine.start()
                        // Stopped or cleared while the landmarkers were being created: close them right away.
                        if (!running) engine.close()
                    }
                    if (running) edit { it.copy(engineError = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    fail(e::class.simpleName ?: "unknown")
                }
            }
        engineJobs =
            listOf(
                viewModelScope.launch { engine.results.collect(::onResult) },
                viewModelScope.launch { engine.errors.collect(::fail) }
            )
    }

    /** Releases the landmarkers off the main thread (lifecycle stop, leaving the screen). */
    fun stop() {
        if (!running) return
        running = false
        engineJobs.forEach { it.cancel() }
        engineJobs = emptyList()
        val pending = startJob
        startJob = null
        stopJob =
            viewModelScope.launch {
                pending?.cancelAndJoin()
                withContext(compute) { engine.close() }
            }
        synchronized(recentHands) { recentHands.clear() }
        hold.update(false, 0L)
        edit { it.copy(framed = false, guidance = null) }
    }

    /** Camera could not start; reason is the exception type name only. */
    fun onCameraError(reason: String) = fail(reason)

    /** Camera analysis thread: an upright, un-mirrored frame; it is only passed to the landmarkers, never kept. */
    fun onFrame(bitmap: Bitmap, timestampMs: Long) {
        if (running) engine.detect(bitmap, timestampMs)
    }

    /**
     * One landmarker result. Hand results are kept (a few); each pose result is evaluated with the nearest hand result
     * within [VisibilityGate.PAIR_MS] of its timestamp, so a pose arriving after its hands keeps them.
     */
    internal fun onResult(result: LandmarkResult) {
        when (result.stream) {
            FpsStream.HAND -> synchronized(recentHands) {
                recentHands.addLast(result)
                while (recentHands.size > KEPT_HANDS) recentHands.removeFirst()
            }
            FpsStream.POSE -> {
                val hand = synchronized(recentHands) {
                    recentHands.filter { abs(it.timestampMs - result.timestampMs) <= VisibilityGate.PAIR_MS }
                        .minByOrNull { abs(it.timestampMs - result.timestampMs) }
                }
                onFrame(LandmarkFrame(result.timestampMs, result.sets.firstOrNull(), hand?.sets.orEmpty()))
            }
            FpsStream.COMBINED -> Unit
        }
    }

    internal fun onFrame(frame: LandmarkFrame) {
        val framed = hold.update(VisibilityGate.framed(frame, needed), frame.timestampMs)
        val guidance = VisibilityGate.guidance(frame, needed)
        edit { it.copy(framed = framed, guidance = guidance) }
    }

    private fun fail(reason: String) {
        edit { it.copy(engineError = reason, canOverride = true, framed = false) }
    }

    private fun edit(change: (FramingUiState.Check) -> FramingUiState.Check) {
        state.update { if (it is FramingUiState.Check) change(it) else it }
    }

    override fun onCleared() {
        running = false
        engineJobs.forEach { it.cancel() }
        val pending = startJob
        // viewModelScope is already cancelled; wait for a start in flight, then close on a background thread.
        CoroutineScope(compute).launch {
            pending?.join()
            engine.close()
        }
    }

    internal companion object {
        const val OVERRIDE_AFTER_MS = 10_000L
        const val KEPT_HANDS = 8
    }
}
