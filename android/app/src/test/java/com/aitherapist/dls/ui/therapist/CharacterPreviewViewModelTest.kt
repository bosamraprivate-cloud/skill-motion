package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.CharacterIndex
import com.aitherapist.dls.catalog.CharacterVariant
import com.aitherapist.dls.catalog.ReactionClip
import com.aitherapist.dls.catalog.StepClip
import com.aitherapist.dls.catalog.StepClipTask
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
class CharacterPreviewViewModelTest {
    private val step1 = StepClip("t.a.01", "a1.webp", "a1.still.webp")
    private val step2 = StepClip("t.a.02", null, "a2.still.webp")
    private val v1 =
        CharacterVariant(
            "v1",
            listOf(ReactionClip("r1", "r1.webp"), ReactionClip("r2", "r2.webp")),
            listOf(StepClipTask("t.a", listOf(step1, step2)), StepClipTask("t.b", emptyList()))
        )
    private val v2 = CharacterVariant("v2", listOf(ReactionClip("r1", "v2r1.webp")), emptyList())
    private val index = CharacterIndex(listOf(v1, v2))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun content(vm: CharacterPreviewViewModel) = vm.uiState.value as CharacterPreviewUiState.Content

    @Test
    fun `content starts on first variant first reaction and first task`() {
        val c = content(CharacterPreviewViewModel { index })
        assertEquals(listOf("v1", "v2"), c.variants)
        assertEquals("t.a", c.taskId)
        assertEquals(ClipSelection.Reaction(v1.reactions[0]), c.selection)
        assertEquals(listOf(step1, step2), c.steps)
    }

    @Test
    fun `selecting reaction task step and variant`() {
        val vm = CharacterPreviewViewModel { index }
        vm.onEvent(CharacterPreviewEvent.SelectReaction("r2"))
        assertEquals(ClipSelection.Reaction(v1.reactions[1]), content(vm).selection)
        vm.onEvent(CharacterPreviewEvent.SelectStep("t.a.02"))
        assertEquals(ClipSelection.Step(step2), content(vm).selection)
        vm.onEvent(CharacterPreviewEvent.SelectTask("t.b"))
        assertEquals(emptyList<StepClip>(), content(vm).steps)
        vm.onEvent(CharacterPreviewEvent.SelectTask("unknown"))
        assertEquals("t.b", content(vm).taskId)
        vm.onEvent(CharacterPreviewEvent.SelectStep("unknown"))
        vm.onEvent(CharacterPreviewEvent.SelectReaction("unknown"))
        assertEquals(ClipSelection.Step(step2), content(vm).selection)
        vm.onEvent(CharacterPreviewEvent.SetEffectOff(true))
        vm.onEvent(CharacterPreviewEvent.SelectVariant("v2"))
        val c = content(vm)
        assertEquals("v2", c.variant.id)
        assertEquals(null, c.taskId)
        assertTrue("effect level is kept across variants", c.effectOff)
    }

    @Test
    fun `failed clip shows error for that item until another is selected`() {
        val vm = CharacterPreviewViewModel { index }
        vm.onEvent(CharacterPreviewEvent.ClipFailed)
        assertTrue(content(vm).clipFailed)
        vm.onEvent(CharacterPreviewEvent.SelectStep("t.a.01"))
        assertFalse(content(vm).clipFailed)
    }

    @Test
    fun `retry keeps the effects-off switch`() {
        val vm = CharacterPreviewViewModel { index }
        vm.onEvent(CharacterPreviewEvent.SetEffectOff(true))
        vm.onEvent(CharacterPreviewEvent.Retry)
        assertTrue(content(vm).effectOff)
    }

    @Test
    fun `toggling effects clears a failed clip`() {
        val vm = CharacterPreviewViewModel { index }
        vm.onEvent(CharacterPreviewEvent.ClipFailed)
        vm.onEvent(CharacterPreviewEvent.SetEffectOff(true))
        assertFalse(content(vm).clipFailed)
    }

    @Test
    fun `empty index is empty state and unknown variant stays empty`() {
        val vm = CharacterPreviewViewModel { CharacterIndex(emptyList()) }
        assertEquals(CharacterPreviewUiState.Empty, vm.uiState.value)
        vm.onEvent(CharacterPreviewEvent.SelectStep("x"))
        assertEquals(CharacterPreviewUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `load failure is error with type name and retry recovers`() {
        var fail = true
        val vm =
            CharacterPreviewViewModel {
                if (fail) throw IOException("secret") else index
            }
        assertEquals(CharacterPreviewUiState.Error("IOException"), vm.uiState.value)
        fail = false
        vm.onEvent(CharacterPreviewEvent.Retry)
        assertEquals("v1", content(vm).variant.id)
    }
}
