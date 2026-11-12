package com.aitherapist.dls.ui.therapist

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.BuildConfig
import com.aitherapist.dls.camera.FrameBuffer
import com.aitherapist.dls.catalog.CatalogJson
import com.aitherapist.dls.catalog.Step
import com.aitherapist.dls.vision.FpsMeter
import com.aitherapist.dls.vision.FpsStream
import com.aitherapist.dls.vision.InferenceDelegate
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.LandmarkResult
import com.aitherapist.dls.vision.Landmarkers
import com.aitherapist.dls.vision.PoseVerifier
import com.aitherapist.dls.vision.VerifyContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DiagnosticsViewModel
@Inject
constructor(private val landmarkers: Landmarkers, @ApplicationContext private val context: Context) :
    ViewModel() {
    private val clock: () -> Long = SystemClock::uptimeMillis
    private val fpsMeter = FpsMeter(clock)
    private val frameBuffer = FrameBuffer<Long, LandmarkResult>(clock)
    private val debuggable = BuildConfig.DEBUG

    // Debug-only spike attempt (Story 1.3): a longer buffer holds the whole attempt window.
    private val attemptBuffer = FrameBuffer<Long, LandmarkResult>(clock, retentionMs = ATTEMPT_RETENTION_MS)
    private val poseVerifier = PoseVerifier(clock)
    private var spikeStep: Step? = null
    private val attempt = MutableStateFlow<AttemptState?>(null)
    private var attemptJob: Job? = null
    private var loadJob: Job? = null

    @Volatile private var recordingAttempt = false

    private val permission = MutableStateFlow<Boolean?>(null)
    private val engine = MutableStateFlow<Engine>(Engine.Stopped)
    private val landmarks = MutableStateFlow(Latest())
    private val run = MutableStateFlow<RunState>(RunState.Idle)
    private var collectorJobs: List<Job> = emptyList()
    private var startJob: Job? = null

    @Volatile private var frameAspect = DEFAULT_FRAME_ASPECT
    private var stopJob: Job? = null
    private var runJob: Job? = null

    private val ticker =
        flow {
            while (true) {
                emit(clock())
                delay(TICK_MS)
            }
        }

    val uiState: StateFlow<DiagnosticsUiState> =
        combine(combine(permission, engine, ::Pair), landmarks, run, attempt, ticker) { (granted, eng), latest, runState, att, _ ->
            when {
                granted == null -> DiagnosticsUiState.PermissionRequired
                !granted -> DiagnosticsUiState.PermissionDenied
                eng is Engine.Failed -> DiagnosticsUiState.Error(eng.reason)
                eng !is Engine.Ready -> DiagnosticsUiState.Loading
                else -> content(eng.delegate, latest, runState).copy(attempt = att)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DiagnosticsUiState.Loading)

    fun onEvent(event: DiagnosticsEvent) {
        when (event) {
            is DiagnosticsEvent.PermissionResult -> permission.value = event.granted
            DiagnosticsEvent.StartRun -> startRun()
            DiagnosticsEvent.TryStep -> tryStep()
            DiagnosticsEvent.Retry -> {
                stop()
                start()
            }
        }
    }

    /** Starts the landmarkers (call on lifecycle start with permission granted). */
    fun start() {
        if (permission.value != true || engine.value is Engine.Ready || engine.value is Engine.Starting) return
        engine.value = Engine.Starting
        if (debuggable && loadJob == null) loadJob = viewModelScope.launch { loadSpikeStep() }
        val previousStop = stopJob
        startJob =
            viewModelScope.launch {
                previousStop?.join() // never let a pending close() hit the landmarkers created here
                val next =
                    try {
                        Engine.Ready(withContext(Dispatchers.Default) { landmarkers.start() })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception
                    ) {
                        Engine.Failed(e::class.simpleName ?: "unknown")
                    }
                setEngine(next)
            }
        collectorJobs =
            listOf(
                viewModelScope.launch { landmarkers.results.collect(::onResult) },
                viewModelScope.launch { landmarkers.errors.collect { setEngine(Engine.Failed(it)) } }
            )
    }

    /** Releases the landmarkers off the main thread (call on lifecycle stop). */
    fun stop() {
        collectorJobs.forEach { it.cancel() }
        collectorJobs = emptyList()
        cancelRun()
        cancelAttempt()
        engine.value = Engine.Stopped
        val pendingStart = startJob
        startJob = null
        stopJob =
            viewModelScope.launch {
                pendingStart?.cancelAndJoin()
                withContext(Dispatchers.Default) { landmarkers.close() }
            }
        frameBuffer.clear()
        fpsMeter.clearPending()
    }

    /** Camera could not be bound; reason is the exception type name only. */
    fun onCameraError(reason: String) {
        setEngine(Engine.Failed(reason))
    }

    /** Called on the camera analysis thread with an upright, un-flipped (already un-mirrored) frame. */
    fun onFrame(bitmap: Bitmap, timestampMs: Long) {
        frameAspect = bitmap.width.toFloat() / bitmap.height
        if (engine.value !is Engine.Ready) return
        if (landmarkers.detect(bitmap, timestampMs)) {
            frameBuffer.addFrame(timestampMs, timestampMs)
            if (recordingAttempt) attemptBuffer.addFrame(timestampMs, timestampMs)
        }
    }

    override fun onCleared() {
        collectorJobs.forEach { it.cancel() }
        runJob?.cancel()
        cancelAttempt()
        // viewModelScope is already cancelled; close on a background thread to avoid blocking main.
        CoroutineScope(Dispatchers.Default).launch { landmarkers.close() }
    }

    private fun setEngine(next: Engine) {
        if (next is Engine.Failed) {
            cancelRun()
            cancelAttempt()
        }
        engine.value = next
    }

    private fun cancelRun() {
        runJob?.cancel()
        runJob = null
        if (run.value is RunState.Running) {
            fpsMeter.finishRun()
            run.value = RunState.Idle
        }
    }

    private fun onResult(result: LandmarkResult) {
        fpsMeter.onResult(result.stream, result.timestampMs)
        when (result.stream) {
            FpsStream.POSE -> {
                if (result.sets.isNotEmpty()) {
                    frameBuffer.addResult(result.timestampMs, result)
                    if (recordingAttempt) attemptBuffer.addResult(result.timestampMs, result)
                }
                landmarks.update { it.copy(pose = result.sets.firstOrNull().orEmpty()) }
            }
            FpsStream.HAND -> landmarks.update { it.copy(hands = result.sets) }
            FpsStream.COMBINED -> Unit
        }
    }

    private fun startRun() {
        if (run.value is RunState.Running || engine.value !is Engine.Ready) return
        fpsMeter.startRun()
        run.value = RunState.Running(0, RUN_SEC)
        runJob =
            viewModelScope.launch {
                delay(FpsMeter.RUN_DURATION_MS)
                val result = fpsMeter.finishRun() ?: return@launch
                run.value = RunState.Done(result)
                if (debuggable) {
                    // Numbers only: no frames, landmarks or child data.
                    Log.d(
                        LOG_TAG,
                        String.format(Locale.ROOT, "%.1f %.1f %.1f", result.pose, result.hand, result.combined)
                    )
                }
            }
    }

    /** Loads the first debug-only spike step from assets/spike (absent in release builds). */
    private suspend fun loadSpikeStep() {
        if (spikeStep != null) return
        val step =
            withContext(Dispatchers.IO) {
                try {
                    val name = context.assets.list(SPIKE_DIR)?.sorted()?.firstOrNull()
                    name?.let { n -> CatalogJson.decodeStep(context.assets.open("$SPIKE_DIR/$n").bufferedReader().use { it.readText() }) }
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    Log.d(VERDICT_TAG, "spike load failed: ${e::class.simpleName}") // type name only, no data
                    null
                }
            } ?: return
        spikeStep = step
        if (attempt.value == null) attempt.value = AttemptState.Idle(step.id)
    }

    private fun cancelAttempt() {
        attemptJob?.cancel()
        attemptJob = null
        recordingAttempt = false
        val step = spikeStep
        if (attempt.value is AttemptState.Recording && step != null) attempt.value = AttemptState.Idle(step.id)
    }

    /** Records one attempt window, then verifies it locally and reports the latency from window close. */
    private fun tryStep() {
        val step = spikeStep ?: return
        if (!debuggable || attempt.value is AttemptState.Recording || engine.value !is Engine.Ready) return
        attemptJob =
            viewModelScope.launch {
                attemptBuffer.clear()
                val openedAt = clock()
                recordingAttempt = true
                for (remaining in ATTEMPT_SEC downTo 1) {
                    attempt.value = AttemptState.Recording(step.id, remaining)
                    delay(MS_PER_SEC)
                }
                recordingAttempt = false
                val closedAt = clock()
                delay(FrameBuffer.DEFAULT_PAIR_WINDOW_MS) // let the last frames pair or time out (AD-3)
                val landmarkFrames =
                    attemptBuffer
                        .resolved()
                        .filter { it.timestampMs in openedAt..closedAt }
                        .map { LandmarkFrame(it.timestampMs, it.result?.sets?.firstOrNull()) }
                val pairingWaitMs = clock() - closedAt
                val verdict =
                    withContext(Dispatchers.Default) { poseVerifier.verify(step, emptyList(), landmarkFrames, VerifyContext()) }
                attempt.value = AttemptState.Done(step.id, verdict.outcome, verdict.reason, pairingWaitMs, verdict.latencyMs)
                // Numbers only: pairing wait ms, evaluation ms, total ms (NFR-1), frame count.
                val total = pairingWaitMs + verdict.latencyMs
                Log.d(VERDICT_TAG, "$pairingWaitMs ${verdict.latencyMs} $total ${landmarkFrames.size}")
            }
    }

    private fun content(delegate: InferenceDelegate, latest: Latest, runState: RunState): DiagnosticsUiState.Content {
        val resolved = frameBuffer.resolved()
        val visible = if (resolved.isEmpty()) 0 else resolved.count { it.visible } * PERCENT / resolved.size
        val shownRun =
            if (runState is RunState.Running) {
                RunState.Running(((fpsMeter.runElapsedMs() ?: 0L) / MS_PER_SEC).toInt(), RUN_SEC)
            } else {
                runState
            }
        return DiagnosticsUiState.Content(
            delegate = delegate,
            poseFps = fpsMeter.fps(FpsStream.POSE),
            handFps = fpsMeter.fps(FpsStream.HAND),
            combinedFps = fpsMeter.fps(FpsStream.COMBINED),
            visiblePercent = visible,
            frameAspect = frameAspect,
            posePoints = latest.pose,
            handPoints = latest.hands,
            run = shownRun
        )
    }

    private data class Latest(val pose: List<LandmarkPoint> = emptyList(), val hands: List<List<LandmarkPoint>> = emptyList())

    private sealed interface Engine {
        data object Stopped : Engine

        data object Starting : Engine

        data class Ready(val delegate: InferenceDelegate) : Engine

        data class Failed(val reason: String) : Engine
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 250L
        const val MS_PER_SEC = 1_000L
        const val PERCENT = 100
        const val RUN_SEC = (FpsMeter.RUN_DURATION_MS / 1_000L).toInt()
        const val LOG_TAG = "DlsFps"
        const val VERDICT_TAG = "DlsVerdict"
        const val SPIKE_DIR = "spike"
        const val ATTEMPT_SEC = 5
        const val ATTEMPT_RETENTION_MS = 7_000L
        const val DEFAULT_FRAME_ASPECT = 4f / 3f
    }
}
