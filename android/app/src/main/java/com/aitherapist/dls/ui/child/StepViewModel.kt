package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.audio.AudioPlayer
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CharacterVariant
import com.aitherapist.dls.catalog.Landmarker
import com.aitherapist.dls.catalog.Step
import com.aitherapist.dls.data.ChildProfiles
import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.data.ResumableSessions
import com.aitherapist.dls.data.SessionStars
import com.aitherapist.dls.session.AssessmentRunner
import com.aitherapist.dls.session.AssessmentRunnerFactory
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.session.PoseSample
import com.aitherapist.dls.session.PromptState
import com.aitherapist.dls.session.RunnerEvent
import com.aitherapist.dls.session.RunnerState
import com.aitherapist.dls.session.StepEnd
import com.aitherapist.dls.ui.therapist.CharacterIndexSource
import com.aitherapist.dls.vision.AttemptCapture
import com.aitherapist.dls.vision.CapturedSample
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Child taps on the step screen. */
sealed interface StepEvent {
    data object Done : StepEvent

    data object Help : StepEvent

    data object Repeat : StepEvent
}

/**
 * Drives one child-mode run (Story 2.14): owns the [AssessmentRunner], plays the step audio (normal, or slow at
 * level 3) and sends AudioEnded, debounces Done, ticks the open window, and retries a failed save without
 * telling the child. All work runs on the main thread (viewModelScope); the runner hops to its own dispatcher.
 */
