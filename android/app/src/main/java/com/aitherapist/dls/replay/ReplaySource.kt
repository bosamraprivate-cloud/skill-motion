package com.aitherapist.dls.replay

import com.aitherapist.dls.catalog.Landmarker
import com.aitherapist.dls.vision.AttemptImage
import com.aitherapist.dls.vision.Frame
import com.aitherapist.dls.vision.LandmarkFrame
import java.io.Closeable
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Decodes a stored clip (Android: MediaMetadataRetriever); fakeable in JVM tests. */
fun interface ClipDecoder {
    /** The opened clip, or null when [file] is not a decodable video. [mirrored] flips every frame left/right. */
    fun open(file: File, mirrored: Boolean): DecodedClip?
}

/** One opened clip. Frames are upright, downscaled like live capture and already un-mirrored when asked. */
interface DecodedClip : Closeable {
    val durationMs: Long

    /** The frame nearest [timeMs], or null when none could be decoded there. */
    fun frameAt(timeMs: Long): AttemptImage?
}

/** Landmarkers in MediaPipe VIDEO mode (synchronous, increasing timestamps per opened detector). */
fun interface VideoLandmarkEngine {
    fun open(pose: Boolean, hand: Boolean): VideoDetector
}

interface VideoDetector : Closeable {
    fun detect(image: AttemptImage, timestampMs: Long): LandmarkFrame
}

/**
 * Replay input (Story 3.7): decodes a clip at the live analysis rate and runs the step's landmarkers in VIDEO mode,
 * handing each frame with its landmarks to [read]'s sink (the runner's window buffers). Never logs frames or landmarks.
 */
class ReplaySource(private val decoder: ClipDecoder, private val engine: VideoLandmarkEngine, private val intervalMs: Long = INTERVAL_MS) {
    /** The clip's duration, or null when it cannot be opened. */
    fun durationMs(file: File): Long? = decoder.open(file, mirrored = false)?.use { it.durationMs }

    /**
     * Feeds every decoded frame of [file] to [sink]. False (cannot tell `not_judgeable`) when the clip cannot be
     * decoded, is longer than [MAX_DURATION_MS], or its decoded frames cover less than [MIN_COVERAGE] of its duration.
     */
    suspend fun read(
        file: File,
        landmarkers: Set<Landmarker>,
        mirrored: Boolean = false,
        sink: suspend (Frame?, LandmarkFrame?) -> Unit
    ): Boolean {
        val clip = decoder.open(file, mirrored) ?: return false
        clip.use {
            if (it.durationMs <= 0 || it.durationMs > MAX_DURATION_MS) return false
            val pose = Landmarker.POSE in landmarkers
            val hand = Landmarker.HAND in landmarkers
            val detector = if (pose || hand) engine.open(pose, hand) else null
            try {
                var decoded = 0
                var slots = 0
                var t = 0L
                while (t <= it.durationMs) {
                    coroutineContext.ensureActive()
                    slots++
                    val image = it.frameAt(t)
                    if (image != null) {
                        decoded++
                        val landmarks = detector?.detect(image, t) ?: LandmarkFrame(t, null)
                        sink(Frame(t, image), landmarks)
                    }
                    t += intervalMs
                }
                return decoded > 0 && decoded >= MIN_COVERAGE * slots
            } finally {
                detector?.close()
            }
        }
    }

    companion object {
        /** About the live analysis rate (one frame in flight at roughly 10 fps). */
        const val INTERVAL_MS = 100L

        /** Longer clips are skipped (memory and time bound; a step clip is far shorter). */
        const val MAX_DURATION_MS = 90_000L

        /** Decoded frames must cover at least this share of the clip. */
        const val MIN_COVERAGE = 0.8
    }
}
