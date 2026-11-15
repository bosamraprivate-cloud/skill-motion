package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Posture
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.data.Zone
import com.aitherapist.dls.data.ZoneStore
import com.aitherapist.dls.vision.BitmapImage
import com.aitherapist.dls.vision.BitmapJpegEncoder
import com.aitherapist.dls.vision.CropBox
import com.aitherapist.dls.vision.FaceBlur
import com.aitherapist.dls.vision.FpsStream
import com.aitherapist.dls.vision.Framing
import com.aitherapist.dls.vision.FramingGuidance
import com.aitherapist.dls.vision.LandmarkEngine
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.LandmarkResult
import com.aitherapist.dls.vision.Landmarkers
import com.aitherapist.dls.vision.NeededLandmarks
import com.aitherapist.dls.vision.PixelSize
import com.aitherapist.dls.vision.SceneIssue
import com.aitherapist.dls.vision.SceneIssueCode
import com.aitherapist.dls.vision.SceneObject
import com.aitherapist.dls.vision.ScenePlan
import com.aitherapist.dls.vision.SceneResult
import com.aitherapist.dls.vision.VisibilityGate
import com.aitherapist.dls.vision.Zone as VisionZone
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Adult-facing framing check before a camera-scored task (Story 3.4). Nothing here is scored, stored or sent. */
sealed interface FramingUiState {
    data object Loading : FramingUiState

    /** Content-only task (every step `none`): no framing check; hand over at once. */
    data object Skip : FramingUiState

    /**
     * [framed] turns the outline sage (needed landmarks visible for [VisibilityGate.HOLD_MS]). [canOverride] shows
     * "Start anyway" (10 s without sage, or the engine failed). [engineError] is an exception type name only.
     */
    data class Check(
        val framing: Framing,
        val framed: Boolean = false,
        val guidance: FramingGuidance? = null,
        val canOverride: Boolean = false,
        val engineError: String? = null,
        /** `inZone` zones this task needs that are not drawn yet (those checks would become cannot tell); start stays allowed. */
        val missingZones: List<String> = emptyList(),
        /** How to position the child for this task (staging hint only; never changes scoring). */
        val posture: Posture = Posture.UNKNOWN,
        /** Story 6.4: the AI scene check that runs before body framing. */
        val scene: SceneUi = SceneUi()
    ) : FramingUiState {
        /** Body framing (and hand-off) is active once the scene phase is over. */
        val bodyPhase: Boolean get() = scene.status in SceneStatus.DONE
    }

    /** The catalogue could not load: message plus Start anyway. */
    data class Unavailable(val reason: String) : FramingUiState
}

/** Scene phase status (Story 6.4). */
enum class SceneStatus {
    /** Waiting for a camera frame and a pose result. */
    CAPTURING,

    /** The face-blurred frame is with the proxy. */
    CHECKING,

    /** The AI answered but something is missing; shows issues and Check again. */
    ISSUES,

    /** Offline or the proxy failed: calm message plus Start anyway. */
    UNAVAILABLE,

    /** Accepted; the boxes are saved as this session's zones. */
    ACCEPTED,

    /** The task uses no zones: no scene phase. */
    NOT_NEEDED,

    /** The therapist started anyway; zones are cleared so zone steps end as cannot tell. */
    SKIPPED;

    companion object {
        val DONE = setOf(ACCEPTED, NOT_NEEDED, SKIPPED)
    }
}

/** [zoneKinds] are what the task needs (labels for messages); [objects] are drawn over the preview; [issues] are blocking only. */
data class SceneUi(
    val status: SceneStatus = SceneStatus.NOT_NEEDED,
    val zoneKinds: List<String> = emptyList(),
    val objects: List<SceneObject> = emptyList(),
    val issues: List<SceneIssue> = emptyList()
)

/** One camera frame held for the scene check; encodes a JPEG with [blur] (normalised) pixelated. Never kept after use. */
fun interface SceneSnapshot {
    fun jpeg(blur: VisionZone?): ByteArray
}

/** The proxy scene check as the view model uses it (built from the current settings per call). */
fun interface SceneService {
    suspend fun check(jpeg: ByteArray, kinds: List<String>): SceneResult
}

