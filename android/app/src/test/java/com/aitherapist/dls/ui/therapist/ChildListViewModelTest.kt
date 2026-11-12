package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.ui.therapist.FakeChildProfiles.Companion.child
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
