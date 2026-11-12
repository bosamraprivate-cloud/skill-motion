package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.catalog.CharacterVariant
import com.aitherapist.dls.catalog.ReactionClip
import com.aitherapist.dls.catalog.StepClip
import com.aitherapist.dls.catalog.StepClipTask
import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.ui.child.CharacterView
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme

/** Stateful entry: wires the ViewModel to the stateless screen. */
@Composable
fun CharacterPreviewRoute(viewModel: CharacterPreviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CharacterPreviewScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun CharacterPreviewScreen(state: CharacterPreviewUiState, onEvent: (CharacterPreviewEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.character_preview_title), style = MaterialTheme.typography.headlineMedium)
            when (state) {
                CharacterPreviewUiState.Loading -> Skeleton(rows = 3)
                CharacterPreviewUiState.Empty ->
                    EmptyState(
                        icon = Icons.Filled.Info,
                        message = stringResource(R.string.character_preview_empty),
                        primaryActionLabel = stringResource(R.string.action_retry),
                        onPrimaryAction = { onEvent(CharacterPreviewEvent.Retry) }
                    )
                is CharacterPreviewUiState.Error ->
                    ErrorState(
                        message = stringResource(R.string.character_preview_error, state.reason),
                        onRetry = { onEvent(CharacterPreviewEvent.Retry) }
                    )
                is CharacterPreviewUiState.Content -> Content(state, onEvent)
            }
        }
    }
}

@Composable
private fun Content(state: CharacterPreviewUiState.Content, onEvent: (CharacterPreviewEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background)
        ) {
            val selection = state.selection
            when {
                selection == null -> Unit
                state.clipFailed ->
                    ErrorState(
                        message = stringResource(R.string.character_preview_clip_missing),
                        onRetry = { onEvent(reselect(selection)) }
                    )
                // key(): a new selection restarts playback from the first frame. One moving clip at a time.
                else -> key(selection) { Clip(selection, state.effectOff, onEvent) }
            }
        }
        Controls(state, onEvent, Modifier.width(CONTROLS_WIDTH.dp).fillMaxHeight())
    }
}

private fun reselect(selection: ClipSelection): CharacterPreviewEvent = when (selection) {
    is ClipSelection.Reaction -> CharacterPreviewEvent.SelectReaction(selection.clip.state)
    is ClipSelection.Step -> CharacterPreviewEvent.SelectStep(selection.clip.id)
}

@Composable
private fun Clip(selection: ClipSelection, effectOff: Boolean, onEvent: (CharacterPreviewEvent) -> Unit) {
    val effect = if (effectOff) EffectLevel.OFF else EffectLevel.CALM
    val onError = { onEvent(CharacterPreviewEvent.ClipFailed) }
    when (selection) {
        is ClipSelection.Reaction ->
            CharacterView(
                clip = selection.clip.file,
                loop = true,
                effectLevel = effect,
                modifier = Modifier.fillMaxSize(),
                onError = onError
            )
        is ClipSelection.Step ->
            CharacterView(
                clip = selection.clip.clip,
                still = selection.clip.still,
                loop = false,
                effectLevel = effect,
                modifier = Modifier.fillMaxSize(),
                onError = onError
            )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(state: CharacterPreviewUiState.Content, onEvent: (CharacterPreviewEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.variants.forEach { id ->
                FilterChip(
                    selected = id == state.variant.id,
                    onClick = { onEvent(CharacterPreviewEvent.SelectVariant(id)) },
                    label = { Text(id) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = state.effectOff, onCheckedChange = { onEvent(CharacterPreviewEvent.SetEffectOff(it)) })
            Text(stringResource(R.string.character_preview_effect_off))
        }
        Text(stringResource(R.string.character_preview_reactions), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.variant.reactions.forEach { r ->
                FilterChip(
                    selected = (state.selection as? ClipSelection.Reaction)?.clip == r,
                    onClick = { onEvent(CharacterPreviewEvent.SelectReaction(r.state)) },
                    label = { Text(r.state) }
                )
            }
        }
        if (state.variant.tasks.isNotEmpty()) {
            Text(stringResource(R.string.character_preview_steps), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.variant.tasks.forEach { t ->
                    FilterChip(
                        selected = t.id == state.taskId,
                        onClick = { onEvent(CharacterPreviewEvent.SelectTask(t.id)) },
                        label = { Text(t.id) }
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.steps.forEach { s ->
                    FilterChip(
                        selected = (state.selection as? ClipSelection.Step)?.clip == s,
                        onClick = { onEvent(CharacterPreviewEvent.SelectStep(s.id)) },
                        label = {
                            Text(if (s.clip == null) stringResource(R.string.character_preview_still_step, s.id) else s.id)
                        }
                    )
                }
            }
        }
    }
}

private const val CONTROLS_WIDTH = 420

private val previewVariant =
    CharacterVariant(
        id = "boy",
        reactions = listOf(ReactionClip("idle", "character/boy/react/idle.webp")),
        tasks = listOf(StepClipTask("task.preview", listOf(StepClip("step.preview.01", null, "s.still.webp"))))
    )

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun CharacterPreviewContentPreview() {
    DlsTheme {
        CharacterPreviewScreen(
            CharacterPreviewUiState.Content(listOf("boy", "girl"), previewVariant, "task.preview", null),
            {}
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun CharacterPreviewLoadingPreview() {
    DlsTheme { CharacterPreviewScreen(CharacterPreviewUiState.Loading, {}) }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun CharacterPreviewErrorPreview() {
    DlsTheme { CharacterPreviewScreen(CharacterPreviewUiState.Error("IOException"), {}) }
}
