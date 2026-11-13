package com.aitherapist.dls.session

import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Step
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionKind
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.data.SessionStore
import com.aitherapist.dls.data.VerifierKind
import com.aitherapist.dls.scoring.RuleAttempt
import com.aitherapist.dls.scoring.RuleOutcome
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.vision.Frame
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.NoneVerifier
import com.aitherapist.dls.vision.Outcome
import com.aitherapist.dls.vision.Verdict
import com.aitherapist.dls.vision.VerdictReason
import com.aitherapist.dls.vision.Verifier
import com.aitherapist.dls.vision.VerifyContext
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Inputs to [AssessmentRunner.onEvent]. */
sealed interface RunnerEvent {
    /** Instruction audio finished: the attempt window opens. */
    data object AudioEnded : RunnerEvent

    /** The child (or a paired HID key) pressed Done. */
    data object Done : RunnerEvent

    /** Help: next prompt level (cap 3), counts as a prompt, replays the prompt. */
    data object Help : RunnerEvent

    /** Repeat: replays the current prompt level, changes nothing. */
    data object Repeat : RunnerEvent

    /** A pose sample with the frame and landmarks captured with it, if any. */
    data class Sample(val sample: PoseSample, val frame: Frame? = null, val landmarks: LandmarkFrame? = null) : RunnerEvent

    /** Time passed: re-check the window close rules at the clock's time. */
    data object Tick : RunnerEvent

    /** A verdict supplied from outside (e.g. replay) for the window in JUDGE. */
    data class VerdictReady(val verdict: Verdict) : RunnerEvent

    /** After [RunnerState.Error]: repeat the failed save. */
    data object Retry : RunnerEvent
}

/**
 * Assessment runner (design "Session runner", AD-6). Every state change is saved through [store] before it
 * is emitted; one attempt row per closed window; the step advances only by [RulesEngine.stepLevel] on the
 * saved rows. A failed step never stops the task. Pure Kotlin + coroutines, no UI, audio or camera.
 */
