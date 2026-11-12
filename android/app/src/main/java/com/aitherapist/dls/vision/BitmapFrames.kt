package com.aitherapist.dls.vision

import android.graphics.Bitmap
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
    override fun encode(image: AttemptImage, box: CropBox, size: PixelSize, quality: Int): ByteArray {
        val source = (image as? BitmapImage)?.bitmapOrNull() ?: throw IllegalStateException("image released")
        val cropped = Bitmap.createBitmap(source, box.left, box.top, box.width, box.height)
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
