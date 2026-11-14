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
 * PoseLandmarkerHelper.kt and HandLandmarkerHelper.kt VIDEO mode for replay (Story 3.7);
 * CPU delegate; pose points carry MediaPipe visibility.
 */
package com.aitherapist.dls.vision

import android.content.Context
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.io.Closeable

/**
 * Pose + Hand landmarkers in VIDEO running mode for replay: one instance per clip, synchronous results, strictly
 * increasing timestamps. Images must be [BitmapImage]s, upright and un-mirrored. Never logs frames or landmarks.
 */
class VideoLandmarkers(context: Context, pose: Boolean, hand: Boolean) : Closeable {
    private val poseLandmarker: PoseLandmarker? =
        if (!pose) {
            null
        } else {
            PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions
                    .builder()
                    .setBaseOptions(baseOptions(POSE_MODEL))
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumPoses(1)
                    .build()
            )
        }
    private val handLandmarker: HandLandmarker? =
        if (!hand) {
            null
        } else {
            HandLandmarker.createFromOptions(
                context,
                HandLandmarker.HandLandmarkerOptions
                    .builder()
                    .setBaseOptions(baseOptions(HAND_MODEL))
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(2)
                    .build()
            )
        }

    /** Landmarks for one frame; a frame whose image was released yields no pose (not visible). */
    fun detect(image: AttemptImage, timestampMs: Long): LandmarkFrame {
        val bitmap = (image as? BitmapImage)?.bitmapOrNull() ?: return LandmarkFrame(timestampMs, null)
        val mp = BitmapImageBuilder(bitmap).build()
        val pose = poseLandmarker?.detectForVideo(mp, timestampMs)?.landmarks()?.toPoints(withVisibility = true)?.firstOrNull()
        val hands = handLandmarker?.detectForVideo(mp, timestampMs)?.landmarks()?.toPoints(withVisibility = false).orEmpty()
        return LandmarkFrame(timestampMs, pose, hands)
    }

    override fun close() {
        poseLandmarker?.close()
        handLandmarker?.close()
    }

    private fun baseOptions(model: String) = BaseOptions.builder().setModelAssetPath(model).setDelegate(Delegate.CPU).build()

    private fun List<List<NormalizedLandmark>>.toPoints(withVisibility: Boolean) = map { set ->
        set.map { LandmarkPoint(it.x(), it.y(), if (withVisibility) it.visibility().orElse(0f) else 1f) }
    }

    private companion object {
        const val POSE_MODEL = "pose_landmarker_full.task"
        const val HAND_MODEL = "hand_landmarker.task"
    }
}
