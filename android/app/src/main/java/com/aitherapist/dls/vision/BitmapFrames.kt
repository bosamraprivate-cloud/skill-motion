package com.aitherapist.dls.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream

/** In-memory attempt frame backed by a Bitmap; never written to disk. */
class BitmapImage(bitmap: Bitmap) : AttemptImage {
    @Volatile private var bitmap: Bitmap? = bitmap
    override val width: Int = bitmap.width
    override val height: Int = bitmap.height

    fun bitmapOrNull(): Bitmap? = bitmap?.takeUnless { it.isRecycled }

    override fun release() {
        // Drop the reference only: the camera pipeline may still hand the same Bitmap to MediaPipe.
        bitmap = null
    }
}

/** Shell JPEG encoder for [FrameCropper]: crop, scale, compress in memory. */
object BitmapJpegEncoder : JpegEncoder {
    override fun encode(image: AttemptImage, box: CropBox, size: PixelSize, quality: Int): ByteArray =
        encode(image, box, size, quality, emptyList())

    override fun encode(image: AttemptImage, box: CropBox, size: PixelSize, quality: Int, blur: List<CropBox>): ByteArray {
        val source = (image as? BitmapImage)?.bitmapOrNull() ?: throw IllegalStateException("image released")
        val cropped =
            Bitmap.createBitmap(source, box.left, box.top, box.width, box.height).let { c ->
                if (blur.isEmpty()) c else pixelate(c, blur, source)
            }
        val scaled = if (size.width == box.width &&
            size.height == box.height
        ) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, size.width, size.height, true)
        }
        try {
            return ByteArrayOutputStream().use { out ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "jpeg encode failed" }
                out.toByteArray()
            }
        } finally {
            if (scaled !== source) scaled.recycle()
            if (cropped !== source && cropped !== scaled) cropped.recycle()
        }
    }
}

private const val PIXEL_BLOCKS = 6

/** Pixelates each [regions] box (pixels of [bitmap]) into coarse blocks; returns a mutable copy (never [source]). */
private fun pixelate(bitmap: Bitmap, regions: List<CropBox>, source: Bitmap): Bitmap {
    val out = if (bitmap.isMutable && bitmap !== source) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
    if (out !== bitmap && bitmap !== source) bitmap.recycle()
    val canvas = Canvas(out)
    regions.forEach { r ->
        val l = r.left.coerceIn(0, out.width - 1)
        val t = r.top.coerceIn(0, out.height - 1)
        val w = (r.right.coerceAtMost(out.width) - l).coerceAtLeast(1)
        val h = (r.bottom.coerceAtMost(out.height) - t).coerceAtLeast(1)
        val part = Bitmap.createBitmap(out, l, t, w, h)
        val small = Bitmap.createScaledBitmap(part, PIXEL_BLOCKS, PIXEL_BLOCKS, true)
        val big = Bitmap.createScaledBitmap(small, w, h, false)
        canvas.drawBitmap(big, l.toFloat(), t.toFloat(), null)
        big.recycle()
        small.recycle()
        if (part !== out) part.recycle()
    }
    return out
}
