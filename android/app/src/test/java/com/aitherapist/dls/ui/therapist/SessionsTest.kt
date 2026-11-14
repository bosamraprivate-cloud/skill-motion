package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.CorrectionEntity
import com.aitherapist.dls.data.RewardEntity
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionHistory
import com.aitherapist.dls.data.SessionKind
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.data.TrainingResultEntity
import com.aitherapist.dls.data.VerifierKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionsTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun step(task: String, n: Int) =
        CatalogStep("$task.0$n", "a", task, n, "ar $n", "", "", emptyList(), "", StepDetector(Method.POSE), null, emptyMap())

    private val catalog =
        Catalog(
            CatalogVersion("v", true, 5, 0),
            listOf(
                CatalogTask(
                    "t.a",
                    "a",
                    (1..5).map {
                        step("t.a", it)
                    },
                    nameAr = "Task A"
                )
            ),
            emptyList()
        )

    private fun session(id: String, state: SessionState, at: Long, end: Long? = at + 60_000) =
        SessionEntity(id, "c", "t.a", SessionKind.ASSESSMENT, state, at, end, "v", "4", 0, 1, 1)

    private fun attempt(step: String, n: Int, verdict: AttemptVerdict, level: Int = n) = AttemptEntity(
        "$step-$n",
        "s1",
        step,
        n,
        level,
        false,
        verdict,
        "r",
        VerifierKind.POSE,
        null,
        null,
        0,
        emptyList(),
        null,
        n.toLong()
    )

    private fun reward(id: String, session: String?, stars: Int, sticker: String? = null) =
        RewardEntity(id, "c", session, stars, sticker, 0)

    private fun training(id: String, activity: String, at: Long) = TrainingResultEntity(id, "c", activity, 5, 30, true, at)

    @Test
    fun `summary counts rule 1 levels cannot tell duration and stars`() {
        val attempts =
            listOf(
                attempt("t.a.01", 1, AttemptVerdict.DONE), // level 3
                attempt("t.a.02", 1, AttemptVerdict.NOT_DONE),
                attempt("t.a.02", 2, AttemptVerdict.DONE), // level 2
                attempt("t.a.03", 1, AttemptVerdict.CANNOT_TELL),
                attempt("t.a.03", 1, AttemptVerdict.CANNOT_TELL) // not scored
            )
        val corrections = listOf(CorrectionEntity("x", "s1", "t.a.04", 1, null, 5))
        val rewards = listOf(reward("step:s1:a", "s1", 2), reward("step:s1:b", "s1", 1), reward("step:s2:a", "s2", 2))
        val summary = buildSessionSummary(session("s1", SessionState.DONE, 1_000, 541_000), attempts, corrections, rewards, catalog)
        assertEquals("Task A", summary.taskName)
        assertEquals(1, summary.levelThree)
        assertEquals(1, summary.levelTwo)
        assertEquals(1, summary.levelOne)
        assertEquals(1, summary.notScored)
        assertEquals(1, summary.notReached) // t.a.05 never tried
        assertEquals(5, summary.steps)
        assertEquals(1, summary.cannotTell) // one step, however many cannot-tell attempts
        assertEquals(540_000L, summary.durationMs)
        assertEquals(3, summary.stars)
        assertEquals(HistoryState.DONE, summary.state)
    }

    @Test
    fun `list mixes assessments and training runs newest first with states and stars`() {
        val sessions =
            listOf(
                session("s2", SessionState.PAUSED, 300),
                session("s1", SessionState.DONE, 100),
                session("s0", SessionState.ABANDONED, 50)
            )
        val trainingRows = listOf(training("run1:0", "act.a", 200), training("run1:1", "act.b", 210), training("run2:0", "act.a", 400))
        val rewards =
            listOf(
                reward("step:s1:x", "s1", 2),
                reward("training:run1:0", null, 2),
                reward("training:run1:1", null, 1),
                reward("task:run:run1", null, 0, "sticker.01"),
                reward("training:run2:0", null, 2)
            )
        val list = buildSessionList(sessions, trainingRows, rewards, catalog)
        assertEquals(listOf("run2", "s2", "run1", "s1", "s0"), list.map { it.id })
        val run1 = list.first { it.id == "run1" }
        assertEquals(HistoryKind.TRAINING, run1.kind)
        assertEquals(HistoryState.DONE, run1.state)
        assertEquals(3, run1.stars)
        assertEquals(2, run1.activities)
        assertEquals(HistoryState.NOT_FINISHED, list.first { it.id == "run2" }.state)
        assertEquals(HistoryState.PAUSED, list.first { it.id == "s2" }.state)
        val s1 = list.first { it.id == "s1" }
        assertEquals("Task A", s1.title)
        assertEquals(2, s1.stars)
        assertTrue(s1.opensDetail)
        assertTrue(list.first { it.id == "s2" }.opensDetail)
        assertFalse(run1.opensDetail)

        val stats = buildOverviewStats(sessions, rewards)
        assertEquals(OverviewStats(lastAssessmentAt = 60_100, finishedTasks = 2, stickers = 1), stats)
    }

    @Test
    fun `no history gives empty stats`() {
        assertEquals(OverviewStats(null, 0, 0), buildOverviewStats(emptyList(), emptyList()))
        assertTrue(buildSessionList(emptyList(), emptyList(), emptyList(), null).isEmpty())
    }

    private class FakeHistory(
        val sessions: List<SessionEntity> = emptyList(),
        val attempts: List<AttemptEntity> = emptyList(),
        val fail: Boolean = false
    ) : SessionHistory {
        override suspend fun assessmentSessions(childId: String): List<SessionEntity> {
            if (fail) throw IllegalStateException("boom")
            return sessions
        }

        override suspend fun session(id: String) = sessions.firstOrNull { it.id == id && it.kind == SessionKind.ASSESSMENT }

        override suspend fun attempts(sessionId: String) = attempts.filter { it.sessionId == sessionId }

        override suspend fun corrections(sessionId: String) = emptyList<CorrectionEntity>()

        override suspend fun trainingResults(childId: String) = emptyList<TrainingResultEntity>()

        override suspend fun rewards(childId: String) = emptyList<RewardEntity>()
    }

    private class FakeCatalog(private val catalog: Catalog) : CatalogRepository {
        override suspend fun load(): Result<Catalog> = Result.success(catalog)
    }

    @Test
    fun `summary view model shows only the visit's done sessions in order`() = runTest {
        val history =
            FakeHistory(
                listOf(session("s2", SessionState.DONE, 500), session("s1", SessionState.DONE, 100), session("old", SessionState.DONE, 50))
            )
        val vm = SessionSummaryViewModel(history, FakeCatalog(catalog), dispatcher).apply { load("c", listOf("s1", "s2")) }
        val content = vm.uiState.value as SessionSummaryUiState.Content
        assertEquals(listOf("s1", "s2"), content.summaries.map { it.sessionId })
    }

    @Test
    fun `summary view model never shows stale or unfinished data`() = runTest {
        val paused = FakeHistory(listOf(session("p", SessionState.PAUSED, 100, null), session("old", SessionState.DONE, 50)))
        val vm = SessionSummaryViewModel(paused, FakeCatalog(catalog), dispatcher).apply { load("c", listOf("p")) }
        assertEquals(SessionSummaryUiState.NotFinished, vm.uiState.value)
        val replay = FakeHistory(listOf(session("r", SessionState.DONE, 100).copy(kind = SessionKind.REPLAY)))
        val r = SessionSummaryViewModel(replay, FakeCatalog(catalog), dispatcher).apply { load("c", listOf("r")) }
        assertEquals(SessionSummaryUiState.Empty, r.uiState.value)
        val empty = SessionSummaryViewModel(FakeHistory(), FakeCatalog(catalog), dispatcher).apply { load("c", emptyList()) }
        assertEquals(SessionSummaryUiState.Empty, empty.uiState.value)
    }

    @Test
    fun `summary view model reports an error on failure`() = runTest {
        val history = object : SessionHistory by FakeHistory() {
            override suspend fun session(id: String): SessionEntity? = throw IllegalStateException("boom")
        }
        val failed = SessionSummaryViewModel(history, FakeCatalog(catalog), dispatcher).apply { load("c", listOf("x")) }
        assertTrue(failed.uiState.value is SessionSummaryUiState.Error)
    }

    @Test
    fun `sessions view model lists history and stats and retries after an error`() = runTest {
        val vm = SessionsViewModel(FakeHistory(fail = true), FakeCatalog(catalog), dispatcher).apply { load("c") }
        assertTrue(vm.uiState.value is SessionsUiState.Error)
        val ok = SessionsViewModel(FakeHistory(listOf(session("s1", SessionState.DONE, 100))), FakeCatalog(catalog), dispatcher)
        ok.load("c")
        val content = ok.uiState.value as SessionsUiState.Content
        assertEquals(1, content.items.size)
        assertEquals(1, content.stats.finishedTasks)
    }
}
