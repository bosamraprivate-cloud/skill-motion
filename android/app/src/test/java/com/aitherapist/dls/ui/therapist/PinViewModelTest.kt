package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.PinAttempts
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
class PinViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun PinViewModel.type(digits: String) = digits.forEach { onEvent(PinEvent.Digit(it)) }

    private val PinViewModel.content get() = uiState.value as PinUiState.Content

    @Test
    fun `right pin unlocks`() {
        val vm = PinViewModel(FakeTherapistSettings(pin = "2468"), dispatcher)
        vm.type("2468")
        vm.onEvent(PinEvent.Submit)
        assertEquals(PinUiState.Unlocked, vm.uiState.value)
    }

    @Test
    fun `wrong pin stays locked clears the field and shows a message`() {
        val vm = PinViewModel(FakeTherapistSettings(pin = "2468"), dispatcher)
        vm.type("1357")
        vm.onEvent(PinEvent.Submit)
        assertEquals(PinUiState.Content(entered = "", wrong = true), vm.uiState.value)
        vm.type("2")
        assertFalse(vm.content.wrong)
    }

    @Test
    fun `short pin cannot be submitted`() {
        val vm = PinViewModel(FakeTherapistSettings(pin = "2468"), dispatcher)
        vm.type("246")
        assertFalse(vm.content.canSubmit)
        vm.onEvent(PinEvent.Submit)
        assertEquals("246", vm.content.entered)
    }

    @Test
    fun `entry caps at six digits and delete removes the last`() {
        val vm = PinViewModel(FakeTherapistSettings(pin = "2468"), dispatcher)
        vm.type("12345678")
        assertEquals("123456", vm.content.entered)
        vm.onEvent(PinEvent.Delete)
        assertEquals("12345", vm.content.entered)
        vm.onEvent(PinEvent.Digit('x'))
        assertEquals("12345", vm.content.entered)
        assertTrue(vm.content.canSubmit)
    }

    @Test
    fun `no stored pin never unlocks`() {
        val vm = PinViewModel(FakeTherapistSettings(), dispatcher)
        vm.type("0000")
        vm.onEvent(PinEvent.Submit)
        assertTrue(vm.content.wrong)
    }

    @Test
    fun `verify failure shows error and retry returns to an empty pad`() {
        val s = FakeTherapistSettings(pin = "2468").apply { failReads = true }
        val vm = PinViewModel(s, dispatcher)
        vm.type("2468")
        vm.onEvent(PinEvent.Submit)
        assertEquals(PinUiState.Error("IOException"), vm.uiState.value)
        vm.onEvent(PinEvent.Retry)
        assertEquals(PinUiState.Content(), vm.uiState.value)
    }

    @Test
    fun `toString hides the entered digits`() {
        assertFalse(PinUiState.Content(entered = "975310").toString().contains("975310"))
    }

    private fun PinViewModel.wrong(times: Int) = repeat(times) {
        type("1111")
        onEvent(PinEvent.Submit)
    }

    @Test
    fun `five wrong pins lock the pad for 30 s with a countdown`() {
        val s = FakeTherapistSettings(pin = "2468")
        val vm = PinViewModel(s, dispatcher) { dispatcher.scheduler.currentTime }
        vm.wrong(4)
        assertFalse(vm.content.locked)
        assertEquals(4, s.attempts.failures)
        vm.wrong(1)
        assertEquals(30, vm.content.lockedSeconds)
        assertEquals(PinViewModel.LOCKOUT_MS, s.attempts.lockedUntil)
        vm.type("2468")
        assertEquals("", vm.content.entered) // the pad ignores digits while locked
        dispatcher.scheduler.advanceTimeBy(10_001)
        assertEquals(20, vm.content.lockedSeconds)
        dispatcher.scheduler.advanceTimeBy(20_000)
        assertFalse(vm.content.locked)
        vm.type("2468")
        vm.onEvent(PinEvent.Submit)
        assertEquals(PinUiState.Unlocked, vm.uiState.value)
    }

    @Test
    fun `right pin resets the wrong counter`() {
        val s = FakeTherapistSettings(pin = "2468")
        val vm = PinViewModel(s, dispatcher) { 0L }
        vm.wrong(4)
        vm.type("2468")
        vm.onEvent(PinEvent.Submit)
        assertEquals(PinAttempts(), s.attempts)
    }

    @Test
    fun `persisted lockout survives a restart`() {
        val s = FakeTherapistSettings(pin = "2468").apply { attempts = PinAttempts(0, 25_000L) }
        val vm = PinViewModel(s, dispatcher) { 10_000L + dispatcher.scheduler.currentTime }
        assertEquals(15, vm.content.lockedSeconds)
        dispatcher.scheduler.advanceTimeBy(15_001)
        assertFalse(vm.content.locked)
    }
}
