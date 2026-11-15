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
    fun `openTraining pushes one training key with the selected ids`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        vm.openTraining("c", emptyList())
        assertEquals(listOf(ShellKey), vm.backStack.toList())
        vm.openTraining("c", listOf("a", "b", "c"))
        vm.openTraining("c", listOf("a"))
        assertEquals(listOf(ShellKey, TrainingKey("c", listOf("a", "b", "c"))), vm.backStack.toList())
    }

    @Test
    fun `training hand-off pins a run that only the pin leaves`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        vm.openTraining("c", listOf("a"))
        assertEquals(false, vm.childModeActive)
        vm.handToTraining(TrainingKey("c", listOf("a")), runId = "run")
        assertEquals(listOf(ShellKey, TrainingRunKey("c", listOf("a"), "run")), vm.backStack.toList())
        assertEquals(true, vm.childModeActive)
        assertEquals(true, vm.back())
        assertEquals(2, vm.backStack.size)
        vm.openChildPin()
        assertEquals(ChildPinKey, vm.backStack.last())
        vm.leaveChildMode()
        assertEquals(listOf(ShellKey), vm.backStack.toList())
        // A training run has no assessment summary.
        assertEquals(null, vm.pendingSummary.value)
    }

    @Test
    fun `leaving an assessment visit asks once for exactly its sessions`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        vm.onChildSession("stale") // outside child mode: ignored
        vm.openChildMode("c1", "t.on", "t.off")
        vm.onChildSession("s1")
        vm.onChildSession("s1")
        vm.nextChildTask(ChildModeKey("c1", "t.on", thenTaskId = "t.off"))
        vm.onChildSession("s2")
        assertEquals(null, vm.pendingSummary.value)
        vm.openChildPin()
        vm.leaveChildMode()
        assertEquals(SummaryRequest("c1", listOf("s1", "s2")), vm.pendingSummary.value)
        vm.consumeSummary()
        assertEquals(null, vm.pendingSummary.value)
        // The next visit starts clean: no stale session from the previous one.
        vm.openChildMode("c1", "t1")
        vm.openChildPin()
        vm.leaveChildMode()
        assertEquals(SummaryRequest("c1", emptyList()), vm.pendingSummary.value)
    }

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
        vm.open(SampleKey)
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

    @Test
    fun `chained task replaces the finished run in place and keeps child mode on`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        vm.openChildMode("c1", "t.on", "t.off")
        val first = ChildModeKey("c1", "t.on", thenTaskId = "t.off")
        assertEquals(listOf(ShellKey, first), vm.backStack.toList())
        vm.nextChildTask(first)
        assertEquals(listOf(ShellKey, ChildModeKey("c1", "t.off", framingFirst = true)), vm.backStack.toList())
        assertTrue(vm.childModeActive)
        vm.nextChildTask(ChildModeKey("c1", "t.off", framingFirst = true))
        vm.nextChildTask(first)
        assertEquals(
            "nothing chained or not on top: unchanged",
            listOf(ShellKey, ChildModeKey("c1", "t.off", framingFirst = true)),
            vm.backStack.toList()
        )
    }

    @Test
    fun `continued session runs its saved chained task`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        vm.openFramingCheck("c1", "t.on", sessionId = "s1")
        val framing = FramingCheckKey("c1", "t.on", sessionId = "s1")
        vm.handToChild(framing)
        val continued = ChildModeKey("c1", "t.on", "s1")
        vm.nextChildTask(continued, "t.off")
        assertEquals(listOf(ShellKey, ChildModeKey("c1", "t.off", framingFirst = true)), vm.backStack.toList())
    }

    @Test
    fun `framing check comes before child mode and hand to child replaces it`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        vm.openFramingCheck("c1", "t1", thenTaskId = "t2")
        vm.openFramingCheck("c1", "t1")
        val framing = FramingCheckKey("c1", "t1", thenTaskId = "t2")
        assertEquals(listOf(ShellKey, framing), vm.backStack.toList())
        assertFalse("no pinning during the framing check", vm.childModeActive)
        vm.handToChild(FramingCheckKey("c1", "other"))
        assertEquals("not on top: unchanged", listOf(ShellKey, framing), vm.backStack.toList())
        vm.handToChild(framing)
        assertEquals(listOf(ShellKey, ChildModeKey("c1", "t1", thenTaskId = "t2")), vm.backStack.toList())
        assertTrue(vm.childModeActive)
        vm.openFramingCheck("c1", "t1")
        assertEquals(2, vm.backStack.size)
    }

    @Test
    fun `back from the framing check returns without starting`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        vm.openFramingCheck("c1", "t1", sessionId = "s1")
        assertTrue(vm.back())
        assertEquals(listOf(ShellKey), vm.backStack.toList())
    }

    @Test
    fun `an expected settings hop skips one background lock`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher)
        vm.enterShell()
        vm.expectExternalReturn()
        vm.onAppBackgrounded()
        assertEquals(listOf(ShellKey), vm.backStack.toList())
        vm.onAppBackgrounded()
        assertEquals(listOf(PinKey), vm.backStack.toList())
    }

    private fun childModeGate(now: () -> Long): AppGateViewModel =
        AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher, now).apply {
            enterShell()
            openChildMode("c", "t")
        }

    @Test
    fun `child mode back within 60 s keeps the same child screen without pin`() {
        var now = 1_000L
        val vm = childModeGate { now }
        val before = vm.backStack.toList()
        vm.onAppBackgrounded()
        assertEquals(before, vm.backStack.toList())
        now += AppGateViewModel.CHILD_GRACE_MS - 1
        vm.onAppForegrounded()
        assertEquals(before, vm.backStack.toList())
        assertTrue(vm.childModeActive)
    }

    @Test
    fun `child mode back after 60 s asks for the pin`() {
        var now = 1_000L
        val vm = childModeGate { now }
        vm.onAppBackgrounded()
        now += AppGateViewModel.CHILD_GRACE_MS + 1
        vm.onAppForegrounded()
        assertEquals(listOf(PinKey), vm.backStack.toList())
        assertFalse(vm.childModeActive)
    }

    @Test
    fun `therapist mode still locks at once on background`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher) { 0L }
        vm.enterShell()
        vm.onAppBackgrounded()
        assertEquals(listOf(PinKey), vm.backStack.toList())
    }

    @Test
    fun `hand-off starts child mode once pinning holds`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher) { 0L }
        vm.enterShell()
        vm.openFramingCheck("c", "t")
        val key = FramingCheckKey("c", "t")
        vm.requestHandOff(key)
        assertEquals(PinningState.Requesting, vm.pinning)
        assertTrue(vm.lockWanted)
        assertFalse(vm.childModeActive)
        vm.onPinningResult(true)
        assertEquals(listOf(ShellKey, ChildModeKey("c", "t")), vm.backStack.toList())
        assertEquals(PinningState.Idle, vm.pinning)
        assertTrue(vm.lockWanted)
    }

    @Test
    fun `refused pinning starts child mode unpinned and leaves a hint for the adult`() {
        val vm = AppGateViewModel(FakeTherapistSettings(pin = "1234"), dispatcher) { 0L }
        vm.enterShell()
        vm.openTraining("c", listOf("a"))
        val key = TrainingKey("c", listOf("a"))
        vm.requestHandOff(key)
        vm.onPinningResult(false)
        assertEquals(PinningState.Idle, vm.pinning)
        assertTrue(vm.backStack.last() is TrainingRunKey)
        assertTrue(vm.childModeActive)
        assertFalse(vm.lockWanted) // no new pinning prompt for this visit
        assertTrue(vm.unpinnedHint)
        vm.openChildPin()
        vm.leaveChildMode()
        assertEquals(listOf(ShellKey), vm.backStack.toList())
        assertTrue(vm.unpinnedHint) // shown on the therapist screen after the visit
        vm.dismissUnpinnedHint()
        assertFalse(vm.unpinnedHint)
    }
}
