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
import com.aitherapist.dls.rewards.FakeRewardStore
import com.aitherapist.dls.rewards.RewardRecorder
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.vision.Frame
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.Outcome
import com.aitherapist.dls.vision.Verdict
import com.aitherapist.dls.vision.VerdictReason
import com.aitherapist.dls.vision.Verifier
import com.aitherapist.dls.vision.VerifyContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssessmentRunnerTest {
    private val log = mutableListOf<String>()

    private inner class FakeStore : SessionStore {
        var session: SessionEntity? = null
        val sessions = linkedMapOf<String, SessionEntity>()
        val rows = mutableListOf<AttemptEntity>()
        var failNext = 0
        var failRow = false

        private fun maybeFail() {
            if (failNext > 0) {
                failNext--
                error("disk full")
            }
        }

        override suspend fun add(session: SessionEntity) {
            maybeFail()
            this.session = session
            sessions[session.id] = session
            log += "save"
        }

        override suspend fun update(session: SessionEntity) {
            maybeFail()
            this.session = session
            sessions[session.id] = session
            log += "save"
        }

        override suspend fun addAttempt(attempt: AttemptEntity) {
            maybeFail()
            if (failRow) {
                failRow = false
                error("disk full")
            }
            rows += attempt
            log += "save"
        }

        override suspend fun resumable(childId: String) = sessions.values.filter {
            it.childId == childId && (it.state == SessionState.RUNNING || it.state == SessionState.PAUSED)
        }

        override suspend fun attempts(sessionId: String, stepId: String) = rows.filter { it.sessionId == sessionId && it.stepId == stepId }
    }

    private class FakeVerifier(vararg outcomes: Outcome) : Verifier {
        val queue = ArrayDeque(outcomes.toList())
        var throwNext = false

        override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict {
            if (throwNext) {
                throwNext = false
                error("boom")
            }
            return Verdict(queue.removeFirst(), VerdictReason.CHECK_PASSED, latencyMs = 5)
        }
    }

    private var now = 1_000L
    private val clock = Clock { now }
    private val store = FakeStore()
    private var ids = 0

    private class FakeImage : com.aitherapist.dls.vision.AttemptImage {
        var released = false
        override val width = 4
        override val height = 3

        override fun release() {
            released = true
        }
    }

    /** Records evidence writes: (attemptId, frame timestamps). */
    private val written = mutableListOf<Pair<String, List<Long>>>()
    private val discarded = mutableListOf<String>()
    private val evidence =
        object : EvidenceWriter {
            override suspend fun write(sessionId: String, attemptId: String, frames: List<Frame>): List<String> {
                written += attemptId to frames.map { it.timestampMs }
                return frames.indices.map { "frames/$sessionId/$attemptId-$it.bin" }
            }

            override fun discard(paths: List<String>) {
                discarded += paths
            }
        }

    private fun TestScope.runner(
        verifier: Verifier,
        steps: List<Step> = listOf(Step("s1", Method.POSE), Step("s2", Method.POSE)),
        kind: SessionKind = SessionKind.ASSESSMENT,
        nextTaskId: String? = null,
        rewards: RewardRecorder? = null
    ) = AssessmentRunner(
        rewards = rewards,
        kind = kind,
        nextTaskId = nextTaskId,
        evidence = evidence,
        sessionId = "sess",
        childId = "child",
        taskId = "task",
        catalogVersion = "cat-1",
        steps = steps,
        store = store,
        verifier = verifier,
        clock = clock,
        dispatcher = StandardTestDispatcher(testScheduler),
        newId = { "a${ids++}" }
    )

    /** Audio ends, then the child presses Done one second later. */
    private suspend fun AssessmentRunner.attemptDone() {
        onEvent(RunnerEvent.AudioEnded)
        now += 1_000
        onEvent(RunnerEvent.Done)
    }

    private fun rowsFor(stepId: String) = store.rows.filter { it.stepId == stepId }

    private fun stepIndex(r: AssessmentRunner) = (r.state.value as RunnerState.OnStep).stepIndex

    @Test
    fun `abandon marks a running session abandoned once, never a finished one, and ignores a failed save`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        now = 5_000
        store.failNext = 1
        r.abandon()
        assertEquals(SessionState.RUNNING, store.session!!.state)
        r.abandon()
        assertEquals(SessionState.ABANDONED, store.session!!.state)
        assertEquals(5_000L, store.session!!.endedAt)
        r.abandon()
        val done = runner(FakeVerifier(Outcome.DONE), steps = emptyList())
        done.start()
        done.complete()
        done.abandon()
        assertEquals(SessionState.DONE, store.session!!.state)
    }

    @Test
    fun `chained task id is saved on the new session`() = runTest {
        runner(FakeVerifier(Outcome.DONE), nextTaskId = "t.off").start()
        assertEquals("t.off", store.session!!.nextTaskId)
    }

    @Test
    fun `finished steps earn 2 then 1 star, after their rows are saved, once each`() = runTest {
        val rewards = FakeRewardStore(onWrite = { log += "reward" })
        val r = runner(FakeVerifier(Outcome.DONE, Outcome.NOT_DONE, Outcome.DONE), rewards = RewardRecorder(rewards))
        r.start()
        log.clear()
        r.attemptDone()
        // Session saves on window open/close, then the attempt row, then the reward, then the next step's save.
        val rewardAt = log.indexOf("reward")
        assertTrue(rewardAt > 0 && store.rows.size == 1)
        assertEquals("save", log[rewardAt - 1])
        r.attemptDone()
        r.attemptDone()
        assertEquals(RunnerState.Finished, r.state.value)
        assertEquals(listOf(2, 1), rewards.rows.values.map { it.stars })
        assertEquals(3, rewards.starsForSession("sess"))
    }

    @Test
    fun `a done on the cannot-tell repeat still earns the first-try bonus`() = runTest {
        val rewards = FakeRewardStore()
        val r =
            runner(
                FakeVerifier(Outcome.CANNOT_TELL, Outcome.DONE),
                steps = listOf(Step("c1", Method.POSE)),
                rewards = RewardRecorder(rewards)
            )
        r.start()
        r.attemptDone()
        r.attemptDone()
        assertEquals(RunnerState.Finished, r.state.value)
        assertEquals(2, rewards.starsForSession("sess"))
    }

    @Test
    fun `a failed fact save writes no reward and the retry rewards once`() = runTest {
        val rewards = FakeRewardStore()
        val r = runner(FakeVerifier(Outcome.DONE), rewards = RewardRecorder(rewards))
        r.start()
        store.failRow = true
        r.attemptDone()
        assertTrue(r.state.value is RunnerState.Error)
        assertTrue(rewards.rows.isEmpty())
        r.onEvent(RunnerEvent.Retry)
        r.onEvent(RunnerEvent.Retry)
        assertEquals(1, rewards.rows.size)
        assertEquals(2, rewards.starsForSession("sess"))
    }

    @Test
    fun `a failed reward write never stops the task, and not done after 3 earns 1 star`() = runTest {
        val failing = FakeRewardStore().apply { fail = true }
        val r = runner(FakeVerifier(Outcome.DONE), rewards = RewardRecorder(failing))
        r.start()
        r.attemptDone()
        assertEquals(1, stepIndex(r))
        val rewards = FakeRewardStore()
        val notDone =
            runner(
                FakeVerifier(Outcome.NOT_DONE, Outcome.NOT_DONE, Outcome.NOT_DONE),
                steps = listOf(Step("n1", Method.POSE)),
                rewards = RewardRecorder(rewards)
            )
        notDone.start()
        repeat(3) { notDone.attemptDone() }
        assertEquals(1, rewards.starsForSession("sess"))
    }

    @Test
    fun `done first try saves one row and moves on`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        assertEquals(RulesEngine.RULES_VERSION, store.session!!.rulesVersion)
        assertEquals("cat-1", store.session!!.catalogVersion)
        r.attemptDone()
        val row = rowsFor("s1").single()
        assertEquals(1, row.number)
        assertEquals(1, row.promptLevel)
        assertEquals(AttemptVerdict.DONE, row.verdict)
        assertEquals(RunnerState.Listen(1, "s2", PromptState()), r.state.value)
        assertEquals(1, store.session!!.stepIndex)
    }

    @Test
    fun `help then done saves prompt level 2 with help pressed`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.onEvent(RunnerEvent.Help)
        assertEquals(RunnerState.Listen(0, "s1", PromptState(2, true, 1)), r.state.value)
        assertEquals(2, store.session!!.promptLevel)
        assertTrue(store.rows.isEmpty())
        r.attemptDone()
        val row = rowsFor("s1").single()
        assertEquals(2, row.promptLevel)
        assertTrue(row.helpPressed)
        assertEquals(1, stepIndex(r))
    }

    @Test
    fun `repeat replays the current level unchanged`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.Repeat)
        assertEquals(RunnerState.Listen(0, "s1", PromptState()), r.state.value)
    }

    @Test
    fun `not done then done gives two rows with raised prompt`() = runTest {
        val r = runner(FakeVerifier(Outcome.NOT_DONE, Outcome.DONE))
        r.start()
        r.attemptDone()
        assertEquals(RunnerState.Listen(0, "s1", PromptState(2, false, 2)), r.state.value)
        r.attemptDone()
        assertEquals(listOf(1 to 1, 2 to 2), rowsFor("s1").map { it.number to it.promptLevel })
        assertEquals(1, stepIndex(r))
    }

    @Test
    fun `three not done gives level one rows and next step`() = runTest {
        val r = runner(FakeVerifier(Outcome.NOT_DONE, Outcome.NOT_DONE, Outcome.NOT_DONE))
        r.start()
        repeat(3) { r.attemptDone() }
        assertEquals(listOf(1 to 1, 2 to 2, 3 to 3), rowsFor("s1").map { it.number to it.promptLevel })
        assertEquals(RunnerState.Listen(1, "s2", PromptState()), r.state.value)
    }

    @Test
    fun `cannot tell twice repeats as the next attempt then moves on`() = runTest {
        // Phone bug (drink.cup.02): the repeat was saved as a second try=1 row.
        val r = runner(FakeVerifier(Outcome.CANNOT_TELL, Outcome.CANNOT_TELL))
        r.start()
        r.attemptDone()
        assertEquals(RunnerState.Listen(0, "s1", PromptState(1, false, 2)), r.state.value)
        r.attemptDone()
        assertEquals(listOf(1, 2), rowsFor("s1").map { it.number })
        assertEquals(1, stepIndex(r))
    }

    @Test
    fun `cannot tell then done is attempt 2 and still level three`() = runTest {
        val r = runner(FakeVerifier(Outcome.CANNOT_TELL, Outcome.DONE))
        r.start()
        r.attemptDone()
        r.attemptDone()
        assertEquals(listOf(1 to AttemptVerdict.CANNOT_TELL, 2 to AttemptVerdict.DONE), rowsFor("s1").map { it.number to it.verdict })
        assertEquals(1, stepIndex(r))
    }

    @Test
    fun `reason is always a code and the model text goes to modelReason`() = runTest {
        val verdicts =
            ArrayDeque(
                listOf(
                    Verdict(Outcome.CANNOT_TELL, VerdictReason.NETWORK, latencyMs = 5),
                    Verdict(Outcome.DONE, VerdictReason.MODEL, "m", "v1", 5, modelReason = "cup at lip")
                )
            )
        val r =
            runner(
                object : Verifier {
                    override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext) =
                        verdicts.removeFirst()
                }
            )
        r.start()
        r.attemptDone()
        r.attemptDone()
        assertEquals(listOf(VerdictReason.NETWORK, VerdictReason.MODEL), rowsFor("s1").map { it.reason })
        assertEquals(listOf(null, "cup at lip"), rowsFor("s1").map { it.modelReason })
        assertEquals(listOf(1, 2), rowsFor("s1").map { it.number })
    }

    @Test
    fun `model reason is capped at 300 chars with newlines collapsed`() = runTest {
        val long = "line one\n\nline   two " + "x".repeat(400)
        val r =
            runner(
                object : Verifier {
                    override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext) =
                        Verdict(Outcome.DONE, VerdictReason.MODEL, "m", "v1", 5, modelReason = long)
                }
            )
        r.start()
        r.attemptDone()
        val saved = rowsFor("s1").single().modelReason!!
        assertEquals(300, saved.length)
        assertTrue(saved.startsWith("line one line two x"))
    }

    @Test
    fun `method none saves a not judgeable row`() = runTest {
        val r = runner(FakeVerifier(), listOf(Step("n1", Method.NONE)))
        r.start()
        r.attemptDone()
        val row = rowsFor("n1").single()
        assertEquals(VerifierKind.NONE, row.verifier)
        assertEquals(AttemptVerdict.CANNOT_TELL, row.verdict)
        assertEquals(VerdictReason.NOT_JUDGEABLE, row.reason)
        assertEquals(RunnerState.Finished, r.state.value)
    }

    @Test
    fun `verifier exception becomes cannot tell`() = runTest {
        val r = runner(FakeVerifier().apply { throwNext = true })
        r.start()
        r.attemptDone()
        val row = rowsFor("s1").single()
        assertEquals(AttemptVerdict.CANNOT_TELL, row.verdict)
        assertEquals(VerdictReason.VERIFIER_ERROR, row.reason)
    }

    @Test
    fun `failed judge save then retry judges and records without duplicates`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        store.failNext = 1 // the Judge save
        r.onEvent(RunnerEvent.Done)
        val error = r.state.value as RunnerState.Error
        assertTrue(error.previous is RunnerState.Attempt)
        r.onEvent(RunnerEvent.Tick) // ignored while in error
        assertEquals(error, r.state.value)
        r.onEvent(RunnerEvent.Retry)
        assertEquals(1, store.rows.size)
        assertEquals(1, stepIndex(r))
    }

    @Test
    fun `failed row save then retry saves exactly one row`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        store.failNext = 0
        store.failRow = true
        r.onEvent(RunnerEvent.Done)
        assertTrue(r.state.value is RunnerState.Error)
        assertTrue(store.rows.isEmpty())
        r.onEvent(RunnerEvent.Retry)
        assertEquals(1, store.rows.size)
        assertEquals(1, stepIndex(r))
    }

    @Test
    fun `failed start save then retry shows the first step`() = runTest {
        val r = runner(FakeVerifier())
        store.failNext = 1
        r.start()
        assertEquals(RunnerState.Error(RunnerState.Idle), r.state.value)
        r.onEvent(RunnerEvent.Retry)
        assertEquals(RunnerState.Listen(0, "s1", PromptState()), r.state.value)
    }

    @Test
    fun `failed window open retries with a fresh open time`() = runTest {
        val r = runner(FakeVerifier())
        r.start()
        store.failNext = 1
        r.onEvent(RunnerEvent.AudioEnded)
        now += 5_000
        r.onEvent(RunnerEvent.Retry)
        assertEquals(now, (r.state.value as RunnerState.Attempt).openedAtMs)
    }

    @Test
    fun `hanging verifier times out as cannot tell`() = runTest {
        val hanging =
            object : Verifier {
                override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict =
                    awaitCancellation()
            }
        val r = runner(hanging)
        r.start()
        r.attemptDone()
        val row = rowsFor("s1").single()
        assertEquals(AttemptVerdict.CANNOT_TELL, row.verdict)
        assertEquals(VerdictReason.TIMEOUT, row.reason)
    }

    @Test
    fun `not done then cannot tell repeats as attempt 3 at level 2`() = runTest {
        val r = runner(FakeVerifier(Outcome.NOT_DONE, Outcome.CANNOT_TELL))
        r.start()
        r.attemptDone()
        r.attemptDone()
        assertEquals(RunnerState.Listen(0, "s1", PromptState(2, false, 3)), r.state.value)
    }

    @Test
    fun `help at level 3 stays 3`() = runTest {
        val r = runner(FakeVerifier())
        r.start()
        repeat(3) { r.onEvent(RunnerEvent.Help) }
        assertEquals(RunnerState.Listen(0, "s1", PromptState(3, true, 1)), r.state.value)
    }

    @Test
    fun `sample done and tick outside attempt are ignored`() = runTest {
        val r = runner(FakeVerifier())
        r.start()
        val before = r.state.value
        r.onEvent(RunnerEvent.Sample(PoseSample(now, emptyMap())))
        r.onEvent(RunnerEvent.Done)
        now += WindowRules.TIMEOUT_MS
        r.onEvent(RunnerEvent.Tick)
        r.onEvent(RunnerEvent.VerdictReady(Verdict(Outcome.DONE, VerdictReason.CHECK_PASSED, latencyMs = 1)))
        assertEquals(before, r.state.value)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `failed help save keeps the open attempt`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        store.failNext = 1
        r.onEvent(RunnerEvent.Help)
        assertTrue((r.state.value as RunnerState.Error).previous is RunnerState.Attempt)
        r.onEvent(RunnerEvent.Retry)
        assertEquals(RunnerState.Listen(0, "s1", PromptState(2, true, 1)), r.state.value)
    }

    @Test
    fun `last step finishes the session`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE, Outcome.NOT_DONE, Outcome.NOT_DONE, Outcome.NOT_DONE))
        r.start()
        repeat(4) { r.attemptDone() }
        assertEquals(RunnerState.Finished, r.state.value)
        // Resumable until the walkthrough ends: running, past the last step.
        assertEquals(SessionState.RUNNING, store.session!!.state)
        assertEquals(2, store.session!!.stepIndex)
        assertEquals(null, store.session!!.endedAt)
        now = 9_000
        r.complete()
        assertEquals(RunnerState.Completed, r.state.value)
        assertEquals(SessionState.DONE, store.session!!.state)
        assertEquals(9_000L, store.session!!.endedAt)
    }

    @Test
    fun `complete is ignored before the last step and retried after a failed save`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE), steps = listOf(Step("s1", Method.POSE)))
        r.start()
        r.complete()
        assertEquals(SessionState.RUNNING, store.session!!.state)
        assertTrue(r.state.value is RunnerState.Listen)
        r.attemptDone()
        store.failNext = 1
        r.complete()
        assertEquals(RunnerState.Error(RunnerState.Finished), r.state.value)
        assertEquals(SessionState.RUNNING, store.session!!.state)
        r.onEvent(RunnerEvent.Retry)
        assertEquals(RunnerState.Completed, r.state.value)
        assertEquals(SessionState.DONE, store.session!!.state)
    }

    @Test
    fun `restore after the last step continues at the walkthrough`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        assertTrue(r.restore(saved(state = SessionState.PAUSED, stepIndex = 2)))
        assertEquals(RunnerState.Finished, r.state.value)
        assertEquals(SessionState.RUNNING, store.sessions["sess"]!!.state)
        r.complete()
        assertEquals(SessionState.DONE, store.sessions["sess"]!!.state)
    }

    @Test
    fun `window closes on timeout via tick and samples feed the window`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.onEvent(RunnerEvent.Sample(PoseSample(now, emptyMap()), Frame(now), LandmarkFrame(now, null)))
        assertTrue(r.state.value is RunnerState.Attempt)
        now += WindowRules.TIMEOUT_MS
        r.onEvent(RunnerEvent.Tick)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `empty task finishes at once`() = runTest {
        val r = runner(FakeVerifier(), emptyList())
        r.start()
        assertEquals(RunnerState.Finished, r.state.value)
        assertEquals(SessionState.RUNNING, store.session!!.state)
        r.complete()
        assertEquals(SessionState.DONE, store.session!!.state)
    }

    @Test
    fun `every new state is saved before it is emitted`() = runTest {
        val r = runner(FakeVerifier(Outcome.NOT_DONE, Outcome.DONE, Outcome.CANNOT_TELL, Outcome.DONE))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            r.state.collect { if (it != RunnerState.Idle) log += "emit:${it::class.simpleName}" }
        }
        r.start()
        repeat(4) { r.attemptDone() }
        assertEquals(RunnerState.Finished, r.state.value)
        assertEquals("save", log.first())
        // Show, Attempt, Judge and Finished follow a save directly; Listen directly follows its Show.
        log.forEachIndexed { i, entry ->
            when (entry) {
                "emit:Listen" -> assertEquals("Listen must follow Show: $log", "emit:Show", log[i - 1])
                "save" -> Unit
                else -> assertEquals("$entry emitted without a save: $log", "save", log[i - 1])
            }
        }
    }

    private fun saved(
        id: String = "sess",
        childId: String = "child",
        state: SessionState = SessionState.RUNNING,
        stepIndex: Int = 0,
        attempt: Int = 1,
        level: Int = 1
    ) = SessionEntity(
        id = id,
        childId = childId,
        taskId = "task",
        kind = SessionKind.ASSESSMENT,
        state = state,
        startedAt = 10,
        endedAt = null,
        catalogVersion = "cat-1",
        rulesVersion = RulesEngine.RULES_VERSION,
        stepIndex = stepIndex,
        attemptNumber = attempt,
        promptLevel = level
    ).also { store.sessions[it.id] = it }

    @Test
    fun `pause mid attempt saves paused, writes no row and ignores child input`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.pause()
        assertEquals(RunnerState.Paused(0, "s1", PromptState()), r.state.value)
        assertEquals(SessionState.PAUSED, store.session!!.state)
        now += 60_000
        r.onEvent(RunnerEvent.Done)
        r.onEvent(RunnerEvent.Tick)
        r.onEvent(RunnerEvent.Help)
        assertTrue(store.rows.isEmpty())
        assertTrue(r.state.value is RunnerState.Paused)
    }

    @Test
    fun `resume restarts the attempt at the same prompt level and saves running`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.onEvent(RunnerEvent.Help)
        r.onEvent(RunnerEvent.AudioEnded)
        r.pause()
        r.resume()
        val prompt = PromptState(level = 2, helpPressed = true, attempt = 1)
        assertEquals(RunnerState.Listen(0, "s1", prompt), r.state.value)
        assertEquals(SessionState.RUNNING, store.session!!.state)
        assertEquals(2, store.session!!.promptLevel)
        r.attemptDone()
        assertEquals(1, store.rows.size)
        assertEquals(2, store.rows.single().promptLevel)
        assertTrue(store.rows.single().helpPressed)
        r.resume()
        assertEquals(1, stepIndex(r))
    }

    @Test
    fun `pause before start or after finish does nothing`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE), steps = listOf(Step("s1", Method.POSE)))
        r.pause()
        assertEquals(RunnerState.Idle, r.state.value)
        r.start()
        r.attemptDone()
        r.pause()
        assertEquals(RunnerState.Finished, r.state.value)
        assertEquals(SessionState.RUNNING, store.session!!.state)
    }

    @Test
    fun `restore continues at the saved step, attempt and level and keeps earlier rows`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE, Outcome.NOT_DONE))
        r.start()
        r.attemptDone() // s1 done
        r.attemptDone() // s2 not done -> attempt 2 at level 2
        val before = store.session!!
        assertEquals(1, before.stepIndex)
        assertEquals(2, before.attemptNumber)
        // Process death: the session row stays running; a new runner restores it.
        val restored = runner(FakeVerifier(Outcome.DONE))
        assertTrue(restored.restore(before))
        assertEquals(RunnerState.Listen(1, "s2", PromptState(level = 2, attempt = 2)), restored.state.value)
        assertEquals(2, store.rows.size)
        restored.attemptDone()
        assertEquals(RunnerState.Finished, restored.state.value)
        assertEquals(listOf(1, 2), rowsFor("s2").map { it.number })
    }

    @Test
    fun `restore of a paused session after help infers help pressed and saves running`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        assertTrue(r.restore(saved(state = SessionState.PAUSED, level = 2)))
        assertEquals(RunnerState.Listen(0, "s1", PromptState(level = 2, helpPressed = true, attempt = 1)), r.state.value)
        assertEquals(SessionState.RUNNING, store.sessions["sess"]!!.state)
    }

    @Test
    fun `corrupt resume point abandons the session`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        assertFalse(r.restore(saved(stepIndex = 5)))
        assertEquals(RunnerState.Idle, r.state.value)
        assertEquals(SessionState.ABANDONED, store.sessions["sess"]!!.state)
    }

    @Test
    fun `finished session is not restored`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        assertFalse(r.restore(saved(state = SessionState.DONE)))
        assertEquals(RunnerState.Idle, r.state.value)
    }

    @Test
    fun `new run abandons the child's running or paused session only`() = runTest {
        saved(id = "old1", state = SessionState.PAUSED)
        saved(id = "old2", state = SessionState.DONE)
        saved(id = "other", childId = "child2", state = SessionState.RUNNING)
        runner(FakeVerifier(Outcome.DONE)).start()
        assertEquals(SessionState.ABANDONED, store.sessions["old1"]!!.state)
        assertEquals(SessionState.DONE, store.sessions["old2"]!!.state)
        assertEquals(SessionState.RUNNING, store.sessions["other"]!!.state)
        assertEquals(SessionState.RUNNING, store.sessions["sess"]!!.state)
    }

    @Test
    fun `abandon also ends a paused session`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.pause()
        r.abandon()
        assertEquals(SessionState.ABANDONED, store.session!!.state)
    }

    @Test
    fun `failed add keeps the old session resumable until the new one is saved`() = runTest {
        saved(id = "old", state = SessionState.PAUSED)
        val r = runner(FakeVerifier(Outcome.DONE))
        store.failNext = 1
        r.start()
        assertTrue(r.state.value is RunnerState.Error)
        assertEquals(SessionState.PAUSED, store.sessions["old"]!!.state)
        r.onEvent(RunnerEvent.Retry)
        assertEquals(SessionState.ABANDONED, store.sessions["old"]!!.state)
        assertEquals(SessionState.RUNNING, store.sessions["sess"]!!.state)
        assertEquals(0, stepIndex(r))
    }

    @Test
    fun `restore of another task or catalogue version abandons the session`() = runTest {
        for (changed in listOf(saved().copy(taskId = "other"), saved().copy(catalogVersion = "cat-2"))) {
            store.sessions[changed.id] = changed
            val r = runner(FakeVerifier(Outcome.DONE))
            assertFalse(r.restore(changed))
            assertEquals(RunnerState.Idle, r.state.value)
            assertEquals(SessionState.ABANDONED, store.sessions["sess"]!!.state)
        }
    }

    @Test
    fun `pause while judging waits for the verdict and records exactly one row`() = runTest {
        val hanging =
            object : Verifier {
                override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict =
                    awaitCancellation()
            }
        val r = runner(hanging)
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        val judging = launch { r.onEvent(RunnerEvent.Done) }
        val pausing = launch { r.pause() }
        judging.join()
        pausing.join()
        assertEquals(1, store.rows.size)
        // The timed-out verdict is cannot tell: the repeat is attempt 2 at the same level.
        assertEquals(RunnerState.Paused(0, "s1", PromptState(attempt = 2)), r.state.value)
        r.resume()
        assertEquals(RunnerState.Listen(0, "s1", PromptState(attempt = 2)), r.state.value)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `pause during a save error shows paused and resume works`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        store.failNext = 1
        r.onEvent(RunnerEvent.AudioEnded)
        assertTrue(r.state.value is RunnerState.Error)
        r.pause()
        assertEquals(RunnerState.Paused(0, "s1", PromptState()), r.state.value)
        assertEquals(SessionState.PAUSED, store.session!!.state)
        r.onEvent(RunnerEvent.Retry)
        assertTrue(r.state.value is RunnerState.Paused)
        r.resume()
        assertEquals(RunnerState.Listen(0, "s1", PromptState()), r.state.value)
        r.attemptDone()
        assertEquals(1, store.rows.size)
        assertEquals(1, stepIndex(r))
    }

    private suspend fun AssessmentRunner.feed(count: Int) {
        repeat(count) {
            now += 100
            onEvent(RunnerEvent.Sample(PoseSample(now, emptyMap()), Frame(now, FakeImage()), LandmarkFrame(now, null)))
        }
    }

    @Test
    fun `closed window saves start, middle and end frames with its row`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.feed(5)
        r.onEvent(RunnerEvent.Done)
        val row = store.rows.single()
        assertEquals(listOf(row.id to listOf(1_100L, 1_300L, 1_500L)), written)
        assertEquals((0..2).map { "frames/sess/${row.id}-$it.bin" }, row.framePaths)
    }

    @Test
    fun `pause mid window drops the buffer and writes no frames`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.feed(5)
        r.pause()
        r.resume()
        r.onEvent(RunnerEvent.AudioEnded)
        r.onEvent(RunnerEvent.Done)
        assertTrue(written.isEmpty())
        assertTrue(store.rows.single().framePaths.isEmpty())
    }

    @Test
    fun `failed evidence write keeps the verdict without frames`() = runTest {
        val r =
            AssessmentRunner(
                "sess", "child", "task", "cat-1", listOf(Step("s1", Method.POSE)), store, FakeVerifier(Outcome.DONE), clock,
                StandardTestDispatcher(testScheduler), evidence = { _, _, _ -> error("disk") }
            )
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.feed(3)
        r.onEvent(RunnerEvent.Done)
        assertEquals(AttemptVerdict.DONE, store.rows.single().verdict)
        assertTrue(store.rows.single().framePaths.isEmpty())
    }

    @Test
    fun `long windows keep a bounded, thinned frame buffer covering the whole window`() = runTest {
        var seen = 0
        val counting =
            object : Verifier {
                override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext): Verdict {
                    seen = frames.size
                    return Verdict(Outcome.DONE, VerdictReason.CHECK_PASSED, latencyMs = 1)
                }
            }
        val r = runner(counting)
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.feed(200)
        r.onEvent(RunnerEvent.Done)
        assertTrue(seen in 1..24)
        assertEquals(1_100L, written.single().second.first())
    }

    @Test
    fun `cloud verdict stores model id and prompt version`() = runTest {
        val cloud =
            object : Verifier {
                override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext) =
                    Verdict(Outcome.DONE, "ok", modelId = "m-1", promptVersion = "p-1", latencyMs = 9)
            }
        val r = runner(cloud, steps = listOf(Step("s1", Method.CLOUD)))
        r.start()
        r.attemptDone()
        val row = store.rows.single()
        assertEquals("m-1", row.modelId)
        assertEquals("p-1", row.promptVersion)
        assertEquals(VerifierKind.CLOUD, row.verifier)
    }

    private fun visiblePose() = (0 until 33).associateWith { com.aitherapist.dls.vision.LandmarkPoint(0.5f, 0.5f, 0.9f) }

    @Test
    fun `samples captured before the window opened are dropped`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        val early = now - 50
        r.onEvent(RunnerEvent.Sample(PoseSample(early, emptyMap()), Frame(early, FakeImage()), LandmarkFrame(early, null)))
        r.feed(2)
        r.onEvent(RunnerEvent.Done)
        assertEquals(listOf(1_100L, 1_200L), written.single().second.distinct())
    }

    @Test
    fun `a still close with an unmet camera check is cannot tell, never not done`() = runTest {
        val r = runner(FakeVerifier(Outcome.NOT_DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        // Still child in frame (pose present, no movement) until the still rule closes the window.
        while (store.rows.isEmpty()) {
            now += 100
            r.onEvent(RunnerEvent.Sample(PoseSample(now, visiblePose()), null, LandmarkFrame(now, visiblePose().values.toList())))
        }
        assertEquals(AttemptVerdict.CANNOT_TELL, store.rows.single().verdict)
        assertEquals(VerdictReason.NOT_JUDGEABLE, store.rows.single().reason)
    }

    @Test
    fun `a failed row insert deletes its frames and the retry writes fresh ones`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.feed(3)
        store.failRow = true
        r.onEvent(RunnerEvent.Done)
        assertTrue(r.state.value is RunnerState.Error)
        assertEquals(3, discarded.size)
        r.onEvent(RunnerEvent.Retry)
        val row = store.rows.single()
        assertEquals((0..2).map { "frames/sess/${row.id}-$it.bin" }, row.framePaths)
        assertTrue(discarded.none { it in row.framePaths })
    }

    @Test
    fun `pausing during a failed save drops the pending row and its frames`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE))
        r.start()
        r.onEvent(RunnerEvent.AudioEnded)
        r.feed(3)
        store.failNext = 1 // the session save at window close fails, after nothing was written
        r.onEvent(RunnerEvent.Done)
        assertTrue(r.state.value is RunnerState.Error)
        r.pause()
        assertTrue(store.rows.isEmpty())
        assertEquals(written.flatMap { (id, ts) -> ts.indices.map { "frames/sess/$id-$it.bin" } }, discarded)
    }

    /* ---- Story 3.7 replay mode ---- */

    @Test
    fun `replay saves a replay session and one row per clip with clipFile, number and level from the name`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE, Outcome.NOT_DONE), kind = SessionKind.REPLAY)
        r.startReplay()
        r.startReplay() // second call is ignored
        assertEquals(SessionKind.REPLAY, store.session?.kind)
        assertEquals(RunnerState.Idle, r.state.value)
        val first =
            r.scoreClip(0, 1, 1, "s1__1__done__v.mp4") { sink ->
                (0L..4L).forEach { t -> sink(Frame(t * 100, FakeImage()), LandmarkFrame(t * 100, null)) }
                sink(null, null)
                true
            }
        val second = r.scoreClip(1, 3, 3, "s2__3__not_done__v.mp4") { true }
        assertEquals(AttemptVerdict.DONE, first.verdict)
        assertEquals("s1__1__done__v.mp4", first.clipFile)
        assertEquals(3, first.framePaths.size)
        assertEquals(AttemptVerdict.NOT_DONE, second.verdict)
        assertEquals(3, second.number)
        assertEquals(3, second.promptLevel)
        assertTrue(second.framePaths.isEmpty())
        assertEquals(2, store.rows.size)
        assertEquals("sess", r.id)
        r.finishReplay()
        assertEquals(SessionState.DONE, store.session?.state)
    }

    @Test
    fun `replay of an undecodable clip is cannot tell not_judgeable`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE), kind = SessionKind.REPLAY)
        r.startReplay()
        val failed = r.scoreClip(0, 1, 1, "a.mp4") { false }
        val threw = r.scoreClip(0, 2, 2, "b.mp4") { error("decode") }
        listOf(failed, threw).forEach {
            assertEquals(AttemptVerdict.CANNOT_TELL, it.verdict)
            assertEquals(VerdictReason.NOT_JUDGEABLE, it.reason)
        }
    }

    @Test
    fun `replay save failure discards the frames and throws`() = runTest {
        val r = runner(FakeVerifier(Outcome.DONE), kind = SessionKind.REPLAY)
        r.startReplay()
        store.failRow = true
        val result =
            runCatching {
                r.scoreClip(0, 1, 1, "a.mp4") { sink ->
                    sink(Frame(0, FakeImage()), null)
                    true
                }
            }
        assertTrue(result.isFailure)
        assertTrue(store.rows.isEmpty())
        assertEquals(1, discarded.size)
    }

    @Test
    fun `finishReplay before startReplay does nothing`() = runTest {
        val r = runner(FakeVerifier(), kind = SessionKind.REPLAY)
        r.finishReplay()
        assertEquals(null, store.session)
        assertTrue(runCatching { r.scoreClip(0, 1, 1, "a.mp4") { true } }.isFailure)
    }

    @Test
    fun `replay calls on an assessment runner are refused`() = runTest {
        val r = runner(FakeVerifier())
        assertTrue(runCatching { r.startReplay() }.isFailure)
        assertTrue(runCatching { r.scoreClip(0, 1, 1, "a.mp4") { true } }.isFailure)
        assertEquals(null, store.session)
    }
}
