package com.aitherapist.dls.session

import com.aitherapist.dls.data.FrameStore
import com.aitherapist.dls.vision.CropBox
import com.aitherapist.dls.vision.Frame
import com.aitherapist.dls.vision.FrameCropper
import com.aitherapist.dls.vision.JpegEncoder
import com.aitherapist.dls.vision.PixelSize

/**
 * [EvidenceWriter] over [FrameStore] (AD-8): each frame is JPEG-encoded whole (already downscaled by the capture) and
 * written encrypted as `<attemptId>-<n>.bin` under the session's folder. Frames without an image are skipped. Logs nothing.
 */
class FrameStoreEvidence(private val store: FrameStore, private val encoder: JpegEncoder) : EvidenceWriter {
    /** All frames or none: a failure deletes the files already written for this attempt, then rethrows. */
    override suspend fun write(sessionId: String, attemptId: String, frames: List<Frame>): List<String> {
        val written = mutableListOf<String>()
        try {
            frames.mapNotNull { it.image }.forEachIndexed { n, image ->
                val box = CropBox(0, 0, image.width, image.height)
                val jpeg = encoder.encode(image, box, PixelSize(image.width, image.height), FrameCropper.JPEG_QUALITY)
                store.write(sessionId, attemptId, n, jpeg)
                written += store.relativePath(sessionId, attemptId, n)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            discard(written)
            throw e
        }
        return written
    }

    override fun discard(paths: List<String>) {
        paths.forEach { store.deleteRelative(it) }
    }
}
