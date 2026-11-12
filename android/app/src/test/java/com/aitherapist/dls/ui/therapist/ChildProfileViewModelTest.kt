package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.ui.therapist.FakeChildProfiles.Companion.child
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
class ChildProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeChildProfiles, id: String? = null) =
        ChildProfileViewModel(repo, dispatcher, { 42L }, { "new-id" }).apply { load(id) }

    private val ChildProfileViewModel.content get() = uiState.value as ChildProfileUiState.Content

    private fun ChildProfileViewModel.fill(name: String = "Mariam", age: String = "6", character: ChildCharacter? = ChildCharacter.GIRL) {
        onEvent(ChildProfileEvent.NameChanged(name))
        onEvent(ChildProfileEvent.AgeChanged(age))
        character?.let { onEvent(ChildProfileEvent.CharacterChanged(it)) }
    }

    @Test
    fun `add valid child saves with calm effect`() = runTest {
        val repo = FakeChildProfiles()
        val vm = vm(repo)
        assertTrue(vm.content.isNew)
        vm.fill(name = "  Mariam ")
        vm.onEvent(ChildProfileEvent.Save)
        val saved = repo.rows.value.single()
        assertEquals("new-id", saved.id)
        assertEquals("Mariam", saved.name)
        assertEquals("C-001", saved.code)
        assertEquals(6, saved.ageYears)
        assertEquals(ChildCharacter.GIRL, saved.characterVariant)
        assertEquals(EffectLevel.CALM, saved.effectLevel)
        assertEquals(42L, saved.createdAt)
        assertEquals(ProfileResult.Saved("new-id"), vm.content.result)
        assertEquals("new-id", vm.content.childId)
    }

    @Test
    fun `missing fields refuse save`() = runTest {
        val repo = FakeChildProfiles()
        listOf(
            Triple("name", { v: ChildProfileViewModel -> v.fill(name = "") }, { e: ChildFormErrors -> e.name }),
            Triple("age", { v: ChildProfileViewModel -> v.fill(age = "") }, { e: ChildFormErrors -> e.age }),
            Triple("character", { v: ChildProfileViewModel -> v.fill(character = null) }, { e: ChildFormErrors -> e.character })
        ).forEach { (label, fill, field) ->
            val vm = vm(repo)
            fill(vm)
            vm.onEvent(ChildProfileEvent.Save)
            assertEquals(label, ChildFieldError.REQUIRED, field(vm.content.errors))
            assertNull(vm.content.result)
        }
        assertTrue(repo.rows.value.isEmpty())
    }

    @Test
    fun `new codes follow the highest existing sequence number`() = runTest {
        val repo =
            FakeChildProfiles(child("1", "Youssef", "C-007"), child("2", "Eval", "C-002", isEval = true), child("3", "Old", "AB1"))
        var ids = 0
        val vm = ChildProfileViewModel(repo, dispatcher, { 1L }, { "n-${++ids}" }).apply { load(null) }
        vm.fill()
        vm.onEvent(ChildProfileEvent.Save)
        vm.consumeResult()
        vm.fill(name = "Salma")
        vm.onEvent(ChildProfileEvent.Save)
        assertEquals(listOf("C-008", "C-009"), repo.rows.value.filter { it.id.startsWith("n-") }.map { it.code })
    }

    @Test
    fun `bad ages are refused`() = runTest {
        val repo = FakeChildProfiles()
        listOf("0", "19", "x").forEach {
            val vm = vm(repo)
            vm.fill(age = it)
            vm.onEvent(ChildProfileEvent.Save)
            assertEquals(it, ChildFieldError.AGE_RANGE, vm.content.errors.age)
        }
        assertTrue(repo.rows.value.isEmpty())
    }

    @Test
    fun `edit loads the child and persists effect and character`() = runTest {
        val repo = FakeChildProfiles(child("1", "Mariam", "A1", character = ChildCharacter.GIRL))
        val vm = vm(repo, "1")
        assertEquals("Mariam", vm.content.form.name)
        assertEquals("6", vm.content.form.age)
        vm.onEvent(ChildProfileEvent.EffectChanged(EffectLevel.FULL))
        vm.onEvent(ChildProfileEvent.CharacterChanged(ChildCharacter.BOY))
        vm.onEvent(ChildProfileEvent.Save)
        val saved = repo.rows.value.single()
        assertEquals(EffectLevel.FULL, saved.effectLevel)
        assertEquals(ChildCharacter.BOY, saved.characterVariant)
        assertEquals(1L, saved.createdAt)
        assertEquals(ProfileResult.Saved("1"), vm.content.result)
    }

    @Test
    fun `edit never changes the code`() = runTest {
        val repo = FakeChildProfiles(child("1", "Mariam", "C-004"))
        val vm = vm(repo, "1")
        vm.onEvent(ChildProfileEvent.NameChanged("Mariam Ali"))
        vm.onEvent(ChildProfileEvent.Save)
        assertEquals(ProfileResult.Saved("1"), vm.content.result)
        assertEquals("C-004", repo.rows.value.single().code)
        assertEquals("Mariam Ali", repo.rows.value.single().name)
    }

    @Test
    fun `delete needs confirmation and cancel keeps the child`() = runTest {
        val repo = FakeChildProfiles(child("1", "Mariam", "A1"))
        val vm = vm(repo, "1")
        vm.onEvent(ChildProfileEvent.ConfirmDelete) // no dialog yet: ignored
        assertTrue(repo.deleted.isEmpty())
        vm.onEvent(ChildProfileEvent.RequestDelete)
        assertTrue(vm.content.confirmDelete)
        vm.onEvent(ChildProfileEvent.CancelDelete)
        assertFalse(vm.content.confirmDelete)
        assertEquals(1, repo.rows.value.size)
        vm.onEvent(ChildProfileEvent.RequestDelete)
        vm.onEvent(ChildProfileEvent.ConfirmDelete)
        assertEquals(listOf("1"), repo.deleted)
        assertTrue(repo.rows.value.isEmpty())
        assertEquals(ChildProfileUiState.Deleted, vm.uiState.value)
    }

    @Test
    fun `new child cannot be deleted`() = runTest {
        val vm = vm(FakeChildProfiles())
        vm.onEvent(ChildProfileEvent.RequestDelete)
        assertFalse(vm.content.confirmDelete)
    }

    @Test
    fun `eval or missing child is an error and read failure can retry`() = runTest {
        val repo = FakeChildProfiles(child("e", "Eval", "EVAL", isEval = true), child("1", "Mariam", "A1"))
        assertEquals(ChildProfileUiState.Error("not_found"), vm(repo, "e").uiState.value)
        assertEquals(ChildProfileUiState.Error("not_found"), vm(repo, "zzz").uiState.value)
        repo.failReads = true
        val vm = vm(repo, "1")
        assertEquals(ChildProfileUiState.Error("IOException"), vm.uiState.value)
        repo.failReads = false
        vm.onEvent(ChildProfileEvent.Retry)
        assertEquals("Mariam", vm.content.form.name)
    }

    @Test
    fun `save after delete does nothing`() = runTest {
        val repo = FakeChildProfiles(child("1", "Mariam", "A1"))
        val vm = vm(repo, "1")
        vm.onEvent(ChildProfileEvent.RequestDelete)
        vm.onEvent(ChildProfileEvent.ConfirmDelete)
        assertEquals(ChildProfileUiState.Deleted, vm.uiState.value)
        vm.onEvent(ChildProfileEvent.Save)
        assertTrue(repo.rows.value.isEmpty())
        assertEquals(ChildProfileUiState.Deleted, vm.uiState.value)
    }

    @Test
    fun `add twice in a row gives a blank form`() = runTest {
        val repo = FakeChildProfiles()
        var ids = 0
        val vm = ChildProfileViewModel(repo, dispatcher, { 1L }, { "id-${++ids}" }).apply { load(null) }
        vm.fill()
        vm.onEvent(ChildProfileEvent.Save)
        assertEquals(ProfileResult.Saved("id-1"), vm.content.result)
        vm.consumeResult()
        vm.load(null)
        assertTrue(vm.content.isNew)
        assertNull(vm.content.result)
        assertEquals(ChildForm(), vm.content.form)
        vm.fill(name = "Youssef")
        vm.onEvent(ChildProfileEvent.Save)
        assertEquals(listOf("id-1", "id-2"), repo.rows.value.map { it.id })
    }

    @Test
    fun `consuming an edit result keeps the form`() = runTest {
        val vm = vm(FakeChildProfiles(child("1", "Mariam", "A1")), "1")
        vm.onEvent(ChildProfileEvent.Save)
        vm.consumeResult()
        assertNull(vm.content.result)
        assertTrue(vm.content.saved)
        assertEquals("1", vm.content.childId)
    }

    @Test
    fun `write failure keeps typed input with a message`() = runTest {
        val repo = FakeChildProfiles().apply { failWrites = java.io.IOException("disk") }
        val vm = vm(repo)
        vm.fill(name = "Mariam")
        vm.onEvent(ChildProfileEvent.Save)
        assertEquals(ProfileMessage.SAVE_FAILED, vm.content.message)
        assertFalse(vm.content.busy)
        assertEquals("Mariam", vm.content.form.name)
        assertEquals("6", vm.content.form.age)
    }

    @Test
    fun `delete failure keeps the form with a message`() = runTest {
        val repo = FakeChildProfiles(child("1", "Mariam", "A1"))
        val vm = vm(repo, "1")
        repo.failWrites = java.io.IOException("disk")
        vm.onEvent(ChildProfileEvent.RequestDelete)
        vm.onEvent(ChildProfileEvent.ConfirmDelete)
        assertEquals(ProfileMessage.DELETE_FAILED, vm.content.message)
        assertEquals(1, repo.rows.value.size)
    }
}
