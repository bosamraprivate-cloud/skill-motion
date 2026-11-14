package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Activity
import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.data.AssessmentResults
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.ChildEntity
import com.aitherapist.dls.data.CorrectionEntity
import com.aitherapist.dls.data.RewardEntity
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionHistory
import com.aitherapist.dls.data.SessionKind
import com.aitherapist.dls.data.SessionResult
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.data.TrainingResultEntity
import com.aitherapist.dls.data.VerifierKind
import com.aitherapist.dls.data.sessionLevels
import com.aitherapist.dls.scoring.StepLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun step(task: String, n: Int, tags: List<String>) =
        CatalogStep("$task.0$n", "a", task, n, "ar $task $n", "", "", tags, "", StepDetector(Method.POSE), null, emptyMap())

    private val taskA =
        CatalogTask("t.a", "a", listOf(step("t.a", 1, listOf("Hand skills")), step("t.a", 2, listOf("Hand skills"))), nameAr = "Task A")
    private val taskB = CatalogTask("t.b", "a", listOf(step("t.b", 1, listOf("Range of motion"))), nameAr = "Task B")
    private val activity = Activity("act.1", "mild", "hand", 1, "Arabic title", "", "", "", "", "", "", "")
    private val catalog = Catalog(CatalogVersion("v", true, 5, 0), listOf(taskA, taskB), listOf(activity))

    private fun session(id: String, task: String, at: Long) =
        SessionEntity(id, "c", task, SessionKind.ASSESSMENT, SessionState.DONE, at, at, "v", "4", 0, 1, 1)

    private class FakeResults(var rows: List<SessionResult> = emptyList(), var fail: Boolean = false) : AssessmentResults {
        override suspend fun results(childId: String, taskId: String?): List<SessionResult> {
            if (fail) throw IllegalStateException("boom")
            return rows
        }
    }

    private class FakeCatalog(private val catalog: Catalog) : CatalogRepository {
        override suspend fun load(): Result<Catalog> = Result.success(catalog)
    }

    private class FakeHistory(var training: List<TrainingResultEntity> = emptyList(), var fail: Boolean = false) : SessionHistory {
        override suspend fun assessmentSessions(childId: String) = emptyList<SessionEntity>()

        override suspend fun session(id: String): SessionEntity? = null

        override suspend fun attempts(sessionId: String) = emptyList<AttemptEntity>()

        override suspend fun corrections(sessionId: String) = emptyList<CorrectionEntity>()

        override suspend fun trainingResults(childId: String): List<TrainingResultEntity> {
            if (fail) throw IllegalStateException("boom")
            return training
        }

        override suspend fun rewards(childId: String) = emptyList<RewardEntity>()
    }

    private val a1 = SessionResult(session("a1", "t.a", 300), mapOf("t.a.01" to StepLevel.THREE, "t.a.02" to StepLevel.TWO))
    private val a2 = SessionResult(session("a2", "t.a", 100), mapOf("t.a.01" to StepLevel.ONE, "t.a.02" to StepLevel.TWO))
    private val b1 = SessionResult(session("b1", "t.b", 200), mapOf("t.b.01" to StepLevel.THREE))

    private fun child(eval: Boolean = false) =
        ChildEntity("c", "Name", "C-1", 6, characterVariant = ChildCharacter.BOY, isEval = eval, createdAt = 0)

    private fun compareVm(results: FakeResults, eval: Boolean = false) =
        CompareViewModel(results, FakeCatalog(catalog), FakeChildProfiles(child(eval)), dispatcher).apply { load("c") }

    @Test
    fun `eval child has nothing to compare`() = runTest {
        assertEquals(CompareUiState.NotEnough, compareVm(FakeResults(listOf(a1, a2)), eval = true).uiState.value)
    }

    @Test
    fun `compare is always older to newer whatever order was tapped`() = runTest {
        val vm = compareVm(FakeResults(listOf(a1, a2)))
        vm.selectFirst("a1")
        vm.selectSecond("a2")
        val result = requireNotNull((vm.uiState.value as CompareUiState.Content).result)
        assertEquals(100L, result.olderAt)
        assertEquals(300L, result.newerAt)
        assertEquals("1.5", result.rows.single().averageA)
        assertEquals("+1.0", result.rows.single().difference)
    }

    @Test
    fun `fewer than two sessions of a task gives the hint`() = runTest {
        val vm = compareVm(FakeResults(listOf(a1, b1)))
        assertEquals(CompareUiState.NotEnough, vm.uiState.value)
    }

    @Test
    fun `second picker offers only the same task and compares`() = runTest {
        val b2 = SessionResult(session("b2", "t.b", 50), mapOf("t.b.01" to StepLevel.TWO))
        val vm = compareVm(FakeResults(listOf(a1, b1, a2, b2)))
        vm.selectFirst("a2")
        var state = vm.uiState.value as CompareUiState.Content
        assertEquals(listOf("a1"), state.secondChoices.map { it.id })
        assertNull(state.result)
        vm.selectSecond("b1") // different task: ignored
        assertNull((vm.uiState.value as CompareUiState.Content).secondId)
        vm.selectSecond("a1")
        state = vm.uiState.value as CompareUiState.Content
        val result = requireNotNull(state.result)
        val row = result.rows.single()
        assertEquals("Hand skills", row.domain)
        assertEquals("1.5", row.averageA)
        assertEquals("2.5", row.averageB)
        assertEquals("+1.0", row.difference)
        assertEquals(Trend.UP, row.trend)
        assertEquals(2, result.commonSteps)
        // Picking a new first clears the second.
        vm.selectFirst("b1")
        state = vm.uiState.value as CompareUiState.Content
        assertEquals(listOf("b2"), state.secondChoices.map { it.id })
        assertNull(state.secondId)
        // Reload keeps the selection.
        vm.load("c")
        assertEquals("b1", (vm.uiState.value as CompareUiState.Content).firstId)
    }

    @Test
    fun `corrected step uses the effective level`() {
        val attempts =
            listOf(
                AttemptEntity(
                    "x", "s", "t.a.01", 1, 1, false, AttemptVerdict.DONE, "", VerifierKind.POSE, null, null, 0, emptyList(), null, 0L
                )
            )
        val corrected = sessionLevels(attempts, listOf(CorrectionEntity("c1", "s", "t.a.01", 1, null, 1L)))
        assertEquals(StepLevel.ONE, corrected["t.a.01"])
        val first = SessionResult(session("s", "t.a", 1), corrected)
        val second = SessionResult(session("s2", "t.a", 2), mapOf("t.a.01" to StepLevel.THREE))
        val row = compareResult(first, second, catalog).rows.single()
        assertEquals("1.0", row.averageA)
        assertEquals("+2.0", row.difference)
    }

    @Test
    fun `no common scored steps gives empty rows`() {
        val first = SessionResult(session("s", "t.a", 1), mapOf("t.a.01" to StepLevel.NOT_SCORED))
        val second = SessionResult(session("s2", "t.a", 2), mapOf("t.a.01" to StepLevel.THREE))
        val result = compareResult(first, second, catalog)
        assertTrue(result.rows.isEmpty())
        assertEquals(0, result.commonSteps)
    }

    @Test
    fun `difference is the real one rounded toward zero`() {
        assertEquals("-0.4" to Trend.DOWN, formatDifference(2.5 - 2.95))
        assertEquals("+0.1" to Trend.UP, formatDifference(2.4 - 2.3))
        assertEquals("0.0" to Trend.SAME, formatDifference(0.0))
        // Under 0.1: the number is hidden, the word says "slightly".
        assertEquals(null to Trend.SLIGHTLY_UP, formatDifference(3.00 - 2.99))
        assertEquals(null to Trend.SLIGHTLY_UP, formatDifference(2.99 - 2.91))
        assertEquals(null to Trend.SLIGHTLY_DOWN, formatDifference(2.91 - 2.99))
    }

    @Test
    fun `compare load failure is an error`() = runTest {
        val vm = compareVm(FakeResults(fail = true))
        assertTrue(vm.uiState.value is CompareUiState.Error)
    }

    @Test
    fun `training history newest first with guided count null and titles`() = runTest {
        val rows =
            listOf(
                TrainingResultEntity("r1", "c", "act.1", 5, 60, true, 100),
                TrainingResultEntity("r2", "c", "act.unknown", null, 30, false, 200)
            )
        val vm = TrainingHistoryViewModel(FakeHistory(rows), FakeCatalog(catalog), dispatcher).apply { load("c") }
        val content = vm.uiState.value as TrainingHistoryUiState.Content
        assertEquals(listOf("r2", "r1"), content.rows.map { it.id })
        assertNull(content.rows[0].count)
        assertEquals("act.unknown", content.rows[0].title)
        assertEquals("Arabic title", content.rows[1].title)
    }

    @Test
    fun `training history empty and error states`() = runTest {
        val empty = TrainingHistoryViewModel(FakeHistory(), FakeCatalog(catalog), dispatcher).apply { load("c") }
        assertEquals(TrainingHistoryUiState.Empty, empty.uiState.value)
        val failing = TrainingHistoryViewModel(FakeHistory(fail = true), FakeCatalog(catalog), dispatcher).apply { load("c") }
        assertTrue(failing.uiState.value is TrainingHistoryUiState.Error)
    }
}
