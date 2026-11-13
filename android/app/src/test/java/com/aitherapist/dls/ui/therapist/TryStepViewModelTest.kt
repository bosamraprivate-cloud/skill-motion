package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TryStepViewModelTest {
    private fun catalog(vararg tasks: String, fail: Boolean = false) = object : CatalogRepository {
        override suspend fun load(): Result<Catalog> = if (fail) {
            Result.failure(IOException("bad file"))
        } else {
            Result.success(Catalog(CatalogVersion("v", false, 0, 0), tasks.map { CatalogTask(it, "a", emptyList()) }, emptyList()))
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `defaults to the first child and first task`() {
        val kids = FakeChildProfiles(FakeChildProfiles.child("b", "Bara", "C-002"), FakeChildProfiles.child("a", "Ali", "C-001"))
        val vm = TryStepViewModel(kids, catalog("t1", "t2"))
        val s = vm.uiState.value as TryStepUiState.Content
        assertEquals("a", s.childId)
        assertEquals("t1", s.taskId)
        assertTrue(s.canStart)
    }

    @Test
    fun `no children cannot start`() {
        val vm = TryStepViewModel(FakeChildProfiles(), catalog("t1"))
        assertFalse((vm.uiState.value as TryStepUiState.Content).canStart)
    }

    @Test
    fun `selects known ids and ignores unknown ones`() {
        val kids = FakeChildProfiles(FakeChildProfiles.child("a", "Ali", "C-001"), FakeChildProfiles.child("b", "Bara", "C-002"))
        val vm = TryStepViewModel(kids, catalog("t1", "t2"))
        vm.onEvent(TryStepEvent.SelectChild("b"))
        vm.onEvent(TryStepEvent.SelectTask("t2"))
        vm.onEvent(TryStepEvent.SelectChild("zz"))
        vm.onEvent(TryStepEvent.SelectTask("zz"))
        val s = vm.uiState.value as TryStepUiState.Content
        assertEquals("b", s.childId)
        assertEquals("t2", s.taskId)
    }

    @Test
    fun `catalogue failure is an error`() {
        val vm = TryStepViewModel(FakeChildProfiles(FakeChildProfiles.child("a", "Ali", "C-001")), catalog(fail = true))
        assertEquals(TryStepUiState.Error("IOException"), vm.uiState.value)
    }
}
