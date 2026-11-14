package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Activity
import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.Criterion
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.data.AssessmentResults
import com.aitherapist.dls.data.PlanDraftStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun step(task: String, n: Int, tags: List<String>) =
        CatalogStep("$task.0$n", "a", task, n, "ar", "", "", tags, "", StepDetector(Method.POSE), null, emptyMap())

    private fun act(plan: String, domain: String, n: Int, counter: Criterion? = null) =
        Activity("$plan.$domain.0$n", plan, domain, n, "title $n", "ar $n", "g", "goal", "how", "tools", "crit", "src", null, counter)

    private val taskA =
        CatalogTask(
            "t.a",
            "a",
            listOf(
                step("t.a", 1, listOf("Hand skills")),
                step("t.a", 2, listOf("Motor planning")),
                step("t.a", 3, listOf("Range of motion"))
            ),
            nameAr = "Task A"
        )
    private val taskB =
        CatalogTask("t.b", "a", listOf(step("t.b", 1, listOf("Hand skills")), step("t.b", 2, listOf("Range of motion"))), nameAr = "Task B")
    private val counter = Criterion.Reps(Check.Question("q"), 10)
    private val activities =
        listOf("mild", "moderate", "severe").flatMap { p ->
            listOf("rom", "hand", "planning").flatMap { d -> listOf(act(p, d, 1, if (d == "hand") counter else null), act(p, d, 2)) }
        }
    private val catalog = Catalog(CatalogVersion("v", true, 5, activities.size), listOf(taskA, taskB), activities)

    private fun result(task: String, levels: Map<String, StepLevel?>) =
        SessionResult(SessionEntity("s$task", "c", task, SessionKind.ASSESSMENT, SessionState.DONE, 1, 1, "v", "4", 0, 1, 1), levels)

    private class FakeResults(var rows: List<SessionResult> = emptyList(), var fail: Boolean = false) : AssessmentResults {
        override suspend fun results(childId: String, taskId: String?): List<SessionResult> {
            if (fail) throw IllegalStateException("boom")
            return rows
        }
    }

    private class FakeCatalog(private val catalog: Catalog) : CatalogRepository {
        override suspend fun load(): Result<Catalog> = Result.success(catalog)
    }

    private class FakeDrafts : PlanDraftStore {
        val map = mutableMapOf<String, List<String>>()

        override suspend fun draft(childId: String): List<String> = map[childId].orEmpty()

        override suspend fun setDraft(childId: String, activityIds: List<String>) {
            if (activityIds.isEmpty()) map.remove(childId) else map[childId] = activityIds
        }
    }

    private fun vm(results: FakeResults, drafts: FakeDrafts = FakeDrafts()) =
        PlanViewModel(results, FakeCatalog(catalog), drafts, dispatcher)

    /** Task A: Hand skills 1.0 = severe, Motor planning 3.0 = strength, Range of motion 2.0 = moderate. */
    private val mixed = result("t.a", mapOf("t.a.01" to StepLevel.ONE, "t.a.02" to StepLevel.THREE, "t.a.03" to StepLevel.TWO))

    @Test
    fun `no assessment is empty`() = runTest {
        assertEquals(PlanUiState.Empty, vm(FakeResults()).apply { load("c") }.uiState.value)
    }

    @Test
    fun `all strengths shows no plan needed`() = runTest {
        val r = FakeResults(listOf(result("t.a", mapOf("t.a.01" to StepLevel.THREE, "t.a.02" to StepLevel.THREE))))
        assertEquals(PlanUiState.AllStrengths, vm(r).apply { load("c") }.uiState.value)
    }

    @Test
    fun `mixed levels order weakest first with verbatim texts and counter badge`() = runTest {
        val state = vm(FakeResults(listOf(mixed))).apply { load("c") }.uiState.value as PlanUiState.Content
        assertEquals(
            listOf("Hand skills" to PlanLevel.SEVERE, "Range of motion" to PlanLevel.MODERATE),
            state.sections.map { it.domain to it.planLevel }
        )
        val first = state.sections[0].activities[0]
        assertEquals("severe.hand.01", first.id)
        assertEquals("ar 1", first.textAr)
        assertEquals("tools", first.tools)
        assertEquals("how", first.howToPerform)
        assertEquals("goal", first.goal)
        assertEquals("crit", first.criterion)
        assertTrue(first.cameraCounted)
        assertFalse(state.sections[1].activities[0].cameraCounted)
    }

    @Test
    fun `one section per domain across tasks at the weakest level with per-task sources`() = runTest {
        // Task B: Hand skills 3.0 (strength), Range of motion not scored yet (null average, covered by the task).
        val b = result("t.b", mapOf("t.b.01" to StepLevel.THREE, "t.b.02" to StepLevel.NOT_SCORED))
        val state = vm(FakeResults(listOf(b, mixed))).apply { load("c") }.uiState.value as PlanUiState.Content
        assertEquals(listOf("Hand skills", "Range of motion"), state.sections.map { it.domain })
        val hand = state.sections[0]
        assertEquals(PlanLevel.SEVERE, hand.planLevel)
        assertEquals(listOf("Task A" to 1.0, "Task B" to 3.0), hand.sources.map { it.taskName to it.average })
        assertEquals(2, hand.activities.size)
        val rom = state.sections[1].sources
        assertEquals(listOf("Task A", "Task B"), rom.map { it.taskName })
        assertNull(rom[1].average)
    }

    @Test
    fun `selection is saved per child and survives a new view model, cleared on start`() = runTest {
        val drafts = FakeDrafts()
        val r = FakeResults(listOf(mixed))
        val vm = vm(r, drafts).apply { load("c") }
        assertTrue((vm.uiState.value as PlanUiState.Content).selectedInOrder.isEmpty())
        listOf("moderate.rom.01", "severe.hand.02", "severe.hand.01", "moderate.rom.01", "moderate.rom.02").forEach(vm::toggle)
        val restored = vm(r, drafts).apply { load("c") }
        assertEquals(
            listOf("severe.hand.01", "severe.hand.02", "moderate.rom.02"),
            (restored.uiState.value as PlanUiState.Content).selectedInOrder
        )
        restored.onTrainingStarted()
        assertTrue((restored.uiState.value as PlanUiState.Content).selected.isEmpty())
        assertTrue(drafts.map.isEmpty())
    }

    @Test
    fun `stale draft ids are dropped and first load error then retry`() = runTest {
        val drafts = FakeDrafts().apply { map["c"] = listOf("gone", "severe.hand.01") }
        val r = FakeResults(fail = true)
        val vm = vm(r, drafts).apply { load("c") }
        assertTrue(vm.uiState.value is PlanUiState.Error)
        vm.toggle("severe.hand.02")
        r.fail = false
        r.rows = listOf(mixed)
        vm.retry()
        assertEquals(setOf("severe.hand.01"), (vm.uiState.value as PlanUiState.Content).selected)
        assertEquals(listOf("severe.hand.01"), drafts.map["c"])
    }

    @Test
    fun `reload error keeps old content with an inline error`() = runTest {
        val r = FakeResults(listOf(mixed))
        val vm = vm(r).apply { load("c") }
        r.fail = true
        vm.load("c")
        val state = vm.uiState.value as PlanUiState.Content
        assertTrue(state.reloadError)
        assertEquals(2, state.sections.size)
        r.fail = false
        vm.retry()
        assertFalse((vm.uiState.value as PlanUiState.Content).reloadError)
    }
}
