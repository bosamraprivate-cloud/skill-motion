package com.aitherapist.dls.ui.therapist

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FULL_PX = 1280
private const val FRAMES_PER_ATTEMPT = 3

/** Loads one frame decrypted (in memory only) and decoded to at most maxPx on its long side; null when unavailable. */
typealias FrameLoader = suspend (relativePath: String, maxPx: Int) -> ImageBitmap?

/** Thumbnail size (long side, px) used by the strip; the ViewModel caches images of this size. */
const val EVIDENCE_THUMB_PX = 240

/**
 * Evidence strip (UX-DR12): up to 3 decrypted thumbnails of one attempt (start/middle/end), downscaled; tap enlarges.
 * A missing or undecodable frame shows a calm placeholder.
 */
@Composable
fun EvidenceStrip(framePaths: List<String>, loadFrame: FrameLoader, modifier: Modifier = Modifier) {
    var enlarged by remember { mutableStateOf<String?>(null) }
    val slots = framePaths.take(FRAMES_PER_ATTEMPT).let { it + List(FRAMES_PER_ATTEMPT - it.size) { null } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        slots.forEachIndexed { i, path ->
            val label = stringResource(R.string.detail_frame, westernDigits(i + 1))
            val image by produceState<ImageBitmap?>(null, path) { value = path?.let { loadFrame(it, EVIDENCE_THUMB_PX) } }
            Box(
                Modifier
                    .width(120.dp)
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(Radius.Sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (image != null && path != null) Modifier.clickable(role = Role.Button) { enlarged = path } else Modifier)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center
            ) {
                val bmp = image
                if (bmp != null) {
                    Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
                } else {
                    FramePlaceholder()
                }
            }
        }
    }
    enlarged?.let { path -> FrameViewer(path, loadFrame) { enlarged = null } }
}

@Composable
private fun FramePlaceholder() {
    Icon(
        Icons.Filled.Info,
        contentDescription = stringResource(R.string.detail_frame_missing),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
    )
}

/** Full-size viewer of one frame; tap anywhere closes it. Decode error shows the placeholder. */
@Composable
private fun FrameViewer(path: String, loadFrame: FrameLoader, onClose: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, path) { value = loadFrame(path, FULL_PX) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(Radius.Md),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(Spacing.S4).clickable(role = Role.Button, onClick = onClose)
        ) {
            Box(Modifier.padding(Spacing.S2), contentAlignment = Alignment.Center) {
                val bmp = image
                if (bmp != null) {
                    Image(bmp, contentDescription = stringResource(R.string.detail_frame_close), contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.size(320.dp, 240.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.detail_frame_missing), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Decodes JPEG [bytes] downscaled to at most [maxPx] on its long side; null on any failure. */
internal suspend fun decodeFrameBytes(bytes: ByteArray, maxPx: Int): ImageBitmap? = withContext(Dispatchers.Default) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        null
    }
}
