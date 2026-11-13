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

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun content(vm: NewSessionViewModel) = vm.uiState.value as NewSessionUiState.Content

    @Test
    fun `an on task with an off sibling folds into one option, grouped by area in catalogue order`() {
        val vm = NewSessionViewModel(catalog())
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
    fun `jacket choice on, off or both`() {
        val vm = NewSessionViewModel(catalog())
        vm.onEvent(NewSessionEvent.Select("dress.coat.on"))
        assertEquals("dress.coat.on" to null, vm.plan())
        vm.onEvent(NewSessionEvent.Choose(PairChoice.OFF))
        assertEquals("dress.coat.off" to null, vm.plan())
        vm.onEvent(NewSessionEvent.Choose(PairChoice.BOTH))
        assertEquals("dress.coat.on" to "dress.coat.off", vm.plan())
        vm.onEvent(NewSessionEvent.Select("eat.cake"))
        assertEquals(PairChoice.ON, content(vm).choice)
        vm.onEvent(NewSessionEvent.Choose(PairChoice.BOTH))
        assertEquals("a single task ignores the pair choice", "eat.cake" to null, vm.plan())
    }

    @Test
    fun `unknown selection is ignored and a failing catalogue shows an error`() {
        val vm = NewSessionViewModel(catalog())
        vm.onEvent(NewSessionEvent.Select("dress.coat.off"))
        assertNull(content(vm).selectedId)
        assertEquals(NewSessionUiState.Error("IOException"), NewSessionViewModel(catalog(fail = true)).uiState.value)
    }
}
