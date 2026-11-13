package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.data.ResumableSessions
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionKind
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.ui.therapist.FakeChildProfiles.Companion.child
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
class ChildListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun ChildListViewModel.ids() = (uiState.value as ChildListUiState.Content).children.map { it.id }

    @Test
    fun `only the eval child means empty`() = runTest(dispatcher) {
        val vm = ChildListViewModel(FakeChildProfiles(child("e", "Eval", "EVAL", isEval = true)), dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(ChildListUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `eval child is hidden and list is sorted`() = runTest(dispatcher) {
        val repo = FakeChildProfiles(child("2", "Youssef", "B2"), child("1", "Mariam", "A1"), child("e", "Eval", "EVAL", isEval = true))
        val vm = ChildListViewModel(repo, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(listOf("1", "2"), vm.ids())
    }

    @Test
    fun `search filters by name only ignoring case`() = runTest(dispatcher) {
        val repo = FakeChildProfiles(child("1", "Youssef", "A1"), child("2", "Mariam", "XB7"))
        val vm = ChildListViewModel(repo, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onQueryChange("yo")
        assertEquals(listOf("1"), vm.ids())
        vm.onQueryChange("b7")
        assertTrue("code is not searchable", vm.ids().isEmpty())
        vm.onQueryChange("MAR")
        assertEquals(listOf("2"), vm.ids())
        vm.onQueryChange("zzz")
        assertTrue(vm.ids().isEmpty())
        vm.onQueryChange(" ")
        assertEquals(listOf("2", "1"), vm.ids())
    }

    @Test
    fun `list follows repository changes`() = runTest(dispatcher) {
        val repo = FakeChildProfiles()
        val vm = ChildListViewModel(repo, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(ChildListUiState.Empty, vm.uiState.value)
        repo.add(child("1", "Mariam", "A1"))
        assertEquals(listOf("1"), vm.ids())
        repo.delete("1")
        assertEquals(ChildListUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `read failure shows error and retry recovers`() = runTest(dispatcher) {
        val repo = FakeChildProfiles(child("1", "Mariam", "A1")).apply { failReads = true }
        val vm = ChildListViewModel(repo, dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(ChildListUiState.Error("IOException"), vm.uiState.value)
        repo.failReads = false
        vm.retry()
        assertEquals(listOf("1"), vm.ids())
    }

    private fun session(id: String, childId: String, state: SessionState = SessionState.PAUSED) = SessionEntity(
        id = id,
        childId = childId,
        taskId = "t1",
        kind = SessionKind.ASSESSMENT,
        state = state,
        startedAt = 0,
        endedAt = null,
        catalogVersion = "v",
        rulesVersion = "r",
        stepIndex = 2,
        attemptNumber = 1,
        promptLevel = 2
    )

    @Test
    fun `continue card per child with a resumable session, never for eval or unknown children`() = runTest(dispatcher) {
        val repo = FakeChildProfiles(child("1", "Mariam", "A1"), child("2", "Youssef", "B2"), child("e", "Eval", "EVAL", isEval = true))
        val open = MutableStateFlow(listOf(session("s1", "1"), session("s0", "1"), session("se", "e"), session("sx", "gone")))
        val sessions =
            object : ResumableSessions {
                override fun observeResumable() = open

                override suspend fun session(id: String) = null
            }
        val vm = ChildListViewModel(repo, dispatcher, sessions)
        backgroundScope.launch { vm.uiState.collect {} }
        val cards = (vm.uiState.value as ChildListUiState.Content).continueRows
        assertEquals(listOf(ContinueRow("s1", "1", "Mariam", "t1")), cards)
        open.value = emptyList()
        assertTrue("finished session: no card", (vm.uiState.value as ChildListUiState.Content).continueRows.isEmpty())
    }

    @Test
    fun `continue card shows the arabic task name from the catalogue`() = runTest(dispatcher) {
        val repo = FakeChildProfiles(child("1", "Mariam", "A1"))
        val sessions =
            object : ResumableSessions {
                override fun observeResumable() = MutableStateFlow(listOf(session("s1", "1")))

                override suspend fun session(id: String) = null
            }
        val catalog =
            object : CatalogRepository {
                override suspend fun load() = Result.success(
                    Catalog(CatalogVersion("v", false, 0, 0), listOf(CatalogTask("t1", "a", emptyList(), "اسم")), emptyList())
                )
            }
        val vm = ChildListViewModel(repo, dispatcher, sessions, catalog)
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals("اسم", (vm.uiState.value as ChildListUiState.Content).continueRows.single().taskName)
    }

    @Test
    fun `search filters continue cards and a failing session query only hides them`() = runTest(dispatcher) {
        val repo = FakeChildProfiles(child("1", "Mariam", "A1"), child("2", "Youssef", "B2"))
        val open = MutableStateFlow(listOf(session("s1", "1"), session("s2", "2")))
        val vm = ChildListViewModel(
            repo,
            dispatcher,
            object : ResumableSessions {
                override fun observeResumable() = open

                override suspend fun session(id: String) = null
            }
        )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onQueryChange("you")
        assertEquals(listOf("s2"), (vm.uiState.value as ChildListUiState.Content).continueRows.map { it.sessionId })

        val failing = ChildListViewModel(
            repo,
            dispatcher,
            object : ResumableSessions {
                override fun observeResumable() = flow<List<SessionEntity>> { error("db") }

                override suspend fun session(id: String) = null
            }
        )
        backgroundScope.launch { failing.uiState.collect {} }
        val content = failing.uiState.value as ChildListUiState.Content
        assertEquals(listOf("1", "2"), content.children.map { it.id })
        assertTrue(content.continueRows.isEmpty())
    }
}
