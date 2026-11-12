package com.aitherapist.dls.ui.child

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.gif.AnimatedImageDecoder
import coil3.gif.MovieDrawable
import coil3.gif.repeatCount
import coil3.request.ImageRequest

/** What [CharacterView] shows: an asset [path] (null = nothing), whether it animates, and Coil's repeat count. */
data class ClipPlayback(val path: String?, val animate: Boolean, val repeatCount: Int)

/**
 * Pure playback decision. Animates [clip] unless [stillMode] is set or there is no clip; otherwise shows [still],
 * or the first frame of [clip] when there is no still. Looping clips repeat forever; others play once.
 */
fun clipPlayback(clip: String?, still: String?, loop: Boolean, stillMode: Boolean): ClipPlayback {
    val animate = clip != null && !stillMode
    val path = if (animate) clip else still ?: clip
    return ClipPlayback(path, animate, if (loop) MovieDrawable.REPEAT_INFINITE else 0)
}

/**
 * Plays one character clip from assets (animated WebP via Coil 3). Callers show at most one moving clip at a time.
 *
 * @param clip asset path of the animated WebP, relative to the assets root.
 * @param still asset path of a still frame; when null the first frame of [clip] is the still.
 * @param loop true for reaction clips; false plays once and holds the last frame (step segments).
 * @param stillMode true shows only the still frame (picker cards, list thumbnails, the preview's still-frames switch).
 */
@Composable
fun CharacterView(
    clip: String?,
    loop: Boolean,
    modifier: Modifier = Modifier,
    stillMode: Boolean = false,
    still: String? = null,
    onError: () -> Unit = {}
) {
    val playback = clipPlayback(clip, still, loop, stillMode)
    val path = playback.path ?: return
    val context = LocalContext.current
    val request =
        ImageRequest
            .Builder(context)
            .data("$ASSET_URI$path")
            .apply {
                if (playback.animate) {
                    decoderFactory(AnimatedImageDecoder.Factory())
                    repeatCount(playback.repeatCount)
                }
                // Without the animated decoder Coil decodes only the first frame (the still).
            }.build()
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        onState = { state ->
            if (state is AsyncImagePainter.State.Error) {
                Log.w(LOG_TAG, "clip failed: ${state.result.throwable::class.simpleName}") // type only, no data
                onError()
            }
        }
    )
}

private const val ASSET_URI = "file:///android_asset/"
private const val LOG_TAG = "DlsCharacter"
