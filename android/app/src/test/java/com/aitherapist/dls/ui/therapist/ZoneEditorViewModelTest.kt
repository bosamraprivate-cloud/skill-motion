package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.Zone
import com.aitherapist.dls.data.ZoneStore
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

private class FakeZoneStore(var stored: Map<String, Zone> = emptyMap()) : ZoneStore {
    var failReads = false
    var failWrites = false

    override suspend fun zones(): Map<String, Zone> {
        if (failReads) throw IOException("boom")
        return stored
    }

    override suspend fun setZones(zones: Map<String, Zone>) {
        if (failWrites) throw IOException("boom")
        stored = zones
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ZoneEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val ZoneEditorViewModel.content get() = uiState.value as ZoneEditorUiState.Content

    @Test
    fun `existing zones are loaded and shown`() {
        val mat = Zone(0.1f, 0.5f, 0.6f, 0.9f)
        val vm = ZoneEditorViewModel(FakeZoneStore(mapOf("mat" to mat)), dispatcher)
        assertEquals(mapOf("mat" to mat), vm.content.zones)
    }

    @Test
    fun `drawing saves the selected zone un-mirrored and redrawing replaces it`() {
        val store = FakeZoneStore()
        val vm = ZoneEditorViewModel(store, dispatcher)
        vm.onEvent(ZoneEditorEvent.Select("sink"))
        vm.onEvent(ZoneEditorEvent.Drawn(0.1f, 0.2f, 0.3f, 0.5f))
        val sink = requireNotNull(store.stored["sink"])
        assertEquals(0.7f, sink.left, 1e-6f)
        assertEquals(0.9f, sink.right, 1e-6f)
        assertTrue(vm.content.saved)
        vm.onEvent(ZoneEditorEvent.Drawn(0.5f, 0.5f, 0.9f, 0.9f))
        assertEquals(0.1f, requireNotNull(store.stored["sink"]).left, 1e-6f)
        assertEquals(setOf("sink"), store.stored.keys)
    }

    @Test
    fun `tiny rectangle is rejected with a message and nothing is saved`() {
        val store = FakeZoneStore()
        val vm = ZoneEditorViewModel(store, dispatcher)
        vm.onEvent(ZoneEditorEvent.Drawn(0.4f, 0.4f, 0.41f, 0.41f))
        assertTrue(vm.content.tooSmall)
        assertTrue(store.stored.isEmpty())
    }

    @Test
    fun `clear removes only that zone`() {
        val store = FakeZoneStore(mapOf("mat" to Zone(0f, 0f, 0.5f, 0.5f), "place" to Zone(0.5f, 0.5f, 1f, 1f)))
        val vm = ZoneEditorViewModel(store, dispatcher)
        vm.onEvent(ZoneEditorEvent.Clear("mat"))
        assertEquals(setOf("place"), store.stored.keys)
        assertFalse("mat" in vm.content.zones)
    }

    @Test
    fun `unknown zone names cannot be selected`() {
        val vm = ZoneEditorViewModel(FakeZoneStore(), dispatcher)
        vm.onEvent(ZoneEditorEvent.Select("kitchen"))
        assertEquals(ZONE_NAMES.first(), vm.content.selected)
    }

    @Test
    fun `load failure shows error and retry recovers`() {
        val store = FakeZoneStore().apply { failReads = true }
        val vm = ZoneEditorViewModel(store, dispatcher)
        assertEquals(ZoneEditorUiState.Error("IOException"), vm.uiState.value)
        store.failReads = false
        vm.onEvent(ZoneEditorEvent.Retry)
        assertTrue(vm.uiState.value is ZoneEditorUiState.Content)
    }

    @Test
    fun `save failure keeps the drawn zone and retry saves it`() {
        val store = FakeZoneStore().apply { failWrites = true }
        val vm = ZoneEditorViewModel(store, dispatcher)
        vm.onEvent(ZoneEditorEvent.Drawn(0.1f, 0.2f, 0.3f, 0.5f))
        assertTrue(vm.content.saveFailed)
        assertTrue("mat" in vm.content.zones)
        assertTrue(store.stored.isEmpty())
        store.failWrites = false
        vm.onEvent(ZoneEditorEvent.RetrySave)
        assertFalse(vm.content.saveFailed)
        assertTrue(vm.content.saved)
        assertEquals(setOf("mat"), store.stored.keys)
    }
}
