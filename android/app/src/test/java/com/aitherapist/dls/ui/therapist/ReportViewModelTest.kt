package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.data.AssessmentResults
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionKind
import com.aitherapist.dls.data.SessionResult
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.scoring.PlanLevel
import com.aitherapist.dls.scoring.StepLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun step(task: String, n: Int, tags: List<String>) =
        CatalogStep("$task.0$n", "a", task, n, "ar $task $n", "", "", tags, "", StepDetector(Method.POSE), null, emptyMap())

    private val taskA =
        CatalogTask(
            "t.a",
            "a",
            listOf(
                step("t.a", 1, listOf("Hand skills")),
                step("t.a", 2, listOf("Hand skills", "Visual-motor coordination")),
                step("t.a", 3, emptyList()),
                step("t.a", 4, listOf("Midline crossing"))
            ),
            nameAr = "Task A"
        )
    private val taskB = CatalogTask("t.b", "a", listOf(step("t.b", 1, listOf("Range of motion"))), nameAr = "Task B")
    private val catalog = Catalog(CatalogVersion("v", true, 5, 0), listOf(taskA, taskB), emptyList())

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

    private fun vm(results: FakeResults) = ReportViewModel(results, FakeCatalog(catalog), dispatcher)

    @Test
    fun `no finished assessment is empty`() = runTest {
        val vm = vm(FakeResults()).apply { load("c") }
        assertEquals(ReportUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `mixed levels give strengths needs and domain rows`() = runTest {
        val levels =
            linkedMapOf(
                "t.a.02" to StepLevel.TWO,
                "t.a.01" to StepLevel.THREE,
                "t.a.03" to StepLevel.ONE,
                "t.a.04" to StepLevel.NOT_SCORED,
                "gone" to StepLevel.ONE
            )
        val vm = vm(FakeResults(listOf(SessionResult(session("s1", "t.a", 2), levels)))).apply { load("c") }
        val task = (vm.uiState.value as ReportUiState.Content).tasks.single()
        assertEquals("Task A", task.taskName)
        assertEquals(listOf(StepLine("t.a.01", 1, "ar t.a 1")), task.strengths)
        assertEquals(listOf("t.a.02", "t.a.03", "gone"), task.needs.map { it.stepId })
        assertEquals(listOf("Hand skills", "Motor coordination"), task.needs.first().domains)
        assertEquals(StepLevel.TWO, task.needs.first().level)
        assertEquals("gone", task.needs.last().text)
        assertEquals(1, task.notScoredCount)
        assertEquals(1, task.untaggedCount)
        assertEquals(1, task.unknownCount)
        assertEquals(DomainRow("Range of motion", null, null, 0, covered = false), task.domains.first { it.domain == "Range of motion" })
        assertEquals(4, task.domains.size)
        val hand = task.domains.first { it.domain == "Hand skills" }
        assertEquals(2.5, hand.average!!, 1e-9)
        assertEquals(PlanLevel.MILD, hand.planLevel)
        assertEquals(2, hand.scoredSteps)
        assertEquals(DomainRow("Midline crossing", null, null, 0), task.domains.first { it.domain == "Midline crossing" })
    }

    @Test
    fun `several tasks give one section each in catalogue order using the latest`() = runTest {
        val rows =
            listOf(
                SessionResult(session("b", "t.b", 9), mapOf("t.b.01" to StepLevel.ONE)),
                SessionResult(session("a2", "t.a", 8), mapOf("t.a.01" to StepLevel.THREE)),
                SessionResult(session("a1", "t.a", 1), mapOf("t.a.01" to StepLevel.ONE))
            )
        val vm = vm(FakeResults(rows)).apply { load("c") }
        val tasks = (vm.uiState.value as ReportUiState.Content).tasks
        assertEquals(listOf("t.a", "t.b"), tasks.map { it.taskId })
        assertEquals(1, tasks.first().strengths.size)
    }

    @Test
    fun `reload reflects a correction`() = runTest {
        val results = FakeResults(listOf(SessionResult(session("s", "t.b", 1), mapOf("t.b.01" to StepLevel.ONE))))
        val vm = vm(results).apply { load("c") }
        assertEquals(
            PlanLevel.SEVERE,
            (vm.uiState.value as ReportUiState.Content).tasks.single().domains.first {
                it.domain ==
                    "Range of motion"
            }.planLevel
        )
        results.rows = listOf(SessionResult(session("s", "t.b", 1), mapOf("t.b.01" to StepLevel.THREE)))
        vm.load("c")
        assertEquals(
            PlanLevel.STRENGTH,
            (vm.uiState.value as ReportUiState.Content).tasks.single().domains.first {
                it.domain ==
                    "Range of motion"
            }.planLevel
        )
    }

    @Test
    fun `a catalogue step outside the task is not unknown`() = runTest {
        val levels = mapOf("t.a.01" to StepLevel.THREE, "t.b.01" to StepLevel.ONE)
        val vm = vm(FakeResults(listOf(SessionResult(session("s", "t.a", 1), levels)))).apply { load("c") }
        assertEquals(0, (vm.uiState.value as ReportUiState.Content).tasks.single().unknownCount)
    }

    @Test
    fun `error then retry`() = runTest {
        val results = FakeResults(fail = true)
        val vm = vm(results).apply { load("c") }
        assertTrue(vm.uiState.value is ReportUiState.Error)
        results.fail = false
        vm.retry()
        assertEquals(ReportUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `average has one decimal and western digits`() {
        assertEquals("2.6", formatAverage(2.6667))
        assertEquals("3.0", formatAverage(3.0))
        assertEquals("2.4", formatAverage(2.45))
        assertEquals("2.9", formatAverage(2.95))
        assertEquals("1.4", formatAverage(1.45))
        assertEquals("2.3", formatAverage(2.3))
    }
}