@HiltViewModel
class FramingCheckViewModel
internal constructor(
    private val catalog: CatalogRepository,
    private val engine: LandmarkEngine,
    private val compute: CoroutineDispatcher,
    private val zones: ZoneStore,
    private val sceneService: SceneService = SceneService { _, _ -> SceneResult.Unavailable("not_configured") }
) : ViewModel() {
    @Inject constructor(catalog: CatalogRepository, landmarkers: Landmarkers, zones: ZoneStore, scene: SceneService) :
        this(catalog, landmarkers, Dispatchers.Default, zones, scene)

    private val state = MutableStateFlow<FramingUiState>(FramingUiState.Loading)
    val uiState: StateFlow<FramingUiState> = state.asStateFlow()

    private var needed = NeededLandmarks(emptySet(), 0)
    private var neededZones: List<String> = emptyList()
    private val hold = VisibilityGate.Hold()
    private var loadedTask: String? = null
    private var engineJobs: List<Job> = emptyList()
    private var startJob: Job? = null
    private var stopJob: Job? = null

    private var zoneKinds: List<String> = emptyList()

    /** Zone kinds plus the task's scene objects: each needs a box before the session (person never blocks). */
    private var requiredKinds: List<String> = emptyList()
    private var sceneJob: Job? = null
    private var overrideJob: Job? = null
    private val firstFrame = CompletableDeferred<Unit>()
    private val firstPose = CompletableDeferred<Unit>()

    @Volatile private var pendingSnapshot: CompletableDeferred<SceneSnapshot>? = null

    @Volatile private var lastPose: LandmarkFrame? = null

    @Volatile private var running = false
    private val recentHands = ArrayDeque<LandmarkResult>()

    /** Loads [taskId]'s needed landmarks once; starts the 10 s override timer. */
    fun load(taskId: String) = load(
        taskId,
        postureOf = { catalog -> catalog.tasks.firstOrNull { it.id == taskId }?.posture ?: Posture.UNKNOWN },
        sceneObjectsOf = { catalog -> catalog.tasks.firstOrNull { it.id == taskId }?.sceneObjects.orEmpty() }
    ) { catalog -> catalog.tasks.firstOrNull { it.id == taskId }?.steps?.map { it.detector } }

    /**
     * Training (Story 4.6): the framing check for the picked activities' camera counters (pose checks); with no
     * camera-counted activity it skips.
     */
    fun loadActivities(activityIds: List<String>) = load("training:" + activityIds.joinToString(",")) { catalog ->
        catalog.activities.filter {
            it.id in activityIds
        }.mapNotNull { a -> a.counter?.let { StepDetector(Method.POSE, check = it.check) } }
    }

    private fun load(
        key: String,
        postureOf: (Catalog) -> Posture = { Posture.UNKNOWN },
        sceneObjectsOf: (Catalog) -> List<String> = { emptyList() },
        detectorsOf: (Catalog) -> List<StepDetector>?
    ) {
        if (loadedTask == key) return
        loadedTask = key
        viewModelScope.launch {
            val loaded =
                try {
                    catalog.load().getOrThrow()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    state.value = FramingUiState.Unavailable(e::class.simpleName ?: "unknown")
                    return@launch
                }
            val detectors = detectorsOf(loaded)
            if (detectors == null) {
                state.value = FramingUiState.Unavailable("unknown task")
                return@launch
            }
            needed = VisibilityGate.neededLandmarks(detectors)
            if (needed.isEmpty) {
                state.value = FramingUiState.Skip
                return@launch
            }
            neededZones = ScenePlan.zoneKinds(detectors)
            zoneKinds = neededZones
            requiredKinds = ScenePlan.requiredKinds(zoneKinds, sceneObjectsOf(loaded))
            val sceneNeeded = requiredKinds.isNotEmpty()
            state.value = FramingUiState.Check(
                Framing.of(detectors.firstNotNullOfOrNull { it.framing }),
                posture = postureOf(loaded),
                scene = SceneUi(if (sceneNeeded) SceneStatus.CAPTURING else SceneStatus.NOT_NEEDED, requiredKinds)
            )
            if (sceneNeeded) runScene() else startBodyPhase()
        }
    }

    /** Body framing phase: the 10 s Start anyway timer starts now. */
    private fun startBodyPhase() {
        overrideJob?.cancel()
        overrideJob =
            viewModelScope.launch {
                val missing = missingZones()
                edit { it.copy(missingZones = missing) }
                delay(OVERRIDE_AFTER_MS)
                edit { it.copy(canOverride = true) }
            }
    }

    /** "Check again": a new frame and a new scene check. */
    fun recheckScene() {
        val check = state.value as? FramingUiState.Check ?: return
        if (check.scene.status != SceneStatus.ISSUES && check.scene.status != SceneStatus.UNAVAILABLE) return
        runScene()
    }

    /** "Start anyway" during the scene phase: clears the zones (zone steps then end as cannot tell) and moves on. */
    fun skipScene() {
        val check = state.value as? FramingUiState.Check ?: return
        if (check.scene.status in SceneStatus.DONE) return
        sceneJob?.cancel()
        pendingSnapshot = null
        viewModelScope.launch {
            saveZones(emptyMap())
            edit { it.copy(scene = it.scene.copy(status = SceneStatus.SKIPPED, objects = emptyList(), issues = emptyList())) }
            startBodyPhase()
        }
    }

    private fun runScene() {
        sceneJob?.cancel()
        sceneJob =
            viewModelScope.launch {
                edit { it.copy(scene = it.scene.copy(status = SceneStatus.CAPTURING, objects = emptyList(), issues = emptyList())) }
                firstFrame.await()
                withTimeoutOrNull(POSE_WAIT_MS) { firstPose.await() }
                val wanted = CompletableDeferred<SceneSnapshot>()
                pendingSnapshot = wanted
                val snapshot = wanted.await()
                val pose = lastPose?.pose
                val blur = FaceBlur.faceBox(pose, personFromPose(pose))
                edit { it.copy(scene = it.scene.copy(status = SceneStatus.CHECKING)) }
                val result =
                    try {
                        val jpeg = withContext(compute) { snapshot.jpeg(blur) }
                        sceneService.check(jpeg, ScenePlan.requestKinds(requiredKinds))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception
                    ) {
                        SceneResult.Unavailable(e::class.simpleName ?: "unknown")
                    }
                onSceneResult(result)
            }
    }

    private suspend fun onSceneResult(result: SceneResult) {
        when (result) {
            is SceneResult.Unavailable -> edit { it.copy(scene = it.scene.copy(status = SceneStatus.UNAVAILABLE)) }
            is SceneResult.Checked -> {
                val objects = result.objects
                if (ScenePlan.accepted(result, requiredKinds)) {
                    saveZones(ScenePlan.zonesOf(result, zoneKinds))
                    edit { it.copy(scene = it.scene.copy(status = SceneStatus.ACCEPTED, objects = objects, issues = emptyList())) }
                    startBodyPhase()
                } else {
                    val issues = ScenePlan.blockingIssues(result).ifEmpty {
                        requiredKinds.filter { k -> objects.none { it.kind == k } }.map { SceneIssue(SceneIssueCode.MISSING_OBJECT, it) }
                    }
                    edit { it.copy(scene = it.scene.copy(status = SceneStatus.ISSUES, objects = objects, issues = issues)) }
                }
            }
        }
    }

    /** Saves the session zones; a store failure leaves those steps as cannot tell (never blocks). */
    private suspend fun saveZones(found: Map<String, VisionZone>) {
        try {
            withContext(compute) { zones.setZones(found.mapValues { (_, z) -> Zone(z.left, z.top, z.right, z.bottom) }) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Unit
        }
    }

    /** Camera path and test seam: hands one frame to a waiting scene capture. */
    internal fun onSnapshot(snapshot: () -> SceneSnapshot) {
        firstFrame.complete(Unit)
        val wanted = pendingSnapshot ?: return
        pendingSnapshot = null
        wanted.complete(snapshot())
    }

    /** Needed zones that are not saved; a store failure counts every needed zone as missing (the notice is advisory). */
    private suspend fun missingZones(): List<String> {
        if (neededZones.isEmpty()) return emptyList()
        val saved =
            try {
                withContext(compute) { zones.zones().keys }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                emptySet()
            }
        return neededZones.filterNot { it in saved }
    }

    /** Starts the landmarkers (lifecycle start with camera permission). */
    fun start() {
        if (running) return
        running = true
        val previousStop = stopJob
        startJob =
            viewModelScope.launch {
                previousStop?.join()
                try {
                    withContext(compute) {
                        engine.start()
                        // Stopped or cleared while the landmarkers were being created: close them right away.
                        if (!running) engine.close()
                    }
                    if (running) edit { it.copy(engineError = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    fail(e::class.simpleName ?: "unknown")
                }
            }
        engineJobs =
            listOf(
                viewModelScope.launch { engine.results.collect(::onResult) },
                viewModelScope.launch { engine.errors.collect(::fail) }
            )
    }

    /** Releases the landmarkers off the main thread (lifecycle stop, leaving the screen). */
    fun stop() {
        if (!running) return
        running = false
        engineJobs.forEach { it.cancel() }
        engineJobs = emptyList()
        val pending = startJob
        startJob = null
        stopJob =
            viewModelScope.launch {
                pending?.cancelAndJoin()
                withContext(compute) { engine.close() }
            }
        synchronized(recentHands) { recentHands.clear() }
        hold.update(false, 0L)
        edit { it.copy(framed = false, guidance = null) }
    }

    /** Camera could not start; reason is the exception type name only. */
    fun onCameraError(reason: String) = fail(reason)

    /** Camera analysis thread: an upright, un-mirrored frame; it is only passed to the landmarkers, never kept. */
    fun onFrame(bitmap: Bitmap, timestampMs: Long) {
        if (!running) return
        // Scene capture: a private copy of this frame (the camera reuses its buffer); encoded once, then dropped.
        onSnapshot {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            SceneSnapshot { blur -> encodeScene(copy, blur) }
        }
        engine.detect(bitmap, timestampMs)
    }

    /**
     * One landmarker result. Hand results are kept (a few); each pose result is evaluated with the nearest hand result
     * within [VisibilityGate.PAIR_MS] of its timestamp, so a pose arriving after its hands keeps them.
     */
    internal fun onResult(result: LandmarkResult) {
        when (result.stream) {
            FpsStream.HAND -> synchronized(recentHands) {
                recentHands.addLast(result)
                while (recentHands.size > KEPT_HANDS) recentHands.removeFirst()
            }
            FpsStream.POSE -> {
                val hand = synchronized(recentHands) {
                    recentHands.filter { abs(it.timestampMs - result.timestampMs) <= VisibilityGate.PAIR_MS }
                        .minByOrNull { abs(it.timestampMs - result.timestampMs) }
                }
                onFrame(LandmarkFrame(result.timestampMs, result.sets.firstOrNull(), hand?.sets.orEmpty()))
            }
            FpsStream.COMBINED -> Unit
        }
    }

    internal fun onFrame(frame: LandmarkFrame) {
        lastPose = frame
        firstPose.complete(Unit)
        val framed = hold.update(VisibilityGate.framed(frame, needed), frame.timestampMs)
        val guidance = VisibilityGate.guidance(frame, needed)
        edit { it.copy(framed = framed, guidance = guidance) }
    }

    private fun fail(reason: String) {
        edit { it.copy(engineError = reason, canOverride = true, framed = false) }
    }

    private fun edit(change: (FramingUiState.Check) -> FramingUiState.Check) {
        state.update { if (it is FramingUiState.Check) change(it) else it }
    }

    override fun onCleared() {
        pendingSnapshot = null
        running = false
        engineJobs.forEach { it.cancel() }
        val pending = startJob
        // viewModelScope is already cancelled; wait for a start in flight, then close on a background thread.
        CoroutineScope(compute).launch {
            pending?.join()
            engine.close()
        }
    }

    internal companion object {
        const val OVERRIDE_AFTER_MS = 10_000L
        const val KEPT_HANDS = 8
        const val POSE_WAIT_MS = 2_000L
        private const val SCENE_MAX_SIDE = 1_024
        private const val SCENE_JPEG_QUALITY = 85

        /** Bounding box of the visible pose points (stand-in person box for the face blur before the AI answers). */
        fun personFromPose(pose: List<LandmarkPoint>?): VisionZone? {
            val pts = pose.orEmpty().filter { it.visibility > VisibilityGate.MIN_VISIBILITY }
            if (pts.isEmpty()) return null
            return VisionZone(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
        }

        /** Whole frame, scaled to at most [SCENE_MAX_SIDE], face pixelated; the copy is recycled. */
        private fun encodeScene(bitmap: Bitmap, blur: VisionZone?): ByteArray {
            val image = BitmapImage(bitmap)
            try {
                val box = CropBox(0, 0, bitmap.width, bitmap.height)
                val scale = minOf(1f, SCENE_MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
                val size = PixelSize(maxOf(1, (bitmap.width * scale).toInt()), maxOf(1, (bitmap.height * scale).toInt()))
                val regions = listOfNotNull(blur?.let { FaceBlur.inCrop(it, bitmap.width, bitmap.height, box) })
                return BitmapJpegEncoder.encode(image, box, size, SCENE_JPEG_QUALITY, regions)
            } finally {
                image.release()
                bitmap.recycle()
            }
        }
    }
}
