/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by the Daily Living Skills app authors, 2026: adapted from mediapipe-samples
 * PoseLandmarkerHelper.kt and HandLandmarkerHelper.kt; runs Pose and Hand together in
 * LIVE_STREAM mode, GPU first with CPU fallback, results exposed as a Flow.
 */
package com.aitherapist.dls.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Normalised landmark position in un-mirrored image space (x to the subject's left grows). */
data class LandmarkPoint(val x: Float, val y: Float)

/** One landmarker result for the frame captured at [timestampMs]; one point list per person or hand. */
data class LandmarkResult(val stream: FpsStream, val timestampMs: Long, val sets: List<List<LandmarkPoint>>)

enum class InferenceDelegate { GPU, CPU }

/** MediaPipe pose landmark indices used by the diagnostics overlay. */
object PoseLandmarkIndex {
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
}

/**
 * Pose + Hand landmarkers in LIVE_STREAM mode. Frames passed to [detect] must already be
 * un-mirrored (see `camera/Mirror`). Never logs frames or landmarks.
 */
class Landmarkers
@Inject
constructor(@ApplicationContext private val context: Context) {
    private val _results =
        MutableSharedFlow<LandmarkResult>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val results: SharedFlow<LandmarkResult> = _results.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Runtime errors reported by MediaPipe, as exception type names only. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var pose: PoseLandmarker? = null
    private var hand: HandLandmarker? = null
    private var lastTimestampMs = -1L

    /** Creates both landmarkers, GPU first then CPU. Throws only if CPU also fails. */
    @Synchronized
    fun start(): InferenceDelegate {
        close()
        return try {
            create(Delegate.GPU)
            InferenceDelegate.GPU
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            close()
            create(Delegate.CPU)
            InferenceDelegate.CPU
        }
    }

    /**
     * Runs both landmarkers on one un-mirrored frame; results arrive on [results].
     * Returns true when the frame was submitted, false when dropped or inference threw
     * (the error goes to [errors] by type name).
     */
    @Synchronized
    fun detect(bitmap: Bitmap, timestampMs: Long): Boolean {
        val p = pose ?: return false
        val h = hand ?: return false
        if (timestampMs <= lastTimestampMs) return false // MediaPipe requires strictly increasing timestamps.
        lastTimestampMs = timestampMs
        return try {
            val image = BitmapImageBuilder(bitmap).build()
            p.detectAsync(image, timestampMs)
            h.detectAsync(image, timestampMs)
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            _errors.tryEmit(e::class.simpleName ?: "unknown")
            false
        }
    }

    @Synchronized
    fun close() {
        pose?.close()
        hand?.close()
        pose = null
        hand = null
        lastTimestampMs = -1L
    }

    private fun create(delegate: Delegate) {
        pose =
            PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions
                    .builder()
                    .setBaseOptions(baseOptions(POSE_MODEL, delegate))
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
                    .setResultListener { result, _ ->
                        _results.tryEmit(LandmarkResult(FpsStream.POSE, result.timestampMs(), result.landmarks().toPoints()))
                    }.setErrorListener { e -> _errors.tryEmit(e::class.simpleName ?: "unknown") }
                    .build()
            )
        hand =
            HandLandmarker.createFromOptions(
                context,
                HandLandmarker.HandLandmarkerOptions
                    .builder()
                    .setBaseOptions(baseOptions(HAND_MODEL, delegate))
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(2)
                    .setResultListener { result, _ ->
                        _results.tryEmit(LandmarkResult(FpsStream.HAND, result.timestampMs(), result.landmarks().toPoints()))
                    }.setErrorListener { e -> _errors.tryEmit(e::class.simpleName ?: "unknown") }
                    .build()
            )
    }

    private fun baseOptions(model: String, delegate: Delegate) =
        BaseOptions.builder().setModelAssetPath(model).setDelegate(delegate).build()

    private fun List<List<NormalizedLandmark>>.toPoints() = map { set -> set.map { LandmarkPoint(it.x(), it.y()) } }

    private companion object {
        const val POSE_MODEL = "pose_landmarker_full.task"
        const val HAND_MODEL = "hand_landmarker.task"
    }
}
