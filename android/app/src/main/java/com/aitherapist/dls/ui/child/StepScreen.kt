package com.aitherapist.dls.ui.child

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The fixed-zone child step screen (UX DESIGN Layout, RTL: start = right edge). Top band: star bar, step schedule,
 * adult corner. Main area: step visual (about 60% width, start side) with the reaction character beside it,
 * sentence under the visual. Bottom band: Done (start) and Help, Repeat (end). Positions never move.
 */
@Composable
fun StepScreen(
    state: StepUiState,
    onEvent: (StepEvent) -> Unit,
    onAdultCorner: () -> Unit,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {}
) {
    Box(modifier.fillMaxSize()) {
        StepContent(state, onEvent, onAdultCorner, onStart)
        // Adult pause: a calm dim over everything, no text. It takes no touches, so the adult corner still works.
        if (state.isPaused) {
            Box(Modifier.fillMaxSize().background(ChildColors.Ink.copy(alpha = PAUSE_DIM_ALPHA)))
        }
    }
}

@Composable
private fun StepContent(state: StepUiState, onEvent: (StepEvent) -> Unit, onAdultCorner: () -> Unit, onStart: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(ChildColors.Canvas).padding(Spacing.S5)) {
        TopBand(state, onAdultCorner)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                StepUiState.Waiting -> WaitVisual(Modifier.align(Alignment.Center))
                is StepUiState.Finished ->
                    CharacterView(clip = state.reactionClip, loop = true, modifier = Modifier.fillMaxSize(0.7f).align(Alignment.Center))
                is StepUiState.Step -> MainArea(state)
                is StepUiState.Start -> TaskStartContent(state, onStart)
            }
        }
        if (state is StepUiState.Step) BottomBand(state, onEvent)
    }
}

@Composable
private fun TopBand(state: StepUiState, onAdultCorner: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        StarBar(stars = (state as? StepUiState.Step)?.stars ?: 0)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (state is StepUiState.Step) StepSchedule(current = state.stepIndex, count = state.stepCount)
        }
        AdultCorner(onAdultCorner)
    }
}

/** Small low-contrast target; only a 2 s press opens the adult pause sheet. */
@Composable
private fun AdultCorner(onLongPress: () -> Unit) {
    val label = stringResource(R.string.child_adult_corner)
    Box(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    // Null only when the 2 s hold elapsed with the finger still down; a lift or cancel returns true.
                    val ended =
                        withTimeoutOrNull(ADULT_HOLD_MS) {
                            waitForUpOrCancellation()
                            true
                        }
                    if (ended == null) onLongPress()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(16.dp).background(ChildColors.StarEmpty, CircleShape))
    }
}

@Composable
private fun MainArea(state: StepUiState.Step) {
    val visual = state.visual
    Row(Modifier.fillMaxSize().padding(vertical = Spacing.S3), horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
        if (visual != null) {
            Column(
                Modifier.weight(if (visual.large) 1f else VISUAL_WEIGHT).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Radius.Lg)).background(ChildColors.Surface),
                    contentAlignment = Alignment.Center
                ) {
                    CharacterView(
                        clip = visual.clip,
                        loop = false,
                        still = visual.still,
                        stillMode = !visual.animate,
                        modifier = Modifier.fillMaxSize().padding(Spacing.S3)
                    )
                }
                Sentence(state.sentence)
            }
            // Level 2 demonstration without the reaction character keeps its normal size.
            if (!visual.large && state.reactionClip == null) Spacer(Modifier.weight(1f - VISUAL_WEIGHT))
        }
        state.reactionClip?.let { clip ->
            Column(Modifier.weight(1f - if (visual != null) VISUAL_WEIGHT else 0f).fillMaxHeight()) {
                CharacterView(clip = clip, loop = true, modifier = Modifier.weight(1f).fillMaxWidth())
                if (visual == null) Sentence(state.sentence)
            }
        }
        if (visual == null && state.reactionClip == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Sentence(state.sentence) }
        }
    }
}

@Composable
private fun Sentence(text: String) {
    Text(
        text = text,
        style = ChildType.Instruction,
        color = ChildColors.Ink,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.S3)
    )
}

@Composable
private fun BottomBand(state: StepUiState.Step, onEvent: (StepEvent) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ChildButton(
            label = stringResource(R.string.child_done),
            icon = Icons.Filled.Check,
            color = ChildColors.Done,
            minWidth = 220.dp,
            minHeight = 160.dp,
            enabled = state.doneEnabled,
            onClick = { onEvent(StepEvent.Done) }
        )
        Spacer(Modifier.weight(1f))
        ChildButton(
            label = stringResource(R.string.child_help),
            icon = Icons.Filled.Info,
            color = ChildColors.Help,
            minWidth = 120.dp,
            minHeight = 120.dp,
            onClick = { onEvent(StepEvent.Help) }
        )
        Spacer(Modifier.width(Spacing.S4))
        ChildButton(
            label = stringResource(R.string.child_repeat),
            icon = Icons.Filled.Refresh,
            color = ChildColors.Repeat,
            minWidth = 120.dp,
            minHeight = 120.dp,
            onClick = { onEvent(StepEvent.Repeat) }
        )
    }
}

/** Child button: icon + label; a disabled Done stays visible, only dimmed (never hidden). */
@Composable
private fun ChildButton(
    label: String,
    icon: ImageVector,
    color: Color,
    minWidth: Dp,
    minHeight: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(Radius.Lg),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = ChildColors.Surface),
        modifier = Modifier.sizeIn(minWidth = minWidth, minHeight = minHeight).alpha(if (enabled) 1f else DIMMED_ALPHA)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Text(label, style = ChildType.Button)
        }
    }
}

/** Calm "wait" visual: a slowly breathing circle, no text, no countdown. */
@Composable
private fun WaitVisual(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wait")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WAIT_BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    Box(modifier.size(160.dp).scale(scale).background(ChildColors.ScheduleDone, CircleShape))
}

private const val VISUAL_WEIGHT = 0.6f
private const val DIMMED_ALPHA = 0.4f
private const val PAUSE_DIM_ALPHA = 0.45f
private const val ADULT_HOLD_MS = 2_000L
private const val WAIT_BREATH_MS = 1_800

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun StepScreenPreview() {
    DlsTheme {
        StepScreen(
            state =
            StepUiState.Step(
                stepIndex = 2,
                stepCount = 12,
                sentence = "Sentence",
                visual = null,
                reactionClip = null,
                doneEnabled = false,
                promptLevel = 1
            ),
            onEvent = {},
            onAdultCorner = {}
        )
    }
}
