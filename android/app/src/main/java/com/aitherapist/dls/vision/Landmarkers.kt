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
 * LIVE_STREAM mode, GPU first with CPU fallback, results exposed as a Flow;
 * pose points carry MediaPipe visibility.
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

/** One landmarker result for the frame captured at [timestampMs]; one point list per person or hand. */
data class LandmarkResult(val stream: FpsStream, val timestampMs: Long, val sets: List<List<LandmarkPoint>>)

enum class InferenceDelegate { GPU, CPU }

/** The live landmark engine as the framing check uses it (fakeable in tests). */
interface LandmarkEngine {
    val results: SharedFlow<LandmarkResult>
    val errors: SharedFlow<String>

    fun start(): InferenceDelegate

    fun detect(bitmap: Bitmap, timestampMs: Long): Boolean

    /** Runs only the chosen landmarkers (AD-3 `Detector.landmarkers`); by default both. */
    fun detect(bitmap: Bitmap, timestampMs: Long, pose: Boolean, hand: Boolean): Boolean = detect(bitmap, timestampMs)

    fun close()
}

/**
 * Pose + Hand landmarkers in LIVE_STREAM mode. Frames passed to [detect] must already be
 * un-mirrored: raw front-camera analysis frames, rotated upright and not flipped. Never logs frames or landmarks.
 */
class Landmarkers
@Inject
constructor(@ApplicationContext private val context: Context) : LandmarkEngine {
    private val _results =
        MutableSharedFlow<LandmarkResult>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val results: SharedFlow<LandmarkResult> = _results.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Runtime errors reported by MediaPipe, as exception type names only. */
    override val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var pose: PoseLandmarker? = null
    private var hand: HandLandmarker? = null
    private var lastTimestampMs = -1L

    /** Creates both landmarkers, GPU first then CPU. Throws only if CPU also fails. */
    @Synchronized
    override fun start(): InferenceDelegate {
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
    override fun detect(bitmap: Bitmap, timestampMs: Long): Boolean = detect(bitmap, timestampMs, pose = true, hand = true)

    @Synchronized
    override fun detect(bitmap: Bitmap, timestampMs: Long, pose: Boolean, hand: Boolean): Boolean {
        val p = this.pose ?: return false
        val h = this.hand ?: return false
        if (!pose && !hand) return false
        if (timestampMs <= lastTimestampMs) return false // MediaPipe requires strictly increasing timestamps.
        lastTimestampMs = timestampMs
        return try {
            val image = BitmapImageBuilder(bitmap).build()
            if (pose) p.detectAsync(image, timestampMs)
            if (hand) h.detectAsync(image, timestampMs)
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            _errors.tryEmit(e::class.simpleName ?: "unknown")
            false
        }
    }

    @Synchronized
    override fun close() {
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
                        _results.tryEmit(
                            LandmarkResult(FpsStream.POSE, result.timestampMs(), result.landmarks().toPoints(withVisibility = true))
                        )
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
                        _results.tryEmit(
                            LandmarkResult(FpsStream.HAND, result.timestampMs(), result.landmarks().toPoints(withVisibility = false))
                        )
                    }.setErrorListener { e -> _errors.tryEmit(e::class.simpleName ?: "unknown") }
                    .build()
            )
    }

    private fun baseOptions(model: String, delegate: Delegate) =
        BaseOptions.builder().setModelAssetPath(model).setDelegate(delegate).build()

    // Pose landmarks carry MediaPipe visibility; hand landmarks have none and use 1f.
    private fun List<List<NormalizedLandmark>>.toPoints(withVisibility: Boolean) = map { set ->
        set.map { LandmarkPoint(it.x(), it.y(), if (withVisibility) it.visibility().orElse(0f) else 1f) }
    }

    private companion object {
        const val POSE_MODEL = "pose_landmarker_full.task"
        const val HAND_MODEL = "hand_landmarker.task"
    }
}
