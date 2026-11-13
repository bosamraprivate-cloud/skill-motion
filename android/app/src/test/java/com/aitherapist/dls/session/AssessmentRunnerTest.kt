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

    private fun TestScope.runner(verifier: Verifier, steps: List<Step> = listOf(Step("s1", Method.POSE), Step("s2", Method.POSE))) =
        AssessmentRunner(
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
    fun `cannot tell twice repeats the number then moves on`() = runTest {
        val r = runner(FakeVerifier(Outcome.CANNOT_TELL, Outcome.CANNOT_TELL))
        r.start()
        r.attemptDone()
        assertEquals(RunnerState.Listen(0, "s1", PromptState(1, false, 1)), r.state.value)
        r.attemptDone()
        assertEquals(listOf(1, 1), rowsFor("s1").map { it.number })
        assertEquals(1, stepIndex(r))
    }

    @Test
    fun `cannot tell then done keeps the number`() = runTest {
        val r = runner(FakeVerifier(Outcome.CANNOT_TELL, Outcome.DONE))
        r.start()
        r.attemptDone()
        r.attemptDone()
        assertEquals(listOf(1 to AttemptVerdict.CANNOT_TELL, 1 to AttemptVerdict.DONE), rowsFor("s1").map { it.number to it.verdict })
        assertEquals(1, stepIndex(r))
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
    fun `not done then cannot tell repeats attempt 2 at level 2`() = runTest {
        val r = runner(FakeVerifier(Outcome.NOT_DONE, Outcome.CANNOT_TELL))
        r.start()
        r.attemptDone()
        r.attemptDone()
        assertEquals(RunnerState.Listen(0, "s1", PromptState(2, false, 2)), r.state.value)
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
        assertEquals(RunnerState.Paused(0, "s1", PromptState()), r.state.value)
        r.resume()
        assertEquals(RunnerState.Listen(0, "s1", PromptState()), r.state.value)
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
}
