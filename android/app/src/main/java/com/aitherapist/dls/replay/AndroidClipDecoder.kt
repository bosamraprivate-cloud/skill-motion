package com.aitherapist.dls.replay

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import com.aitherapist.dls.vision.AttemptImage
import com.aitherapist.dls.vision.BitmapImage
import java.io.File
import javax.inject.Inject

/** [ClipDecoder] over MediaMetadataRetriever: frames come out rotated upright and downscaled like live capture. */
class AndroidClipDecoder
@Inject
constructor() : ClipDecoder {
    override fun open(file: File, mirrored: Boolean): DecodedClip? {
        if (!file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            if (duration == null || !hasVideo) {
                retriever.release()
                null
            } else {
                RetrieverClip(retriever, duration, mirrored)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            retriever.release()
            null
        }
    }

    private class RetrieverClip(
        private val retriever: MediaMetadataRetriever,
        override val durationMs: Long,
        private val mirrored: Boolean
    ) : DecodedClip {
        override fun frameAt(timeMs: Long): AttemptImage? {
            // Decoded straight to at most MAX_SIDE_PX (aspect kept): no full-size bitmap is ever allocated.
            val bitmap =
                try {
                    retriever.getScaledFrameAtTime(timeMs * US_PER_MS, MediaMetadataRetriever.OPTION_CLOSEST, MAX_SIDE_PX, MAX_SIDE_PX)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    null
                } ?: return null
            return BitmapImage(if (mirrored) flipped(bitmap) else bitmap)
        }

        override fun close() = retriever.release()

        /** Front-camera recordings are saved mirrored: flip back before landmarks so left/right rules hold. */
        private fun flipped(bitmap: Bitmap): Bitmap {
            val matrix = Matrix().apply { preScale(-1f, 1f) }
            val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (out !== bitmap) bitmap.recycle()
            return out
        }
    }

    private companion object {
        const val US_PER_MS = 1_000L

        /** Same bound as live evidence frames (LiveCapture). */
        const val MAX_SIDE_PX = 480
    }
}
