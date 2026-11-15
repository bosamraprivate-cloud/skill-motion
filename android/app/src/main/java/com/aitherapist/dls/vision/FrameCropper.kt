package com.aitherapist.dls.vision

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A crop rectangle in pixels of the upright frame; [right] and [bottom] are exclusive. */
data class CropBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Output size in pixels after downscaling. */
data class PixelSize(val width: Int, val height: Int)

/**
 * Pure crop math for cloud frames (AD-7). The box is the union of all hand landmarks, the pose face
 * landmarks (nose, mouth left/right) and the context zones, expanded by 25% per side, made at least 20%
 * of the frame's shorter side, and clamped to the frame. Encoding lives in shell code ([JpegEncoder]).
 */
object FrameCropper {
    const val MARGIN = 0.25
    const val MIN_SIDE_FRACTION = 0.2
    const val MAX_SIDE_PX = 512
    const val JPEG_QUALITY = 80

    /** Pose indices of the face landmarks included in the crop. */
    val FACE_LANDMARKS: Set<Int> = setOf(PoseLandmarkIndex.NOSE, PoseLandmarkIndex.MOUTH_LEFT, PoseLandmarkIndex.MOUTH_RIGHT)

    /** Normalised points for the crop from one landmark frame: all hand points plus visible face points. */
    fun cropPoints(frame: LandmarkFrame?): List<LandmarkPoint> {
        if (frame == null) return emptyList()
        val face =
            frame.pose
                ?.let { pose -> FACE_LANDMARKS.mapNotNull { pose.getOrNull(it) } }
                .orEmpty()
                .filter { it.visibility > VisibilityGate.MIN_VISIBILITY }
        return frame.hands.flatten() + face
    }

    /** Crop box in pixels for a [frameWidth] x [frameHeight] frame; null when there are no points and no zones (nothing to crop around). */
    fun cropBox(frameWidth: Int, frameHeight: Int, points: List<LandmarkPoint>, zones: Collection<Zone> = emptyList()): CropBox? {
        require(frameWidth > 0 && frameHeight > 0) { "frame must not be empty" }
        val xs = points.map { it.x.toDouble() } + zones.flatMap { listOf(it.left.toDouble(), it.right.toDouble()) }
        val ys = points.map { it.y.toDouble() } + zones.flatMap { listOf(it.top.toDouble(), it.bottom.toDouble()) }
        if (xs.isEmpty()) return null
        var left = xs.min() * frameWidth
        var right = xs.max() * frameWidth
        var top = ys.min() * frameHeight
        var bottom = ys.max() * frameHeight
        val marginX = (right - left) * MARGIN
        val marginY = (bottom - top) * MARGIN
        left -= marginX
        right += marginX
        top -= marginY
        bottom += marginY
        val minSide = min(frameWidth, frameHeight) * MIN_SIDE_FRACTION
        val (l, r) = enforceMin(left, right, minSide)
        val (t, b) = enforceMin(top, bottom, minSide)
        val (cl, cr) = clamp(l, r, frameWidth)
        val (ct, cb) = clamp(t, b, frameHeight)
        return CropBox(cl, ct, cr, cb)
    }

    /** Size after downscaling so the longest side is at most [MAX_SIDE_PX]; never upscales. */
    fun scaledSize(box: CropBox): PixelSize {
        val longest = max(box.width, box.height)
        if (longest <= MAX_SIDE_PX) return PixelSize(box.width, box.height)
        val scale = MAX_SIDE_PX.toDouble() / longest
        return PixelSize(max(1, (box.width * scale).roundToInt()), max(1, (box.height * scale).roundToInt()))
    }

    private fun enforceMin(a: Double, b: Double, minSide: Double): Pair<Double, Double> {
        if (b - a >= minSide) return a to b
        val center = (a + b) / 2
        return (center - minSide / 2) to (center + minSide / 2)
    }

    /** Shifts the span inside 0..limit when it fits, otherwise clips it; result has at least 1 px. */
    private fun clamp(a: Double, b: Double, limit: Int): Pair<Int, Int> {
        var lo = a
        var hi = b
        if (lo < 0) {
            hi -= lo
            lo = 0.0
        }
        if (hi > limit) {
            lo -= hi - limit
            hi = limit.toDouble()
        }
        val start = lo.coerceAtLeast(0.0).roundToInt().coerceIn(0, limit - 1)
        val end = hi.roundToInt().coerceIn(start + 1, limit)
        return start to end
    }
}

/** Crops, scales and JPEG-encodes one attempt image (shell implementation wraps Bitmap). */
fun interface JpegEncoder {
    fun encode(image: AttemptImage, box: CropBox, size: PixelSize, quality: Int): ByteArray

    /** As [encode], with [blur] regions (pixels relative to [box]) pixelated first (Story 6.4 face blur). */
    fun encode(image: AttemptImage, box: CropBox, size: PixelSize, quality: Int, blur: List<CropBox>): ByteArray =
        encode(image, box, size, quality)
}