@HiltViewModel
class StepViewModel
@Inject
constructor(
    private val catalog: CatalogRepository,
    private val children: ChildProfiles,
    private val characters: CharacterIndexSource,
    private val runners: AssessmentRunnerFactory,
    private val sessions: ResumableSessions,
    private val audio: AudioPlayer,
    private val keys: ChildModeKeys,
    private val clock: Clock,
    /** Outlives this ViewModel: saves the session `paused` after the step screen is gone. */
    private val outerScope: CoroutineScope,
    /** Story 3.5: background camera landmarks for camera-scored steps (no preview). */
    private val capture: AttemptCapture? = null,
    /** Story 4.7: live star count of the current session for the star bar. */
    private val stars: SessionStars? = null
) : ViewModel() {
    private val state = MutableStateFlow<StepUiState>(StepUiState.Waiting)
    val uiState: StateFlow<StepUiState> = state.asStateFlow()

    private val sheet = MutableStateFlow(false)

    private val finishedFlow = MutableStateFlow(false)

    /** The last step finished and the session is saved `done` (the host may chain the next task). */
    val completed: StateFlow<Boolean> = finishedFlow.asStateFlow()

    private val nextTaskFlow = MutableStateFlow<String?>(null)

    /** Jacket "both": the task to run after this one (new run: the chained task; continued run: the saved one). */
    val nextTaskId: StateFlow<String?> = nextTaskFlow.asStateFlow()

    /** The running session's id (Story 4.9 task reward), null until a run exists. */
    val sessionId: String?
        get() = runner?.id

    private val cameraFlow = MutableStateFlow(false)

    /** The camera should run (a camera-scored step is on screen and the run is not held); the host binds it. */
    val cameraWanted: StateFlow<Boolean> = cameraFlow.asStateFlow()

    private val cameraDown = MutableStateFlow(false)

    /** The camera or landmarkers failed: shown to the adult in the pause sheet only, never to the child. */
    val cameraUnavailable: StateFlow<Boolean> = cameraDown.asStateFlow()

    /** Step index the capture was last configured for: a change drops any frame in flight. */
    private var captureStep: Int? = null

    private val effectFlow = MutableStateFlow<StepEffect?>(null)

    /** Story 4.8: the step-end effect now on screen (after the verdict is saved and no instruction audio plays). */
    val effect: StateFlow<StepEffect?> = effectFlow.asStateFlow()

    private var effectLevel = EffectLevel.CALM

    /** The step screen the effect plays on (step N), kept while the runner already sits on step N+1 or the end. */
    private var effectScreen: StepUiState.Step? = null

    /** Last screen rendered per step index, so the effect shows the step that just ended. */
    private val shownSteps = HashMap<Int, StepUiState.Step>()
    private var effectSeq = 0
    private var handledStepEnd = -1
    private var effectJob: Job? = null

    /** The adult pause sheet (Pause / Resume / Exit) is open. */
    val adultSheet: StateFlow<Boolean> = sheet.asStateFlow()

    private val doneDebouncer = DoneDebouncer(clock)
    private val helpDebouncer = DoneDebouncer(clock)
    private val repeatDebouncer = DoneDebouncer(clock)
    private var paused = false
    private var started = false
    private var ended = false
    private var runner: AssessmentRunner? = null
    private var steps: List<CatalogStep> = emptyList()
    private var variant: CharacterVariant? = null
    private var variantId: String = ""
    private var pointStep = -1
    private var pointLevel = 0
    private var pointing = false
    private var pointJob: Job? = null
    private var taskName: String? = null
    private var starCount = 0

    /** New session: the task start screen shows until the child taps Start ([beginTask]). */
    private var awaitingStart = false

    /** Adult sheet Pause where the runner has nothing to pause (start screen, finished run): dim and hold. */
    private var localPaused = false

    /** The `done` save after the last step is in flight (or retrying): never start it twice. */
    private var completing = false

    /** The LISTEN state the audio was started for (by reference: every prompt entry is a new object). */
    private var audioFor: RunnerState.Listen? = null
    private var audioPlaying = false
    private var audioToken = 0
    private var fallbackJob: Job? = null
    private var tickJob: Job? = null
    private var retryJob: Job? = null

    init {
        // A paired key means Start on the task start screen, Done on a step.
        viewModelScope.launch { keys.events.collect { if (awaitingStart) beginTask() else onEvent(StepEvent.Done) } }
        capture?.let { source ->
            viewModelScope.launch { source.samples.collect(::onSample) }
            viewModelScope.launch { source.failed.collect { if (it) cameraDown.value = true } }
        }
    }

    /** Camera analysis thread: handed to the capture, never kept here. */
    fun onFrame(bitmap: Bitmap, timestampMs: Long) {
        capture?.onFrame(bitmap, timestampMs)
    }

    /** The camera could not start: no samples arrive, so camera steps are judged cannot tell (out_of_frame). */
    fun onCameraError(reason: String) {
        runCatching { Log.w(TAG, "camera unavailable: $reason") }
        cameraDown.value = true
    }

    /** A paired sample goes to the runner only while its window is open (the runner ignores it otherwise). */
    private suspend fun onSample(sample: CapturedSample) {
        val target = runner ?: return
        if (target.state.value !is RunnerState.Attempt || paused || localPaused) return
        val joints = sample.landmarks.pose?.withIndex()?.associate { (i, p) -> i to p }.orEmpty()
        val pose = PoseSample(sample.landmarks.timestampMs, joints, sample.landmarks.hands)
        target.onEvent(RunnerEvent.Sample(pose, sample.frame, sample.landmarks))
    }

    /** Landmarkers for the current state: the step's own while it is on screen and not held, else none. */
    private fun syncCapture(current: RunnerState) {
        val held = paused || awaitingStart || localPaused || ended
        val wanted: Set<Landmarker> =
            if (held || current !is RunnerState.OnStep || current is RunnerState.Paused) {
                emptySet()
            } else {
                steps.getOrNull(current.stepIndex)?.detector?.landmarkers.orEmpty().toSet()
            }
        val stepIndex = (current as? RunnerState.OnStep)?.stepIndex
        if (stepIndex != captureStep) {
            captureStep = stepIndex
            capture?.reset()
        }
        capture?.configure(wanted)
        cameraFlow.value = wanted.isNotEmpty()
    }

    /**
     * Starts a new assessment session for [childId] on [taskId], or continues the saved session [sessionId]
     * (Story 2.15); later calls are ignored.
     */
    fun start(childId: String, taskId: String, sessionId: String? = null, thenTaskId: String? = null) {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                val loaded = catalog.load().getOrThrow()
                val child = requireNotNull(children.get(childId)) { "child missing" }
                val task = loaded.tasks.firstOrNull { it.id == taskId }
                effectLevel = child.effectLevel
                variantId = child.characterVariant.name.lowercase(Locale.ROOT)
                variant = runCatching { characters.load().variants.firstOrNull { it.id == variantId } }.getOrNull()
                steps = task?.steps.orEmpty()
                taskName = task?.takeIf { it.hasName }?.nameAr
                val runSteps = steps.map { it.toStep() }
                if (sessionId != null) {
                    continueRun(childId, taskId, sessionId, loaded.catalogVersion, runSteps)
                    return@launch
                }
                requireNotNull(task) { "task missing" }
                nextTaskFlow.value = thenTaskId
                val created = runners.create(childId, taskId, loaded.catalogVersion, runSteps, thenTaskId)
                runner = created
                awaitingStart = true
                launch { created.state.collect { sync() } }
                observeStars(created.id)
                created.start()
                sync()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                // Type only, no data. runCatching: android.util.Log is not available in plain unit tests.
                runCatching { Log.w(TAG, "child mode load failed: ${e::class.simpleName}") }
                if (sessionId != null) {
                    // A continue that cannot load ends calmly; the saved session is abandoned so its card goes away.
                    runCatching { sessions.session(sessionId)?.let { runners.restore(it, "", "", emptyList()).restore(it) } }
                    endCalmly()
                } else {
                    // The child only ever sees the calm wait visual; the adult leaves through the adult corner.
                    state.value = StepUiState.Waiting
                }
            }
        }
    }

    /**
     * Continues saved session [sessionId]. A missing session, or one whose task, catalogue version or resume point
     * no longer fits, is abandoned (the runner does it) and the child sees the calm end visual.
     */
    private suspend fun continueRun(childId: String, taskId: String, sessionId: String, catalogVersion: String, runSteps: List<Step>) {
        val saved = sessions.session(sessionId)?.takeIf { it.childId == childId }
        val created = saved?.let { runners.restore(it, taskId, catalogVersion, runSteps) }
        if (saved == null || created == null || !created.restore(saved)) {
            // Type only, no data.
            runCatching { Log.w(TAG, "resume point invalid") }
            endCalmly()
            return
        }
        runner = created
        // A chained task already finished after this session started is not run again.
        nextTaskFlow.value =
            saved.nextTaskId?.takeUnless { next ->
                runCatching { sessions.finishedSince(childId, next, saved.startedAt) }.getOrDefault(false)
            }
        // A session interrupted before anything was recorded starts again from the task start screen.
        awaitingStart = saved.stepIndex == 0 && !created.hasAttempts()
        viewModelScope.launch { created.state.collect { sync() } }
        observeStars(created.id)
        sync()
    }

    /** Keeps the star bar on the session's saved star total (stars only; effects come in Story 4.8). */
    private fun observeStars(sessionId: String) {
        val source = stars ?: return
        viewModelScope.launch {
            source
                .observe(sessionId)
                .retryWhen { e, attempt ->
                    // The star bar keeps its last count and retries with a short backoff; type only, no data.
                    if (e is CancellationException) return@retryWhen false
                    runCatching { Log.w(TAG, "star count failed: ${e::class.simpleName}") }
                    delay(STAR_RETRY_MS * (attempt + 1).coerceAtMost(STAR_RETRY_MAX_STEPS))
                    true
                }.collect { n ->
                    starCount = n
                    (state.value as? StepUiState.Step)?.let { state.value = it.copy(stars = n) }
                }
        }
    }

    /**
     * A step's rows and stars are saved: its effect plays right away on that step's screen; the next step's
     * instruction audio waits until the effect ends (bounded). Held (adult pause, screen gone): no effect, never late.
     */
    private fun onStepEnd(end: StepEnd) {
        if (paused || localPaused || awaitingStart) return
        if (runner?.state?.value is RunnerState.Paused) return
        val kind = if (end.outcome == StepEnd.Outcome.NOT_DONE) StepEffect.Kind.ENCOURAGE else StepEffect.Kind.CELEBRATE
        val next = StepEffect(++effectSeq, kind, effectLevel)
        effectJob?.cancel()
        effectScreen = shownSteps[end.stepIndex]
        effectFlow.value = next
        effectJob = viewModelScope.launch {
            delay(next.durationMs)
            clearEffect()
        }
    }

    /** Ends the effect now (bounded duration, or instruction audio starting: effects never overlap it). */
    private fun clearEffect() {
        effectJob?.cancel()
        effectJob = null
        if (effectFlow.value == null) return
        effectFlow.value = null
        effectScreen = null
        sync()
    }

    /** No run: the calm end visual until the adult leaves. */
    private fun endCalmly() {
        ended = true
        state.value = StepUiState.Finished(reaction(IDLE))
    }

    /** Start tapped on the task start screen: the first step's prompt begins. */
    fun beginTask() {
        if (runner == null || !awaitingStart || localPaused) return
        awaitingStart = false
        sync()
    }

    fun onEvent(event: StepEvent) {
        val current = runner ?: return
        if (awaitingStart) return
        when (event) {
            StepEvent.Done -> {
                val ui = state.value
                if (ui !is StepUiState.Step || !ui.doneEnabled || !doneDebouncer.accept()) return
                send(current, RunnerEvent.Done)
            }
            StepEvent.Help -> if (helpDebouncer.accept()) send(current, RunnerEvent.Help)
            StepEvent.Repeat -> if (repeatDebouncer.accept()) send(current, RunnerEvent.Repeat)
        }
    }

    /** Adult corner held 2 s: open the pause sheet. */
    fun openAdultSheet() {
        sheet.value = true
    }

    fun dismissAdultSheet() {
        sheet.value = false
    }

    /** Sheet Pause: freeze the attempt (no row), save `paused`, dim the screen. */
    fun pause() {
        sheet.value = false
        val target = runner ?: return
        if (awaitingStart || target.state.value == RunnerState.Finished) {
            localPaused = true
            sync()
            return
        }
        viewModelScope.launch {
            target.pause()
            sync()
        }
    }

    /** Sheet Resume: the current attempt restarts from its beginning at the same prompt level. */
    fun resume() {
        sheet.value = false
        val target = runner ?: return
        if (localPaused) {
            localPaused = false
            sync()
            return
        }
        viewModelScope.launch {
            target.resume()
            sync()
        }
    }

    /** Sheet Exit: suspends until the session is saved `paused` (it stays resumable); the caller then opens the PIN. */
    suspend fun exit() {
        sheet.value = false
        val target = runner ?: return
        target.pause()
        sync()
    }

    /**
     * The step screen is visible (true) or covered by the PIN screen / app stopped (false). While inactive no audio
     * plays and no window ticks; on return the current prompt restarts with a fresh window (FR-15).
     */
    fun setActive(active: Boolean) {
        if (active != paused) return
        paused = !active
        val target = runner ?: return
        if (paused) {
            sync()
            return
        }
        val current = target.state.value
        if (current is RunnerState.Listen || current is RunnerState.Attempt) send(target, RunnerEvent.Repeat) else sync()
    }

    /**
     * Sends [event] and syncs right after: a Repeat while listening re-enters an equal LISTEN state the flow may not
     * re-emit, but it is a new object, so [sync] replays the audio for it.
     */
    private fun send(target: AssessmentRunner, event: RunnerEvent) {
        viewModelScope.launch {
            target.onEvent(event)
            sync()
        }
    }

    /** Maps the runner's current state to audio actions and the child UI; safe to call repeatedly. */
    private fun sync() {
        val target = runner ?: return
        // Read before the state: the runner sets the step end first, so the effect is known before any next audio.
        target.lastStepEnd.value?.takeIf { it.stepIndex != handledStepEnd }?.let { end ->
            handledStepEnd = end.stepIndex
            onStepEnd(end)
        }
        val current = target.state.value
        val held = paused || awaitingStart || localPaused
        when {
            held -> {
                stopAudio()
                stopTicking()
            }
            // Effects never overlap instruction audio: the next step's audio starts when the effect ends.
            current is RunnerState.Listen -> if (current !== audioFor && effectFlow.value == null) startAudio(current)
            else -> stopAudio()
        }
        // Adult pause or screen gone cuts the effect; it is never replayed later.
        if (held || current is RunnerState.Paused) clearEffect()
        if (current == RunnerState.Finished) ensureCompleting()
        if (!held) {
            if (current is RunnerState.Attempt) ensureTicking() else stopTicking()
        }
        // The last step's effect finishes before the end screen and any chained task.
        if (current == RunnerState.Completed && effectFlow.value == null) finishedFlow.value = true
        if (current is RunnerState.Error) ensureRetrying() else stopRetrying()
        syncCapture(current)
        state.value = render(current)
    }

    private fun startAudio(listen: RunnerState.Listen) {
        stopAudio()
        audioFor = listen
        val token = ++audioToken
        val step = steps.getOrNull(listen.stepIndex)
        val path = step?.audio?.let { if (listen.prompt.level >= PromptState.MAX_LEVEL) it.slow else it.normal }
        audioPlaying = true
        val playing = path != null && audio.play(path) { onAudioEnded(token) }
        if (!playing) {
            // Missing audio (dev catalogue): show the sentence and continue after a short fixed delay.
            fallbackJob = viewModelScope.launch {
                delay(MISSING_AUDIO_DELAY_MS)
                onAudioEnded(token)
            }
        }
    }

    private fun onAudioEnded(token: Int) {
        if (token != audioToken) return
        audioPlaying = false
        val target = runner ?: return
        state.value = render(target.state.value)
        send(target, RunnerEvent.AudioEnded)
    }

    private fun stopAudio() {
        if (!audioPlaying && fallbackJob == null) return
        audioToken++
        audioPlaying = false
        fallbackJob?.cancel()
        fallbackJob = null
        audio.stop()
    }

    /** After the last step the session is saved `done` right away; a failed save is retried via [RunnerState.Error]. */
    private fun ensureCompleting() {
        if (completing) return
        val target = runner ?: return
        completing = true
        viewModelScope.launch {
            target.complete()
            sync()
        }
    }

    private fun ensureTicking() {
        if (tickJob?.isActive == true) return
        val target = runner ?: return
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                target.onEvent(RunnerEvent.Tick)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun ensureRetrying() {
        if (retryJob?.isActive == true) return
        val target = runner ?: return
        retryJob = viewModelScope.launch {
            while (isActive && target.state.value is RunnerState.Error) {
                delay(RETRY_MS)
                target.onEvent(RunnerEvent.Retry)
            }
        }
    }

    private fun stopRetrying() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun render(current: RunnerState): StepUiState {
        val frozen = effectScreen
        val effectOn = effectFlow.value
        if (effectOn != null && frozen != null) {
            // Step N's screen stays during its effect: the character reacts, no button opens a window, no clip plays.
            return frozen.copy(
                visual = frozen.visual?.copy(animate = false),
                reactionClip = effectReaction() ?: frozen.reactionClip,
                doneEnabled = false,
                stars = starCount,
                paused = false
            )
        }
        return renderPlain(current)
    }

    private fun renderPlain(current: RunnerState): StepUiState = when {
        awaitingStart && current !is RunnerState.Error && current != RunnerState.Idle ->
            StepUiState.Start(
                taskName,
                steps.firstOrNull()?.media(variantId)?.still,
                reaction(POINT),
                paused = localPaused,
                portraitClip = reaction(IDLE)
            )
        current is RunnerState.OnStep -> renderStep(current)
        current == RunnerState.Completed -> StepUiState.Finished(reaction(IDLE))
        else -> StepUiState.Waiting
    }

    private fun renderStep(current: RunnerState.OnStep): StepUiState {
        val step = steps.getOrNull(current.stepIndex) ?: return StepUiState.Waiting
        val level = current.prompt.level
        val media = step.media(variantId)
        val demo = level >= 2
        val visual =
            media?.let {
                when {
                    // Levels 2-3: the demonstration clip segment, large at level 3; a missing clip shows the still enlarged.
                    demo && it.clip != null -> StepVisual(it.clip, it.still, animate = true, large = level >= PromptState.MAX_LEVEL)
                    demo -> StepVisual(null, it.still, animate = false, large = true)
                    else -> StepVisual(it.clip, it.still, animate = false, large = false)
                }
            }
        val listening = current is RunnerState.Listen && audioPlaying
        val paused = current is RunnerState.Paused
        trackPoint(current.stepIndex, level)
        // The side character guides every step (EXPERIENCE "Side character"): talk while the instruction plays (any
        // level), point toward the demonstration for a moment when the level rises to 2+, idle while the child tries.
        val reactionState =
            when {
                paused -> IDLE
                listening -> TALK
                pointing -> POINT
                else -> IDLE
            }
        return StepUiState.Step(
            stepIndex = current.stepIndex,
            stepCount = steps.size,
            sentence = step.textAr,
            visual = if (paused) visual?.copy(animate = false) else visual,
            reactionClip = reaction(reactionState),
            portraitClip = reaction(if (reactionState == POINT) IDLE else reactionState),
            doneEnabled = current is RunnerState.Attempt,
            promptLevel = level,
            stars = starCount,
            paused = paused
        ).also { shownSteps[current.stepIndex] = it }
    }

    /**
     * Starts the short `point` when the prompt level rises to 2+ on a step; it ends after [POINT_MS] (or earlier in
     * effect, because instruction audio wins with `talk`). A new step resets the tracking.
     */
    private fun trackPoint(stepIndex: Int, level: Int) {
        if (stepIndex != pointStep) {
            pointStep = stepIndex
            pointLevel = level
            pointing = false
            pointJob?.cancel()
            return
        }
        if (level == pointLevel) return
        pointLevel = level
        if (level < 2) return
        pointing = true
        pointJob?.cancel()
        pointJob = viewModelScope.launch {
            delay(POINT_MS)
            pointing = false
            runner?.let { state.value = render(it.state.value) }
        }
    }

    /** The character clip of the effect on screen: celebrateSmall or encourage. */
    private fun effectReaction(): String? = effectFlow.value?.let {
        reaction(if (it.kind == StepEffect.Kind.ENCOURAGE) ENCOURAGE else CELEBRATE_SMALL)
    }

    private fun reaction(name: String): String? = variant?.reactions?.let { list ->
        (
            list.firstOrNull { it.state == name }
                ?: list.firstOrNull { it.state == IDLE }
                ?: list.firstOrNull()
            )?.file
    }

    override fun onCleared() {
        audio.stop()
        cameraFlow.value = false
        capture?.close()
        // Leaving child mode (Exit + PIN) keeps the session resumable: it is saved `paused`, never abandoned.
        runner?.let { target -> outerScope.launch { target.pause() } }
    }

    companion object {
        private const val TAG = "DlsChild"
        const val MISSING_AUDIO_DELAY_MS = 1_500L

        /** How long the side character points after the level rises to 2+ (unless the instruction audio starts). */
        const val POINT_MS = 2_000L

        const val TICK_MS = 1_000L
        const val RETRY_MS = 2_000L
        private const val STAR_RETRY_MS = 500L
        private const val STAR_RETRY_MAX_STEPS = 10L

        // Reaction clip states from the character index (Story 2.3).
        private const val IDLE = "idle"
        private const val TALK = "talk"

        // Points toward the main area: the Start button, or the demonstration from level 2.
        private const val POINT = "point"

        // Story 4.8 step-end reactions.
        private const val CELEBRATE_SMALL = "celebrateSmall"
        private const val ENCOURAGE = "encourage"
    }
}
