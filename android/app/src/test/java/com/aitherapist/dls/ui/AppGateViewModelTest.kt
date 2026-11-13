package com.aitherapist.dls.ui

import com.aitherapist.dls.ui.therapist.FakeTherapistSettings
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
class AppGateViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no pin starts on setup`() {
        val vm = AppGateViewModel(FakeTherapistSettings(), dispatcher)
        assertEquals(GateState.Ready, vm.gateState.value)
        assertEquals(listOf(SetupKey), vm.backStack.toList())
    }

    @Test
    fun `stored pin starts on the pin gate and enterShell replaces it`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        assertEquals(listOf(PinKey), vm.backStack.toList())
        vm.enterShell()
        assertEquals(listOf(ShellKey), vm.backStack.toList())
    }

    @Test
    fun `back on one entry is a no-op and pops otherwise`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        assertFalse(vm.back())
        assertEquals(listOf(ShellKey), vm.backStack.toList())
        vm.open(SampleKey)
        assertTrue(vm.back())
        assertEquals(listOf(ShellKey), vm.backStack.toList())
    }

    @Test
    fun `backgrounding relocks the shell but keeps setup and pin`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.onAppBackgrounded()
        assertEquals(listOf(PinKey), vm.backStack.toList())
        vm.enterShell()
        vm.open(CharacterPreviewKey)
        vm.onAppBackgrounded()
        assertEquals(listOf(PinKey), vm.backStack.toList())

        val setup = AppGateViewModel(FakeTherapistSettings(), dispatcher)
        setup.onAppBackgrounded()
        assertEquals(listOf(SetupKey), setup.backStack.toList())
    }

    @Test
    fun `read failure shows error and retry recovers`() {
        val s = FakeTherapistSettings(pin = "1234").apply { failReads = true }
        val vm = AppGateViewModel(s, dispatcher)
        assertEquals(GateState.Error("IOException"), vm.gateState.value)
        assertTrue(vm.backStack.isEmpty())
        s.failReads = false
        vm.retry()
        assertEquals(GateState.Ready, vm.gateState.value)
        assertEquals(listOf(PinKey), vm.backStack.toList())
    }

    @Test
    fun `child mode blocks back, opens the pin from the adult corner and leaves after it`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        vm.openChildMode("c1", "t1")
        vm.openChildMode("c1", "t1")
        assertEquals("a double tap pushes one run", 2, vm.backStack.size)
        assertTrue(vm.childModeActive)
        assertTrue(vm.childStepOnTop)
        assertTrue(vm.back())
        assertEquals(listOf(ShellKey, ChildModeKey("c1", "t1")), vm.backStack.toList())
        vm.openChildPin()
        assertFalse(vm.childStepOnTop)
        assertTrue(vm.childModeActive)
        assertTrue(vm.back())
        assertEquals(listOf(ShellKey, ChildModeKey("c1", "t1")), vm.backStack.toList())
        vm.openChildPin()
        vm.leaveChildMode()
        assertEquals(listOf(ShellKey), vm.backStack.toList())
        assertFalse(vm.childModeActive)
    }
}
