package com.aitherapist.dls.session

import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Step
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.SessionEntity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssessmentRunnerTest {
    private val log = mutableListOf<String>()

    private inner class FakeStore : SessionStore {
        var session: SessionEntity? = null
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
            log += "save"
        }

        override suspend fun update(session: SessionEntity) {
            maybeFail()
            this.session = session
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
        assertEquals(SessionState.DONE, store.session!!.state)
        assertNotNull(store.session!!.endedAt)
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
}
