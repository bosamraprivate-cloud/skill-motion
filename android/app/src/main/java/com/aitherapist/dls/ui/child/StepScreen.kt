package com.aitherapist.dls.ui.child

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
 * adult corner. Below it the side character (start side, always visible, guiding) and the main area (step visual,
 * sentence under it). Bottom band: Done (start) and Help, Repeat (end). Positions never move.
 */
@Composable
fun StepScreen(
    state: StepUiState,
    onEvent: (StepEvent) -> Unit,
    onAdultCorner: () -> Unit,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    onStarBarPositioned: (Offset) -> Unit = {},
    onCharacterCentre: (Offset) -> Unit = {}
) {
    Box(modifier.fillMaxSize()) {
        StepContent(state, onEvent, onAdultCorner, onStart, onStarBarPositioned, onCharacterCentre)
        // Adult pause: a calm dim over everything, no text. It takes no touches, so the adult corner still works.
        if (state.isPaused) {
            Box(Modifier.fillMaxSize().background(ChildColors.Ink.copy(alpha = PAUSE_DIM_ALPHA)))
        }
    }
}

@Composable
private fun StepContent(
    state: StepUiState,
    onEvent: (StepEvent) -> Unit,
    onAdultCorner: () -> Unit,
    onStart: () -> Unit,
    onStarBarPositioned: (Offset) -> Unit,
    onCharacterCentre: (Offset) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(ChildColors.Canvas).padding(Spacing.S5)) {
        TopBand(state, onAdultCorner, onStarBarPositioned)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                StepUiState.Waiting -> WaitVisual(Modifier.align(Alignment.Center))
                is StepUiState.Finished ->
                    SideCharacter(
                        clip = state.reactionClip,
                        modifier = Modifier.fillMaxSize(FINISHED_SIZE).align(Alignment.Center).onGloballyPositioned {
                            onCharacterCentre(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
                        }
                    )
                is StepUiState.Step -> StepBody(state, onCharacterCentre)
                is StepUiState.Start -> TaskStartContent(state, onStart, onCharacterCentre = onCharacterCentre)
            }
        }
        if (state is StepUiState.Step) BottomBand(state, onEvent)
    }
}

@Composable
private fun TopBand(state: StepUiState, onAdultCorner: () -> Unit, onStarBarPositioned: (Offset) -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        // Equal-width side zones keep the schedule centred: star bar at the start, adult corner at the end.
        Box(Modifier.weight(1f).widthIn(min = ADULT_CORNER_SIZE), contentAlignment = Alignment.CenterStart) {
            if (state is StepUiState.Step) {
                // Story 4.8: the reward star flies to the measured star bar centre (root coordinates).
                StarBar(
                    state.stars,
                    Modifier.onGloballyPositioned {
                        onStarBarPositioned(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
                    }
                )
            }
        }
        Box(contentAlignment = Alignment.Center) {
            if (state is StepUiState.Step) StepSchedule(current = state.stepIndex, count = state.stepCount)
        }
        Box(Modifier.weight(1f).widthIn(min = ADULT_CORNER_SIZE), contentAlignment = Alignment.CenterEnd) { AdultCorner(onAdultCorner) }
    }
}

/** Small low-contrast target; only a 2 s press opens the adult pause sheet. */
@Composable
private fun AdultCorner(onLongPress: () -> Unit) {
    val label = stringResource(R.string.child_adult_corner)
    Box(
        Modifier
            .size(ADULT_CORNER_SIZE)
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

/**
 * Two zones: the side character (always visible, guiding) and the main area. Level 1 shows the step picture; from
 * level 2 the demonstration clip slides and fades in from the character's side while it points, and the card grows
 * (about 15% at level 2, full height at level 3). Reduced motion switches instantly.
 */
@Composable
private fun StepBody(state: StepUiState.Step, onCharacterCentre: (Offset) -> Unit) {
    SideCharacterLayout(
        clip = state.reactionClip,
        modifier = Modifier.padding(vertical = Spacing.S3),
        portraitClip = state.portraitClip,
        snapKey = state.stepIndex to state.promptLevel,
        onCharacterCentre = onCharacterCentre
    ) { mainModifier -> MainArea(state, mainModifier) }
}

@Composable
private fun MainArea(state: StepUiState.Step, modifier: Modifier) {
    val visual = state.visual
    val reduced = rememberReducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val demo = state.promptLevel >= 2
    val fraction by animateFloatAsState(
        targetValue = when {
            visual?.large == true -> 1f
            demo -> LEVEL2_HEIGHT
            else -> LEVEL1_HEIGHT
        },
        animationSpec = if (reduced) snap() else tween(GROW_MS, easing = FastOutSlowInEasing),
        label = "visualSize"
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = state,
            // Only a new step or the switch to the demonstration animates; other changes update in place.
            contentKey = { it.stepIndex to (it.promptLevel >= 2) },
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    // Enters from the character's side (start), as if the character hands it over.
                    val slide = slideInHorizontally(tween(ENTER_MS, easing = FastOutSlowInEasing)) { w ->
                        if (rtl) w / SLIDE_PART else -w / SLIDE_PART
                    }
                    (fadeIn(tween(ENTER_MS)) + slide) togetherWith fadeOut(tween(EXIT_MS))
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "mainArea"
        ) { shown ->
            val shownVisual = shown.visual
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (shownVisual != null) {
                    CharacterView(
                        clip = shownVisual.clip,
                        loop = false,
                        still = shownVisual.still,
                        stillMode = !shownVisual.animate,
                        // The card takes the clip's portrait aspect, so the clip fills it edge to edge (no band).
                        modifier = Modifier
                            .fillMaxHeight(fraction)
                            .aspectRatio(CLIP_ASPECT, matchHeightConstraintsFirst = true)
                            .shadow(CARD_ELEVATION, RoundedCornerShape(Radius.Lg))
                            .clip(RoundedCornerShape(Radius.Lg))
                            .background(ChildColors.Surface)
                    )
                } else {
                    Sentence(shown.sentence)
                }
            }
        }
        if (visual != null) Sentence(state.sentence)
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
            icon = ImageVector.vectorResource(R.drawable.ic_helping_hand),
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

private const val LEVEL1_HEIGHT = 0.82f
private const val LEVEL2_HEIGHT = 0.94f
private const val FINISHED_SIZE = 0.7f
private const val GROW_MS = 500
private const val ENTER_MS = 500
private const val EXIT_MS = 250
private const val SLIDE_PART = 6
private val CARD_ELEVATION = 2.dp
private val ADULT_CORNER_SIZE = 48.dp
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
                reactionClip = "character/boy/react/idle.webp",
                doneEnabled = false,
                promptLevel = 1
            ),
            onEvent = {},
            onAdultCorner = {}
        )
    }
}
