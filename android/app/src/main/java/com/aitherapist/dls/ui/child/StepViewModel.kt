package com.aitherapist.dls.ui.child

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.audio.AudioPlayer
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CharacterVariant
import com.aitherapist.dls.data.ChildProfiles
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val audio: AudioPlayer,
    private val keys: ChildModeKeys,
    private val clock: Clock,
    /** Outlives this ViewModel: marks the session abandoned after the step screen is gone. */
    private val outerScope: CoroutineScope
) : ViewModel() {
    private val state = MutableStateFlow<StepUiState>(StepUiState.Waiting)
    val uiState: StateFlow<StepUiState> = state.asStateFlow()

    private val doneDebouncer = DoneDebouncer(clock)
    private val helpDebouncer = DoneDebouncer(clock)
    private val repeatDebouncer = DoneDebouncer(clock)
    private var paused = false
    private var started = false
    private var runner: AssessmentRunner? = null
    private var steps: List<CatalogStep> = emptyList()
    private var variant: CharacterVariant? = null
    private var variantId: String = ""

    /** The LISTEN state the audio was started for (by reference: every prompt entry is a new object). */
    private var audioFor: RunnerState.Listen? = null
    private var audioPlaying = false
    private var audioToken = 0
    private var fallbackJob: Job? = null
    private var tickJob: Job? = null
    private var retryJob: Job? = null

    init {
        viewModelScope.launch { keys.events.collect { onEvent(StepEvent.Done) } }
    }

    /** Starts a new assessment session for [childId] on [taskId]; later calls are ignored. */
    fun start(childId: String, taskId: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                val loaded = catalog.load().getOrThrow()
                val child = requireNotNull(children.get(childId)) { "child missing" }
                val task = requireNotNull(loaded.tasks.firstOrNull { it.id == taskId }) { "task missing" }
                variantId = child.characterVariant.name.lowercase(Locale.ROOT)
                variant = runCatching { characters.load().variants.firstOrNull { it.id == variantId } }.getOrNull()
                steps = task.steps
                val created = runners.create(childId, taskId, loaded.catalogVersion, task.steps.map { it.toStep() })
                runner = created
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
                // The child only ever sees the calm wait visual; the adult leaves through the adult corner.
                state.value = StepUiState.Waiting
            }
        }
    }

    fun onEvent(event: StepEvent) {
        val current = runner ?: return
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
        if (paused) {
            stopAudio()
            stopTicking()
        } else if (current is RunnerState.Listen) {
            if (current !== audioFor) startAudio(current)
        } else {
            stopAudio()
        }
        if (!paused) {
            if (current is RunnerState.Attempt) ensureTicking() else stopTicking()
        }
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

    private fun render(current: RunnerState): StepUiState = when (current) {
        is RunnerState.OnStep -> renderStep(current)
        RunnerState.Finished -> StepUiState.Finished(reaction(IDLE))
        RunnerState.Idle, is RunnerState.Error -> StepUiState.Waiting
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
        val reactionState =
            when {
                level >= 2 -> POINT
                listening -> TALK
                else -> IDLE
            }
        // Level 3 shows the moving demonstration large; the reaction character hides so only one clip moves.
        val hideReaction = visual != null && level >= PromptState.MAX_LEVEL
        return StepUiState.Step(
            stepIndex = current.stepIndex,
            stepCount = steps.size,
            sentence = step.textAr,
            visual = visual,
            reactionClip = if (hideReaction) null else reaction(reactionState),
            doneEnabled = current is RunnerState.Attempt,
            promptLevel = level
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
        runner?.let { target -> outerScope.launch { target.abandon() } }
    }

    companion object {
        private const val TAG = "DlsChild"
        const val MISSING_AUDIO_DELAY_MS = 1_500L
        const val TICK_MS = 1_000L
        const val RETRY_MS = 2_000L
        const val LEVEL_2_SCALE = 1.15f

        // Reaction clip states from the character index (Story 2.3).
        private const val IDLE = "idle"
        private const val TALK = "talk"
        private const val POINT = "point"
    }
}
