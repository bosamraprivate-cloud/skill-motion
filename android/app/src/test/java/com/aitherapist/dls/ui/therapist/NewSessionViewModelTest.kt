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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewSessionViewModelTest {
    private val tasks =
        listOf(
            CatalogTask("eat.cake", "eat", emptyList(), "كيك"),
            CatalogTask("dress.coat.on", "dress", emptyList(), "لبس"),
            CatalogTask("dress.coat.off", "dress", emptyList(), "قلع"),
            CatalogTask("dress.hat.on", "dress", emptyList(), "طاقية"),
            CatalogTask("eat.cup.on", "eat", emptyList(), "أ"),
            CatalogTask("eat.cup.off", "drink", emptyList(), "ب")
        )

    private fun catalog(fail: Boolean = false) = object : CatalogRepository {
        override suspend fun load(): Result<Catalog> = if (fail) {
            Result.failure(IOException("bad"))
        } else {
            Result.success(
                Catalog(
                    CatalogVersion("v", false, 0, 0),
                    tasks,
                    emptyList(),
                    mapOf(
                        "eat" to "أكل"
                    )
                )
            )
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()

    /** Every task ready for "boy" unless listed in [notReady]. */
    private fun vm(repo: CatalogRepository = catalog(), notReady: Set<String> = emptySet()) =
        NewSessionViewModel(repo, { task -> if (task.id in notReady) emptySet() else setOf("boy") }, dispatcher)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun content(vm: NewSessionViewModel) = vm.uiState.value as NewSessionUiState.Content

    @Test
    fun `an on task with an off sibling folds into one option, grouped by area in catalogue order`() {
        val vm = vm()
        val c = content(vm)
        assertEquals(listOf("eat.cake", "dress.coat.on", "dress.hat.on", "eat.cup.on", "eat.cup.off"), c.options.map { it.id })
        assertEquals("different areas never fold", false, c.options[3].isPair)
        assertEquals(listOf("eat", "dress", "drink"), c.byArea.map { it.first })
        assertEquals(mapOf("eat" to "أكل"), c.areaNames)
        val coat = c.options[1]
        assertTrue(coat.isPair)
        assertEquals("dress.coat.off", coat.offId)
        assertEquals("قلع", coat.offNameAr)
        assertEquals(null, c.options[2].offId)
        assertNull("nothing selected: no start", vm.plan())
    }

    @Test
    fun `pair defaults to both, take off first then put on`() {
        val vm = vm()
        assertEquals(listOf(PairChoice.OFF, PairChoice.ON, PairChoice.BOTH), PairChoice.entries)
        vm.onEvent(NewSessionEvent.Select("dress.coat.on"))
        assertEquals(PairChoice.BOTH, content(vm).choice)
        assertEquals("dress.coat.off" to "dress.coat.on", vm.plan())
        vm.onEvent(NewSessionEvent.Choose(PairChoice.ON))
        assertEquals("dress.coat.on" to null, vm.plan())
        vm.onEvent(NewSessionEvent.Choose(PairChoice.OFF))
        assertEquals("dress.coat.off" to null, vm.plan())
        vm.onEvent(NewSessionEvent.Select("eat.cake"))
        assertEquals(PairChoice.ON, content(vm).choice)
        vm.onEvent(NewSessionEvent.Choose(PairChoice.BOTH))
        assertEquals("a single task ignores the pair choice", "eat.cake" to null, vm.plan())
    }

    @Test
    fun `off listed before on in the catalogue still folds into one pair option`() {
        val reordered = listOf(
            CatalogTask("dress.sock.off", "dress", emptyList(), "قلع"),
            CatalogTask("dress.sock.on", "dress", emptyList(), "لبس")
        )
        val options = TaskOption.from(reordered)
        assertEquals(listOf("dress.sock.on"), options.map { it.id })
        assertEquals("dress.sock.off" to "dress.sock.on", options.single().plan(PairChoice.BOTH))
    }

    @Test
    fun `unknown selection is ignored and a failing catalogue shows an error`() {
        val vm = vm()
        vm.onEvent(NewSessionEvent.Select("dress.coat.off"))
        assertNull(content(vm).selectedId)
        assertEquals(NewSessionUiState.Error("IOException"), vm(catalog(fail = true)).uiState.value)
    }

    @Test
    fun `a not-ready task cannot be selected and sorts after ready ones`() {
        val vm = vm(notReady = setOf("eat.cake"))
        vm.onEvent(NewSessionEvent.Select("eat.cake", "boy"))
        assertNull(content(vm).selectedId)
        val eat = content(vm).byAreaReadyFirst("boy").first { it.first == "eat" }.second
        assertEquals(listOf("eat.cup.on", "eat.cake"), eat.map { it.id })
        vm.onEvent(NewSessionEvent.Select("eat.cup.on", "girl"))
        assertNull("ready only for boy", content(vm).selectedId)
    }

    @Test
    fun `areas without a ready task stay listed but come last`() {
        val vm = vm(notReady = setOf("eat.cake", "eat.cup.on"))
        assertEquals(listOf("dress", "drink", "eat"), content(vm).byAreaReadyFirst("boy").map { it.first })
    }

    @Test
    fun `a pair with only one ready half offers that half`() {
        val vm = vm(notReady = setOf("dress.coat.off"))
        vm.onEvent(NewSessionEvent.Select("dress.coat.on", "boy"))
        val c = content(vm)
        assertEquals(PairChoice.ON, c.choice)
        assertTrue(c.selected!!.choiceReady(PairChoice.ON, "boy"))
        assertFalse(c.selected!!.choiceReady(PairChoice.BOTH, "boy"))
        assertFalse(c.selected!!.choiceReady(PairChoice.OFF, "boy"))
    }
}