class AssessmentRunner(
    private val sessionId: String,
    private val childId: String,
    private val taskId: String,
    private val catalogVersion: String,
    private val steps: List<Step>,
    private val store: SessionStore,
    private val verifier: Verifier,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
    private val ctx: VerifyContext = VerifyContext(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Wall time for stored timestamps; [clock] (monotonic in the app) times windows and latency. */
    private val wallClock: Clock = clock
) {
    private val mutableState = MutableStateFlow<RunnerState>(RunnerState.Idle)
    val state: StateFlow<RunnerState> = mutableState.asStateFlow()

    private val mutex = Mutex()
    private var session: SessionEntity? = null
    private var window: AttemptWindow? = null

    /** In-memory evidence for the open window only; evidence-frame persistence (framePaths) comes in Story 3.5. */
    private val frames = mutableListOf<Frame>()
    private val landmarks = mutableListOf<LandmarkFrame>()
    private var pending: (suspend () -> Unit)? = null

    /** Saves the new running session and shows the first step. */
    suspend fun start() = serialized {
        if (mutableState.value != RunnerState.Idle) return@serialized
        val now = wallClock.nowMs()
        val first =
            SessionEntity(
                id = sessionId,
                childId = childId,
                taskId = taskId,
                kind = SessionKind.ASSESSMENT,
                state = SessionState.RUNNING,
                startedAt = now,
                endedAt = null,
                catalogVersion = catalogVersion,
                rulesVersion = RulesEngine.RULES_VERSION,
                stepIndex = 0,
                attemptNumber = 1,
                promptLevel = PromptState.MIN_LEVEL
            )
        guarded {
            // Save the new session first (a failed add keeps the old one resumable), then abandon older ones:
            // at most one running/paused session per child.
            if (session == null) {
                store.add(first)
                session = first
            }
            store.resumable(childId).filter { it.id != sessionId }.forEach {
                store.update(it.copy(state = SessionState.ABANDONED, endedAt = now))
            }
            if (steps.isEmpty()) mutableState.value = RunnerState.Finished else enterPrompt(0, PromptState())
        }
    }

    /**
     * Continues a saved running/paused session ([sessionId] must be its id): the interrupted attempt restarts from
     * LISTEN at the saved step, attempt number and prompt level; earlier rows are kept. A session saved after its
     * last step (stepIndex == step count, not yet completed) continues at [RunnerState.Finished]. A resume point outside the
     * task, another task or another catalogue version marks the session `abandoned` and returns false (the runner
     * stays [RunnerState.Idle]).
     */
    suspend fun restore(saved: SessionEntity): Boolean = serialized {
        if (mutableState.value != RunnerState.Idle || saved.id != sessionId) return@serialized false
        if (saved.state != SessionState.RUNNING && saved.state != SessionState.PAUSED) return@serialized false
        val index = saved.stepIndex
        val valid =
            saved.taskId == taskId &&
                saved.catalogVersion == catalogVersion &&
                (index in steps.indices || index == steps.size) &&
                saved.attemptNumber >= 1 &&
                saved.promptLevel in PromptState.MIN_LEVEL..PromptState.MAX_LEVEL
        if (!valid) {
            quietly { store.update(saved.copy(state = SessionState.ABANDONED, endedAt = wallClock.nowMs())) }
            return@serialized false
        }
        session = saved
        if (index == steps.size) {
            guarded {
                val running = saved.copy(state = SessionState.RUNNING)
                store.update(running)
                session = running
                resetWindow()
                mutableState.value = RunnerState.Finished
            }
            return@serialized true
        }
        guarded {
            val prompt =
                PromptState(
                    level = saved.promptLevel,
                    helpPressed = saved.promptLevel > baseLevel(store.attempts(sessionId, steps[index].id), saved.attemptNumber),
                    attempt = saved.attemptNumber
                )
            val running = saved.copy(state = SessionState.RUNNING)
            store.update(running)
            session = running
            enterPrompt(index, prompt)
        }
        true
    }

    /** True when any attempt row is saved for this session (Continue shows the start screen when none). */
    suspend fun hasAttempts(): Boolean = serialized { steps.any { store.attempts(sessionId, it.id).isNotEmpty() } }

    /** Adult pause: the open window is dropped (no row), the session is saved `paused`, then [RunnerState.Paused] shows. */
    suspend fun pause() = serialized {
        val current = mutableState.value
        if (current is RunnerState.Error) {
            // A save is failing: try to mark the session paused anyway; on success the failed action is dropped
            // (the attempt restarts on Resume) and the screen shows Paused.
            val saved = session ?: return@serialized
            if (saved.state != SessionState.RUNNING) return@serialized
            val prev = current.previous as? RunnerState.OnStep
            val paused =
                prev?.let {
                    saved.copy(
                        state = SessionState.PAUSED,
                        stepIndex = it.stepIndex,
                        attemptNumber = it.prompt.attempt,
                        promptLevel = it.prompt.level
                    )
                } ?: saved.copy(state = SessionState.PAUSED)
            quietly {
                store.update(paused)
                session = paused
                if (prev != null && prev !is RunnerState.Paused) {
                    pending = null
                    resetWindow()
                    mutableState.value = RunnerState.Paused(prev.stepIndex, prev.stepId, prev.prompt)
                }
            }
            return@serialized
        }
        // Judge: the verdict is being recorded; pausing then could drop or duplicate a row, so it is ignored.
        if (current !is RunnerState.OnStep || current is RunnerState.Paused || current is RunnerState.Judge) return@serialized
        guarded {
            val paused =
                requireNotNull(session).copy(
                    state = SessionState.PAUSED,
                    stepIndex = current.stepIndex,
                    attemptNumber = current.prompt.attempt,
                    promptLevel = current.prompt.level
                )
            store.update(paused)
            session = paused
            resetWindow()
            mutableState.value = RunnerState.Paused(current.stepIndex, current.stepId, current.prompt)
        }
    }

    /** Ends a pause: the session is saved `running` and the current attempt restarts from LISTEN at the same prompt. */
    suspend fun resume() = serialized {
        val current = mutableState.value as? RunnerState.Paused ?: return@serialized
        guarded {
            val running = requireNotNull(session).copy(state = SessionState.RUNNING)
            store.update(running)
            session = running
            enterPrompt(current.stepIndex, current.prompt)
        }
    }

    /**
     * After the last step: the [RunnerState.Finished] session is saved `done` and
     * [RunnerState.Completed] shows. Ignored in any other state.
     */
    suspend fun complete() = serialized {
        if (mutableState.value != RunnerState.Finished) return@serialized
        guarded {
            val done = requireNotNull(session).copy(state = SessionState.DONE, endedAt = wallClock.nowMs())
            store.update(done)
            session = done
            mutableState.value = RunnerState.Completed
        }
    }

    /** Child mode left before the end: a running or paused session becomes `abandoned`. A failed save is ignored. */
    suspend fun abandon() = serialized {
        val current = session ?: return@serialized
        if (current.state != SessionState.RUNNING && current.state != SessionState.PAUSED) return@serialized
        val abandoned = current.copy(state = SessionState.ABANDONED, endedAt = wallClock.nowMs())
        try {
            store.update(abandoned)
            session = abandoned
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing to show: Story 2.15 offers to continue a session left running.
        }
    }

    suspend fun onEvent(event: RunnerEvent) = serialized {
        val current = mutableState.value
        if (current is RunnerState.Error) {
            if (event == RunnerEvent.Retry) pending?.let { guarded(it) }
            return@serialized
        }
        if (current !is RunnerState.OnStep) return@serialized
        when (event) {
            RunnerEvent.AudioEnded -> if (current is RunnerState.Listen) openWindow(current)
            RunnerEvent.Help -> if (current.isPrompting()) guarded { enterPrompt(current.stepIndex, PromptLadder.onHelp(current.prompt)) }
            RunnerEvent.Repeat -> if (current.isPrompting()) {
                guarded {
                    enterPrompt(current.stepIndex, PromptLadder.onRepeat(current.prompt))
                }
            }
            RunnerEvent.Done -> if (current is RunnerState.Attempt) {
                window?.onDone(clock.nowMs())
                checkWindow(current)
            }
            is RunnerEvent.Sample -> if (current is RunnerState.Attempt) onSample(current, event)
            RunnerEvent.Tick -> if (current is RunnerState.Attempt) checkWindow(current)
            is RunnerEvent.VerdictReady -> if (current is RunnerState.Judge) {
                val row = attemptRow(current, event.verdict)
                guarded { record(current, row) }
            }
            RunnerEvent.Retry -> Unit
        }
    }

    private suspend fun <T> serialized(block: suspend () -> T): T = withContext(dispatcher) { mutex.withLock { block() } }

    /** Runs [action]; a store failure emits [RunnerState.Error] with the state unchanged and [action] kept for retry. */
    private suspend fun guarded(action: suspend () -> Unit) {
        val before = (mutableState.value as? RunnerState.Error)?.previous ?: mutableState.value
        try {
            action()
            pending = null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            pending = action
            mutableState.value = RunnerState.Error(before)
        }
    }

    /** Runs [action] ignoring a store failure (the caller has nothing to show). */
    private suspend fun quietly(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Left as it was; nothing is shown to the child.
        }
    }

    /** Prompt level an attempt [number] starts at, given the step's saved rows (same rule as [advance]). */
    private fun baseLevel(rows: List<AttemptEntity>, number: Int): Int {
        val last = rows.lastOrNull() ?: return PromptState.MIN_LEVEL
        return when {
            last.verdict == AttemptVerdict.CANNOT_TELL && last.number == number -> last.promptLevel
            else -> (last.promptLevel + 1).coerceAtMost(PromptState.MAX_LEVEL)
        }
    }

    private fun RunnerState.OnStep.isPrompting() = this is RunnerState.Show || this is RunnerState.Listen || this is RunnerState.Attempt

    /** SHOW then LISTEN for [prompt] on step [index]; the window (if any) is discarded. */
    private suspend fun enterPrompt(index: Int, prompt: PromptState) {
        val stepId = steps[index].id
        save(index, prompt)
        resetWindow()
        mutableState.value = RunnerState.Show(index, stepId, prompt)
        mutableState.value = RunnerState.Listen(index, stepId, prompt)
    }

    private suspend fun openWindow(listen: RunnerState.Listen) {
        guarded {
            val opened = clock.nowMs()
            save(listen.stepIndex, listen.prompt)
            resetWindow()
            window = AttemptWindow(opened)
            mutableState.value = RunnerState.Attempt(listen.stepIndex, listen.stepId, listen.prompt, opened)
        }
    }

    private suspend fun onSample(current: RunnerState.Attempt, event: RunnerEvent.Sample) {
        window?.onSample(event.sample)
        event.frame?.let { frames.addCapped(it, MAX_FRAMES) }
        event.landmarks?.let { landmarks.addCapped(it, MAX_LANDMARKS) }
        checkWindow(current)
    }

    private suspend fun checkWindow(current: RunnerState.Attempt) {
        if (window?.check(clock) == null) return
        val judge = RunnerState.Judge(current.stepIndex, current.stepId, current.prompt)
        var row: AttemptEntity? = null
        // One retryable action: save -> judge -> record; the row is built once so a retry never duplicates it.
        guarded {
            save(current.stepIndex, current.prompt)
            mutableState.value = judge
            val built = row ?: attemptRow(judge, judge(steps[current.stepIndex])).also { row = it }
            record(judge, built)
        }
    }

    /** Runs the step's verifier; any exception becomes cannot_tell `verifier_error`, never not_done. */
    private suspend fun judge(step: Step): Verdict {
        val started = clock.nowMs()
        val chosen = if (step.method == Method.NONE) NoneVerifier else verifier
        return withTimeoutOrNull(VERIFY_TIMEOUT_MS) {
            try {
                chosen.verify(step, frames.toList(), landmarks.toList(), ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Verdict(Outcome.CANNOT_TELL, VerdictReason.VERIFIER_ERROR, latencyMs = clock.nowMs() - started)
            }
        } ?: Verdict(Outcome.CANNOT_TELL, VerdictReason.TIMEOUT, latencyMs = clock.nowMs() - started)
    }

    private fun <T> MutableList<T>.addCapped(item: T, max: Int) {
        add(item)
        if (size > max) removeAt(0)
    }

    /** The attempt row for the closed window; built once so a retried save never inserts it twice. */
    private fun attemptRow(judge: RunnerState.Judge, verdict: Verdict): AttemptEntity {
        val step = steps[judge.stepIndex]
        return AttemptEntity(
            id = newId(),
            sessionId = sessionId,
            stepId = step.id,
            number = judge.prompt.attempt,
            promptLevel = judge.prompt.level,
            helpPressed = judge.prompt.helpPressed,
            verdict = verdict.outcome.toStorage(),
            reason = verdict.reason,
            verifier = step.method.toVerifierKind(),
            modelId = verdict.modelId,
            promptVersion = verdict.promptVersion,
            latencyMs = verdict.latencyMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            framePaths = emptyList(),
            clipFile = null,
            createdAt = wallClock.nowMs()
        )
    }

    /** Saves [row] unless already saved, then advances by Rule 1 on the saved rows. */
    private suspend fun record(judge: RunnerState.Judge, row: AttemptEntity) {
        if (store.attempts(sessionId, row.stepId).none { it.id == row.id }) store.addAttempt(row)
        advance(judge, store.attempts(sessionId, row.stepId))
    }

    private suspend fun advance(judge: RunnerState.Judge, rows: List<AttemptEntity>) {
        val level = RulesEngine.stepLevel(rows.map { it.toRule() })
        if (level != null) {
            nextStep(judge.stepIndex)
            return
        }
        val last = rows.last()
        val next =
            when (last.verdict) {
                AttemptVerdict.CANNOT_TELL -> PromptState(level = last.promptLevel, attempt = last.number)
                else -> PromptState(level = (last.promptLevel + 1).coerceAtMost(PromptState.MAX_LEVEL), attempt = last.number + 1)
            }
        enterPrompt(judge.stepIndex, next)
    }

    private suspend fun nextStep(index: Int) {
        val next = index + 1
        if (next < steps.size) {
            enterPrompt(next, PromptState())
            return
        }
        // Every step is recorded; the session stays resumable (running, past the last step) until the view model completes it
        // ends and [complete] saves it `done`.
        resetWindow()
        save(steps.size, PromptState())
        mutableState.value = RunnerState.Finished
    }

    private suspend fun save(index: Int, prompt: PromptState) {
        val updated = requireNotNull(session).copy(stepIndex = index, attemptNumber = prompt.attempt, promptLevel = prompt.level)
        store.update(updated)
        session = updated
    }

    private fun resetWindow() {
        window = null
        frames.clear()
        landmarks.clear()
    }

    private companion object {
        /** A hanging verifier becomes cannot_tell `timeout` after this (FR-20: never not_done). */
        const val VERIFY_TIMEOUT_MS = 10_000L
        const val MAX_FRAMES = 90
        const val MAX_LANDMARKS = 300
    }

    private fun Outcome.toStorage() = when (this) {
        Outcome.DONE -> AttemptVerdict.DONE
        Outcome.NOT_DONE -> AttemptVerdict.NOT_DONE
        Outcome.CANNOT_TELL -> AttemptVerdict.CANNOT_TELL
    }

    private fun Method.toVerifierKind() = when (this) {
        Method.POSE -> VerifierKind.POSE
        Method.HAND -> VerifierKind.HAND
        Method.CLOUD -> VerifierKind.CLOUD
        Method.NONE -> VerifierKind.NONE
    }

    private fun AttemptEntity.toRule() = RuleAttempt(
        number = number,
        promptLevel = promptLevel,
        outcome =
        when (verdict) {
            AttemptVerdict.DONE -> RuleOutcome.DONE
            AttemptVerdict.NOT_DONE -> RuleOutcome.NOT_DONE
            AttemptVerdict.CANNOT_TELL -> RuleOutcome.CANNOT_TELL
        },
        verifierNone = verifier == VerifierKind.NONE
    )
}
