package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.data.Zone
import com.aitherapist.dls.data.ZoneStore
import com.aitherapist.dls.vision.FpsStream
import com.aitherapist.dls.vision.Framing
import com.aitherapist.dls.vision.FramingGuidance
import com.aitherapist.dls.vision.InferenceDelegate
import com.aitherapist.dls.vision.LandmarkEngine
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.LandmarkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FramingCheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeEngine(private val failStart: Boolean = false, private val onStart: () -> Unit = {}) : LandmarkEngine {
        val events = mutableListOf<String>()
        override val results = MutableSharedFlow<LandmarkResult>(extraBufferCapacity = 8)
        override val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
        var started = 0
        var closed = 0

        override fun start(): InferenceDelegate {
            started++
            events += "start"
            onStart()
            check(!failStart)
            return InferenceDelegate.CPU
        }

        override fun detect(bitmap: Bitmap, timestampMs: Long) = true

        override fun close() {
            closed++
            events += "close"
        }
    }

    private fun step(n: Int, method: Method, check: Check? = null) =
        CatalogStep("t.x.0$n", "a", "t.x", n, "", "", "", emptyList(), "", StepDetector(method, "table", check), null, emptyMap())

    private fun repo(vararg steps: CatalogStep) = object : CatalogRepository {
        override suspend fun load() = Result.success(
            Catalog(CatalogVersion("v1", false, steps.size, 0), listOf(CatalogTask("t.x", "a", steps.toList())), emptyList())
        )
    }

    private fun TestScope.vm(engine: LandmarkEngine, vararg steps: CatalogStep) =
        FramingCheckViewModel(repo(*steps), engine, dispatcher, zoneStore).also {
            it.load("t.x")
            runCurrent()
        }

    private val zoneStore = object : ZoneStore {
        var saved: Map<String, Zone> = emptyMap()

        override suspend fun zones() = saved

        override suspend fun setZones(zones: Map<String, Zone>) {
            saved = zones
        }
    }

    private val cloud = step(1, Method.CLOUD, Check.Question("q"))

    @Test
    fun `a needed zone that is not drawn shows as missing until it is saved`() = runTest(dispatcher) {
        val inMat = step(1, Method.POSE, Check.InZone(LandmarkRef.Lm("WRIST"), "mat", 0.3, Side.EITHER))
        val vm = vm(FakeEngine(), inMat, cloud)
        runCurrent()
        assertEquals(listOf("mat"), (vm.uiState.value as FramingUiState.Check).missingZones)
        zoneStore.setZones(mapOf("mat" to Zone(0.1f, 0.1f, 0.5f, 0.5f)))
        vm.refreshZones()
        runCurrent()
        assertEquals(emptyList<String>(), (vm.uiState.value as FramingUiState.Check).missingZones)
    }

    @Test
    fun `a task without zone checks never shows the notice`() = runTest(dispatcher) {
        assertEquals(emptyList<String>(), (vm(FakeEngine(), cloud).uiState.value as FramingUiState.Check).missingZones)
    }
    private val visiblePose = List(33) { i -> LandmarkPoint(if (i % 2 == 0) 0.3f else 0.7f, if (i % 4 < 2) 0.3f else 0.7f, 0.9f) }

    @Test
    fun `content-only task skips`() = runTest(dispatcher) {
        assertEquals(FramingUiState.Skip, vm(FakeEngine(), step(1, Method.NONE)).uiState.value)
    }

    @Test
    fun `unknown task or failed catalogue is unavailable`() = runTest(dispatcher) {
        val vm = FramingCheckViewModel(repo(cloud), FakeEngine(), dispatcher, zoneStore)
        vm.load("nope")
        runCurrent()
        assertTrue(vm.uiState.value is FramingUiState.Unavailable)
        val failing = object : CatalogRepository {
            override suspend fun load(): Result<Catalog> = Result.failure(IllegalStateException())
        }
        val bad = FramingCheckViewModel(failing, FakeEngine(), dispatcher, zoneStore)
        bad.load("t.x")
        runCurrent()
        assertEquals(FramingUiState.Unavailable("IllegalStateException"), bad.uiState.value)
    }

    @Test
    fun `one second of visibility turns sage and a flicker stays grey`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = vm(engine, cloud)
        vm.start()
        runCurrent()
        assertEquals(1, engine.started)
        val pose = { ts: Long, points: List<LandmarkPoint>? -> vm.onResult(LandmarkResult(FpsStream.POSE, ts, listOfNotNull(points))) }
        pose(0, visiblePose)
        pose(250, visiblePose)
        pose(500, null) // lost: resets the hold
        for (t in 700L..1_500L step 200) pose(t, visiblePose)
        var check = vm.uiState.value as FramingUiState.Check
        assertEquals(Framing.TABLE, check.framing)
        assertFalse(check.framed)
        pose(1_700, visiblePose)
        vm.onResult(LandmarkResult(FpsStream.HAND, 1_700, emptyList()))
        vm.onResult(LandmarkResult(FpsStream.COMBINED, 1_700, emptyList()))
        check = vm.uiState.value as FramingUiState.Check
        assertTrue(check.framed)
        assertEquals(FramingGuidance.OK, check.guidance)
        vm.stop()
        runCurrent()
        assertEquals(1, engine.closed)
        assertFalse((vm.uiState.value as FramingUiState.Check).framed)
    }

    @Test
    fun `missing wrists asks for hands`() = runTest(dispatcher) {
        val vm = vm(FakeEngine(), cloud)
        val pose = visiblePose.mapIndexed { i, p -> if (i == 15 || i == 16) p.copy(visibility = 0f) else p }
        vm.onResult(LandmarkResult(FpsStream.POSE, 0, listOf(pose)))
        val check = vm.uiState.value as FramingUiState.Check
        assertEquals(FramingGuidance.SHOW_HANDS, check.guidance)
        assertFalse(check.framed)
    }

    @Test
    fun `start anyway appears after ten seconds`() = runTest(dispatcher) {
        val vm = vm(FakeEngine(), cloud)
        advanceTimeBy(FramingCheckViewModel.OVERRIDE_AFTER_MS - 1)
        assertFalse((vm.uiState.value as FramingUiState.Check).canOverride)
        advanceTimeBy(2)
        assertTrue((vm.uiState.value as FramingUiState.Check).canOverride)
    }

    @Test
    fun `engine failure offers start anyway`() = runTest(dispatcher) {
        val vm = vm(FakeEngine(failStart = true), cloud)
        vm.start()
        runCurrent()
        val check = vm.uiState.value as FramingUiState.Check
        assertEquals("IllegalStateException", check.engineError)
        assertTrue(check.canOverride)
        vm.onCameraError("NoCamera")
        assertEquals("NoCamera", (vm.uiState.value as FramingUiState.Check).engineError)
    }

    @Test
    fun `stop during start closes the new landmarkers after they are created`() = runTest(dispatcher) {
        lateinit var vm: FramingCheckViewModel
        val engine = FakeEngine(onStart = { vm.stop() })
        vm = vm(engine, cloud)
        vm.start()
        runCurrent()
        assertEquals(listOf("start", "close", "close"), engine.events)
    }

    @Test
    fun `successful start clears an earlier engine error`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = vm(engine, cloud)
        vm.onCameraError("Busy")
        vm.start()
        runCurrent()
        assertEquals(null, (vm.uiState.value as FramingUiState.Check).engineError)
    }

    @Test
    fun `hands arriving before their pose are paired`() = runTest(dispatcher) {
        val sink =
            CatalogStep(
                "t.x.01", "a", "t.x", 1, "", "", "", emptyList(), "",
                StepDetector(
                    Method.CLOUD,
                    "sink",
                    Check.Question("q")
                ),
                null, emptyMap()
            )
        val vm = vm(FakeEngine(), sink)
        val hand = listOf(LandmarkPoint(0.5f, 0.5f))
        vm.onResult(LandmarkResult(FpsStream.HAND, 950, listOf(hand, hand)))
        vm.onResult(LandmarkResult(FpsStream.HAND, 1_000, listOf(hand, hand)))
        vm.onResult(LandmarkResult(FpsStream.POSE, 1_000, listOf(visiblePose)))
        assertEquals(FramingGuidance.OK, (vm.uiState.value as FramingUiState.Check).guidance)
        vm.onResult(LandmarkResult(FpsStream.POSE, 1_200, listOf(visiblePose)))
        assertEquals("no hand within 100 ms", FramingGuidance.SHOW_HANDS, (vm.uiState.value as FramingUiState.Check).guidance)
    }
}
