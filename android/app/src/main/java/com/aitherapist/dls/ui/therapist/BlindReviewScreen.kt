package com.aitherapist.dls.ui.therapist

import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.SectionCard
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import java.io.File

@Composable
fun BlindReviewRoute(viewModel: BlindReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BlindReviewScreen(state, viewModel::rate, viewModel::skip, viewModel::retry, viewModel::image)
}

/**
 * Blind review (Story 5.7): the step sentence and the attempt's frames only. Nothing on this screen (text, TalkBack
 * labels) names the app's verdict, reason, level, verifier or the child.
 */
@Composable
fun BlindReviewScreen(
    state: BlindReviewUiState,
    onRate: (BlindAnswer) -> Unit,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
    loadFrame: FrameLoader,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.S4),
        verticalArrangement = Arrangement.spacedBy(Spacing.S4)
    ) {
        PageHeader(stringResource(R.string.blind_title))
        Text(
            stringResource(R.string.blind_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (state) {
            BlindReviewUiState.Loading -> Skeleton(rows = 3)
            BlindReviewUiState.Empty -> EmptyState(Icons.Filled.Info, stringResource(R.string.blind_empty))
            is BlindReviewUiState.AllRated ->
                EmptyState(Icons.Filled.CheckCircle, stringResource(R.string.blind_all_rated, westernDigits(state.total)))
            is BlindReviewUiState.Error -> ErrorState(stringResource(R.string.blind_error, state.reason), onRetry)
            is BlindReviewUiState.Content -> BlindContent(state, onRate, onSkip, loadFrame)
        }
    }
}

@Composable
private fun BlindContent(state: BlindReviewUiState.Content, onRate: (BlindAnswer) -> Unit, onSkip: () -> Unit, loadFrame: FrameLoader) {
    val width = Modifier.widthIn(max = 760.dp).fillMaxWidth()
    Text(
        stringResource(R.string.blind_progress, westernDigits(state.rated), westernDigits(state.total)),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SectionCard(stringResource(R.string.blind_step), width) {
        Text(
            state.attempt.stepText,
            style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Rtl),
            modifier = Modifier.fillMaxWidth()
        )
        state.attempt.clip?.let { BlindClip(it, Modifier.widthIn(max = 640.dp).fillMaxWidth()) }
        if (state.attempt.framePaths.isNotEmpty()) BlindFrames(state.attempt.framePaths, loadFrame)
    }
    if (state.writeFailed) Text(stringResource(R.string.blind_write_failed), color = MaterialTheme.colorScheme.error)
    Row(width, horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        AnswerButton(R.string.blind_done, !state.saving, Modifier.weight(1f)) { onRate(BlindAnswer.DONE) }
        AnswerButton(R.string.blind_not_done, !state.saving, Modifier.weight(1f)) { onRate(BlindAnswer.NOT_DONE) }
        OutlinedButton(
            onClick = { onRate(BlindAnswer.CANNOT_TELL) },
            enabled = !state.saving,
            shape = RoundedCornerShape(Radius.Sm),
            modifier = Modifier.weight(1f).heightIn(min = 72.dp)
        ) { Text(stringResource(R.string.blind_cannot_tell), style = MaterialTheme.typography.titleMedium) }
    }
    TextButton(onClick = onSkip, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.blind_skip))
    }
}

@Composable
private fun AnswerButton(label: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // All answers share one neutral colour: no answer looks like the "right" one.
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.Sm),
        colors = ButtonDefaults.buttonColors(),
        modifier = modifier.heightIn(min = 72.dp)
    ) { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) }
}

/** Only the frames that exist; one that fails to decode is left out silently (no placeholder, no path anywhere). */
@Composable
private fun BlindFrames(paths: List<String>, loadFrame: FrameLoader) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        paths.forEachIndexed { i, path ->
            val image by produceState<ImageBitmap?>(null, path) { value = loadFrame(path, BLIND_FRAME_PX) }
            val bmp = image ?: return@forEachIndexed
            Image(
                bmp,
                contentDescription = stringResource(R.string.detail_frame, westernDigits(i + 1)),
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(240.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(Radius.Sm))
            )
        }
    }
}

/** The replay clip from app-private storage, looping and muted; its file name is never shown. */
@Composable
private fun BlindClip(clip: File, modifier: Modifier) {
    val label = stringResource(R.string.blind_clip)
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                setOnPreparedListener { player ->
                    player.isLooping = true
                    player.setVolume(0f, 0f)
                    start()
                }
                setOnErrorListener { _, _, _ -> true }
            }
        },
        update = { view ->
            if (view.tag != clip) {
                view.tag = clip
                view.setVideoPath(clip.path)
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(Radius.Sm)).semantics { contentDescription = label }
    )
}

private const val BLIND_FRAME_PX = 640

@Preview(device = Devices.TABLET)
@Composable
private fun BlindReviewPreview() {
    DlsTheme {
        BlindReviewScreen(
            BlindReviewUiState.Content(BlindAttempt("a", "Step sentence", listOf("frames/s/a-0.bin")), 3, 12),
            {},
            {},
            {},
            { _, _ -> null }
        )
    }
}
