package com.aitherapist.dls.vision

import android.graphics.Bitmap
import com.aitherapist.dls.catalog.Landmarker
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One captured frame with the landmarks paired to it; [landmarks].pose is null when no pose was found. */
class CapturedSample(val frame: Frame, val landmarks: LandmarkFrame)

/**
 * Background capture for the child runner (Story 3.5): no preview, only the landmarkers a step needs. Frames are
 * passed in from the camera; paired samples come out on [samples]. Never stores or logs frames or landmarks.
 */
interface AttemptCapture {
    val samples: SharedFlow<CapturedSample>

    /** True once the landmarkers failed to start or run; adult-visible only. */
    val failed: StateFlow<Boolean>

    /** The landmarkers to run; empty stops them (content-only step, pause, exit, finish). */
    fun configure(landmarkers: Set<Landmarker>)

    /** Drops any frame in flight (the step changed). */
    fun reset()

    /** Camera analysis thread: an upright, un-mirrored frame with its timestamp on the app's elapsed-realtime clock. */
    fun onFrame(bitmap: Bitmap, timestampMs: Long)

    fun close()
}

/**
 * One frame in flight (AD-3 pairing): a new frame is accepted only after the previous one was answered by every
 * wanted landmarker or dropped. Results carry the timestamp of the frame they ran on. A frame that is never answered
 * within [staleMs] is discarded and left out of the series (never counted as "not visible"). Thread-safe; pure Kotlin.
 */
class SamplePairer(private val staleMs: Long = STALE_MS) {
    private class Pending(val timestampMs: Long, val image: AttemptImage?, val pose: Boolean, val hand: Boolean) {
        var poseSets: List<List<LandmarkPoint>>? = null
        var handSets: List<List<LandmarkPoint>>? = null
        val complete: Boolean get() = (!pose || poseSets != null) && (!hand || handSets != null)
    }

    private var pending: Pending? = null

    /** True when a frame at [timestampMs] may be submitted now (nothing in flight, or the one in flight went stale). */
    @Synchronized
    fun tryBegin(timestampMs: Long, image: () -> AttemptImage?, pose: Boolean, hand: Boolean): Boolean {
        val current = pending
        if (current != null && timestampMs - current.timestampMs < staleMs) return false
        current?.image?.release()
        pending = Pending(timestampMs, image(), pose, hand)
        return true
    }

    /** The landmarkers refused the frame: drop it. */
    @Synchronized
    fun drop(timestampMs: Long) {
        if (pending?.timestampMs == timestampMs) clear()
    }

    /** One landmarker result; the sample once every wanted stream answered, else null. */
    @Synchronized
    fun onResult(result: LandmarkResult): CapturedSample? {
        val entry = pending?.takeIf { it.timestampMs == result.timestampMs } ?: return null
        when (result.stream) {
            FpsStream.POSE -> entry.poseSets = result.sets
            FpsStream.HAND -> entry.handSets = result.sets
            FpsStream.COMBINED -> return null
        }
        if (!entry.complete) return null
        pending = null
        val landmarks = LandmarkFrame(entry.timestampMs, entry.poseSets?.firstOrNull(), entry.handSets.orEmpty())
        return CapturedSample(Frame(entry.timestampMs, entry.image), landmarks)
    }

    @Synchronized
    fun clear() {
        pending?.image?.release()
        pending = null
    }

    companion object {
        const val STALE_MS = 1_000L
    }
}

/** [AttemptCapture] over the MediaPipe [LandmarkEngine]; start and close run on one background thread. */
class LiveCapture
internal constructor(private val engine: LandmarkEngine, worker: CoroutineDispatcher) : AttemptCapture {
    @Inject
    constructor(landmarkers: Landmarkers) : this(landmarkers, Dispatchers.Default.limitedParallelism(1))

    private val scope = CoroutineScope(SupervisorJob() + worker)
    private val pairer = SamplePairer()
    private val out = MutableSharedFlow<CapturedSample>(extraBufferCapacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val samples: SharedFlow<CapturedSample> = out.asSharedFlow()
    private val failure = MutableStateFlow(false)
    override val failed: StateFlow<Boolean> = failure.asStateFlow()

    private val lock = Any()
    private var wanted: Set<Landmarker> = emptySet()
    private var ready = false
    private var closed = false

    init {
        scope.launch { engine.results.collect { r -> pairer.onResult(r)?.let { out.tryEmit(it) } } }
        scope.launch { engine.errors.collect { failure.value = true } }
    }

    override fun configure(landmarkers: Set<Landmarker>) {
        synchronized(lock) {
            if (closed || landmarkers == wanted) return
            val wasIdle = wanted.isEmpty()
            wanted = landmarkers
            pairer.clear()
            when {
                landmarkers.isEmpty() -> {
                    ready = false
                    scope.launch { engine.close() }
                }
                wasIdle -> scope.launch { startEngine() }
            }
        }
    }

    private fun startEngine() {
        val ok =
            try {
                engine.start()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                false // no landmarks: every camera step is cannot tell out_of_frame
            }
        if (!ok) failure.value = true
        synchronized(lock) {
            ready = ok && wanted.isNotEmpty() && !closed
            if (ok && !ready) engine.close()
        }
    }

    override fun reset() = pairer.clear()

    override fun onFrame(bitmap: Bitmap, timestampMs: Long) {
        val (pose, hand) =
            synchronized(lock) {
                if (closed || wanted.isEmpty() || !ready) return
                (Landmarker.POSE in wanted) to (Landmarker.HAND in wanted)
            }
        if (!pairer.tryBegin(timestampMs, { BitmapImage(downscaled(bitmap)) }, pose, hand)) return
        if (!engine.detect(bitmap, timestampMs, pose, hand)) pairer.drop(timestampMs)
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            wanted = emptySet()
            ready = false
        }
        pairer.clear()
        scope.launch { engine.close() }.invokeOnCompletion { scope.cancel() }
    }

    /** Evidence and crop frames are kept small: longest side at most [MAX_SIDE_PX] (memory bound per window). */
    private fun downscaled(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_SIDE_PX) return bitmap
        val scale = MAX_SIDE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).roundToInt()),
            max(1, (bitmap.height * scale).roundToInt()),
            true
        )
    }

    private companion object {
        const val MAX_SIDE_PX = 480
    }
}
