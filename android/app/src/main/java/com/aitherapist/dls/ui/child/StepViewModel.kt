package com.aitherapist.dls.ui.child

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.audio.AudioPlayer
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CharacterVariant
import com.aitherapist.dls.catalog.Step
import com.aitherapist.dls.data.ChildProfiles
import com.aitherapist.dls.data.ResumableSessions
import com.aitherapist.dls.session.AssessmentRunner
import com.aitherapist.dls.session.AssessmentRunnerFactory
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.session.PromptState
import com.aitherapist.dls.session.RunnerEvent
import com.aitherapist.dls.session.RunnerState
import com.aitherapist.dls.ui.therapist.CharacterIndexSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private val outerScope: CoroutineScope
) : ViewModel() {
    private val state = MutableStateFlow<StepUiState>(StepUiState.Waiting)
    val uiState: StateFlow<StepUiState> = state.asStateFlow()

    private val sheet = MutableStateFlow(false)

    private val finishedFlow = MutableStateFlow(false)

    /** The walkthrough ended and the session is saved `done` (the host may chain the next task). */
    val completed: StateFlow<Boolean> = finishedFlow.asStateFlow()

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
    private var taskName: String? = null

    /** New session: the task start screen shows until the child taps Start ([beginTask]). */
    private var awaitingStart = false

    /** Adult sheet Pause where the runner has nothing to pause (start screen, walkthrough): dim and hold. */
    private var localPaused = false

    /** The walkthrough step to play next; Resume or return continues from here. */
    private var walkIndex = 0
    private var walkJob: Job? = null
    private var walkState: StepUiState.Walkthrough? = null

    /** The walkthrough ended and the `done` save is in flight (or retrying): never replay it. */
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
    }

    /**
     * Starts a new assessment session for [childId] on [taskId], or continues the saved session [sessionId]
     * (Story 2.15); later calls are ignored.
     */
    fun start(childId: String, taskId: String, sessionId: String? = null) {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                val loaded = catalog.load().getOrThrow()
                val child = requireNotNull(children.get(childId)) { "child missing" }
                val task = loaded.tasks.firstOrNull { it.id == taskId }
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
                val created = runners.create(childId, taskId, loaded.catalogVersion, runSteps)
                runner = created
                awaitingStart = true
                launch { created.state.collect { sync() } }
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
        // A session interrupted before anything was recorded starts again from the task start screen.
        awaitingStart = saved.stepIndex == 0 && !created.hasAttempts()
        viewModelScope.launch { created.state.collect { sync() } }
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
        val current = runner?.state?.value ?: return
        val held = paused || awaitingStart || localPaused
        if (held || current != RunnerState.Finished) stopWalkthrough()
        when {
            held -> {
                stopAudio()
                stopTicking()
            }
            current is RunnerState.Listen -> if (current !== audioFor) startAudio(current)
            current == RunnerState.Finished -> ensureWalkthrough()
            else -> stopAudio()
        }
        if (!held) {
            if (current is RunnerState.Attempt) ensureTicking() else stopTicking()
        }
        if (current == RunnerState.Completed) finishedFlow.value = true
        if (current is RunnerState.Error) ensureRetrying() else stopRetrying()
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

    /**
     * Closing walkthrough (Story 2.16): every step's demonstration (clip, else still, else the reaction character)
     * with its normal audio (or the fixed fallback delay), back to back; then the session is saved `done`.
     * Interrupted (adult Pause or screen covered), it continues from the current step.
     */
    private fun ensureWalkthrough() {
        if (completing || walkJob?.isActive == true) return
        val target = runner ?: return
        walkJob = viewModelScope.launch {
            while (walkIndex < steps.size) {
                val walk = renderWalk(walkIndex)
                walkState = walk
                state.value = walk
                playAndWait(steps[walkIndex].audio?.normal)
                walkIndex++
            }
            walkState = null
            walkJob = null
            completing = true
            target.complete()
            sync()
        }
    }

    private fun stopWalkthrough() {
        val job = walkJob ?: return
        walkJob = null
        job.cancel()
        stopAudio()
    }

    /**
     * Plays [path] and suspends until it ends, at most [WALK_STEP_CAP_MS] (a player that never reports the end must
     * not hang the walkthrough); missing or unplayable audio waits the fixed fallback delay.
     */
    private suspend fun playAndWait(path: String?) {
        stopAudio()
        val token = ++audioToken
        val ended = CompletableDeferred<Unit>()
        audioPlaying = true
        try {
            val playing = path != null && audio.play(path) { if (token == audioToken) ended.complete(Unit) }
            if (playing) {
                if (withTimeoutOrNull(WALK_STEP_CAP_MS) { ended.await() } == null) audio.stop()
            } else {
                delay(MISSING_AUDIO_DELAY_MS)
            }
        } finally {
            audioPlaying = false
        }
    }

    private fun renderWalk(index: Int): StepUiState.Walkthrough {
        val step = steps[index]
        val visual = step.media(variantId)?.let { StepVisual(it.clip, it.still, animate = it.clip != null, scale = 1f, large = true) }
        return StepUiState.Walkthrough(
            stepIndex = index,
            stepCount = steps.size,
            sentence = step.textAr,
            visual = visual,
            reactionClip = if (visual == null) reaction(TALK) else null
        )
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

    private fun render(current: RunnerState): StepUiState = when {
        awaitingStart && current !is RunnerState.Error && current != RunnerState.Idle ->
            StepUiState.Start(taskName, steps.firstOrNull()?.media(variantId)?.still, reaction(IDLE), paused = localPaused)
        current is RunnerState.OnStep -> renderStep(current)
        current == RunnerState.Finished -> walkState?.copy(paused = localPaused) ?: StepUiState.Waiting
        current == RunnerState.Completed -> StepUiState.Finished(reaction(IDLE))
        else -> StepUiState.Waiting
    }

    private fun renderStep(current: RunnerState.OnStep): StepUiState {
        val step = steps.getOrNull(current.stepIndex) ?: return StepUiState.Waiting
        val level = current.prompt.level
        val media = step.media(variantId)
        val visual =
            media?.let {
                when {
                    level >= PromptState.MAX_LEVEL -> StepVisual(it.clip, it.still, animate = it.clip != null, scale = 1f, large = true)
                    level == 2 -> StepVisual(it.clip, it.still, animate = false, scale = LEVEL_2_SCALE, large = false)
                    else -> StepVisual(it.clip, it.still, animate = false, scale = 1f, large = false)
                }
            }
        val listening = current is RunnerState.Listen && audioPlaying
        val paused = current is RunnerState.Paused
        val reactionState =
            when {
                paused -> IDLE
                level >= 2 -> POINT
                listening -> TALK
                else -> IDLE
            }
        // Level 3 shows the moving demonstration large; the reaction character hides so only one clip moves.
        val hideReaction = !paused && visual != null && level >= PromptState.MAX_LEVEL
        return StepUiState.Step(
            stepIndex = current.stepIndex,
            stepCount = steps.size,
            sentence = step.textAr,
            visual = if (paused) visual?.copy(animate = false) else visual,
            reactionClip = if (hideReaction) null else reaction(reactionState),
            doneEnabled = current is RunnerState.Attempt,
            promptLevel = level,
            paused = paused
        )
    }

    private fun reaction(name: String): String? = variant?.reactions?.let { list ->
        (
            list.firstOrNull { it.state == name }
                ?: list.firstOrNull()
            )?.file
    }

    override fun onCleared() {
        audio.stop()
        // Leaving child mode (Exit + PIN) keeps the session resumable: it is saved `paused`, never abandoned.
        runner?.let { target -> outerScope.launch { target.pause() } }
    }

    companion object {
        private const val TAG = "DlsChild"
        const val MISSING_AUDIO_DELAY_MS = 1_500L

        /** Longest a walkthrough step waits for its audio to end. */
        const val WALK_STEP_CAP_MS = 15_000L
        const val TICK_MS = 1_000L
        const val RETRY_MS = 2_000L
        const val LEVEL_2_SCALE = 1.15f

        // Reaction clip states from the character index (Story 2.3).
        private const val IDLE = "idle"
        private const val TALK = "talk"
        private const val POINT = "point"
    }
}
