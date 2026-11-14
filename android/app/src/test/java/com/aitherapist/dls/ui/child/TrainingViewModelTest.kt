package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import com.aitherapist.dls.audio.FakeAudioPlayer
import com.aitherapist.dls.catalog.Activity
import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.Landmarker
import com.aitherapist.dls.data.TrainingResultEntity
import com.aitherapist.dls.data.TrainingResultStore
import com.aitherapist.dls.rewards.FakeRewardStore
import com.aitherapist.dls.rewards.RewardRecorder
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.vision.AttemptCapture
import com.aitherapist.dls.vision.CapturedSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val rows = mutableListOf<TrainingResultEntity>()
    private var addFails = false
    private val audio = FakeAudioPlayer()
    private val warnings = mutableListOf<String>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val capture =
        object : AttemptCapture {
            override val samples = MutableSharedFlow<CapturedSample>()
            override val failed = MutableStateFlow(false)

            override fun configure(landmarkers: Set<Landmarker>) = Unit

            override fun reset() = Unit

            override fun onFrame(bitmap: Bitmap, timestampMs: Long) = Unit

            override fun close() = Unit
        }

    private fun vm(): TrainingViewModel {
        val activity = Activity("a", "moderate", "rom", 1, "t", "ar", "g", "g", "h", "tools", "c", "s")
        val catalog =
            object : CatalogRepository {
                override suspend fun load() = Result.success(Catalog(CatalogVersion("v", false, 0, 1), emptyList(), listOf(activity)))
            }
        val store =
            object : TrainingResultStore {
                override suspend fun add(result: TrainingResultEntity) {
                    if (addFails) error("disk full")
                    rows += result
                }

                override suspend fun find(id: String) = rows.firstOrNull { it.id == id }
            }
        return TrainingViewModel(catalog, store, RewardRecorder(FakeRewardStore()) { 0L }, audio, Clock { 0L }, capture, ChildModeKeys(), {
            warnings +=
                it
        })
            .also { it.start("c", listOf("a"), "run") }
    }

    private fun TrainingViewModel.doneReady() = (uiState.value as TrainingUiState.Content).doneReady

    @Test
    fun `pause before the fallback ends re-runs it on resume and when the screen returns`() {
        val vm = vm()
        scope.runCurrent()
        assertFalse(vm.doneReady())
        vm.pause()
        scope.advanceTimeBy(5_000)
        assertFalse(vm.doneReady())
        vm.resume()
        scope.advanceTimeBy(1_600)
        assertTrue(vm.doneReady())
        val other = vm()
        scope.runCurrent()
        other.setActive(false)
        scope.advanceTimeBy(5_000)
        assertFalse(other.doneReady())
        other.setActive(true)
        scope.advanceTimeBy(1_600)
        assertTrue(other.doneReady())
    }

    @Test
    fun `a failed save keeps the activity and Done retries`() {
        val vm = vm()
        scope.advanceTimeBy(1_600)
        addFails = true
        vm.done()
        scope.runCurrent()
        assertFalse((vm.uiState.value as TrainingUiState.Content).training.ended)
        addFails = false
        vm.done()
        scope.runCurrent()
        assertTrue((vm.uiState.value as TrainingUiState.Content).training.ended)
        assertEquals(listOf("run:0"), rows.map { it.id })
        assertEquals(listOf("training save failed: IllegalStateException"), warnings)
    }
}
