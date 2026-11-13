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
 * CameraFragment.kt and PoseLandmarkerHelper.detectLiveStream; Compose-friendly controller,
 * front camera only, frames rotated upright before delivery (raw front frames are already un-mirrored).
 */
package com.aitherapist.dls.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Binds the front camera: a preview for the user (may stay mirrored) and an analysis stream
 * (RGBA, keep-only-latest, about 640x480) whose frames are rotated upright (not flipped:
 * raw front frames are already un-mirrored), then handed to [onFrame] with the frame's sensor timestamp in ms (imageInfo.timestamp, elapsed-realtime base). Never saves frames.
 */
class CameraController(private val context: Context) {
    private var provider: ProcessCameraProvider? = null
    private var executor: ExecutorService? = null

    @Volatile private var disposed = false

    /** Bumped on every bind and unbind: a provider future that completes for an older bind does nothing. */
    @Volatile private var generation = 0

    /** [onError] receives the exception type name only (e.g. no front camera). */
    fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, onFrame: (Bitmap, Long) -> Unit, onError: (String) -> Unit) =
        bindInternal(owner, surfaceProvider, onFrame, onError) {}

    /** Story 3.5: the analysis stream only, no preview (the child never sees the camera). [onError] as in [bind]. */
    fun bindAnalysis(owner: LifecycleOwner, onFrame: (Bitmap, Long) -> Unit, onError: (String) -> Unit) =
        bindInternal(owner, null, onFrame, onError) {}

    /**
     * Binds the front preview only (no analysis, no frame work). [onAspect] gets the upright preview width / height once,
     * on the main thread; [onError] as in [bind].
     */
    fun bindPreview(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, onAspect: (Float) -> Unit, onError: (String) -> Unit) =
        bindInternal(owner, surfaceProvider, null, onError, onAspect)

    private fun bindInternal(
        owner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider?,
        onFrame: ((Bitmap, Long) -> Unit)?,
        onError: (String) -> Unit,
        onAspect: (Float) -> Unit
    ) {
        disposed = false
        val token = ++generation
        // The provider future is a process-wide shared instance, so it is not cancelled (that would break later binds);
        // the generation token makes a completion after unbind or rebind a no-op instead.
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed || token != generation) return@addListener
            try {
                val info = bindNow(future.get(), owner, surfaceProvider, onFrame)
                val size = info?.resolution
                if (size != null && size.width > 0 && size.height > 0) {
                    val quarter = info.rotationDegrees % HALF_TURN != 0
                    val w = if (quarter) size.height else size.width
                    val h = if (quarter) size.width else size.height
                    onAspect(w.toFloat() / h)
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                unbind()
                onError(e::class.simpleName ?: "unknown")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindNow(
        cameraProvider: ProcessCameraProvider,
        owner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider?,
        onFrame: ((Bitmap, Long) -> Unit)?
    ): androidx.camera.core.ResolutionInfo? {
        provider = cameraProvider
        val selector =
            ResolutionSelector
                .Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(TARGET_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                ).build()
        val preview = surfaceProvider?.let { Preview.Builder().setResolutionSelector(selector).build().apply { this.surfaceProvider = it } }
        if (onFrame == null) {
            checkNotNull(preview) { "nothing to bind" }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            return preview.resolutionInfo
        }
        val analysisExecutor = Executors.newSingleThreadExecutor().also { executor = it }
        val analysis =
            ImageAnalysis
                .Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
        analysis.setAnalyzer(analysisExecutor) { proxy ->
            proxy.use { onFrame(uprightBitmap(it), it.imageInfo.timestamp / NANOS_PER_MS) }
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, *listOfNotNull(preview, analysis).toTypedArray())
        return null
    }

    /** Releases the camera and the analysis thread. */
    fun unbind() {
        disposed = true
        generation++
        provider?.unbindAll()
        provider = null
        executor?.shutdown()
        executor = null
    }

    private fun uprightBitmap(proxy: ImageProxy): Bitmap {
        val source = proxy.toBitmap()
        val matrix = transformMatrix(source.width, source.height, proxy.imageInfo.rotationDegrees)
        val upright = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (upright !== source) source.recycle()
        return upright
    }

    /** Rotate upright only (no flip: raw front frames are already un-mirrored), built from the pure [FrameTransform] by mapping three corners. */
    private fun transformMatrix(width: Int, height: Int, rotation: Int): Matrix {
        val w = width.toFloat()
        val h = height.toFloat()
        val outW = if (FrameTransform.swapsAxes(rotation)) h else w
        val outH = if (FrameTransform.swapsAxes(rotation)) w else h
        val corners = listOf(NormPoint(0f, 0f), NormPoint(1f, 0f), NormPoint(0f, 1f))
        val src = corners.flatMap { listOf(it.x * w, it.y * h) }.toFloatArray()
        val dst =
            corners
                .map { FrameTransform.map(it, rotation, flip = false) }
                .flatMap { listOf(it.x * outW, it.y * outH) }
                .toFloatArray()
        return Matrix().apply { setPolyToPoly(src, 0, dst, 0, corners.size) }
    }

    private companion object {
        val TARGET_SIZE = Size(640, 480)
        const val HALF_TURN = 180
        const val NANOS_PER_MS = 1_000_000L
    }
}
