package com.aitherapist.dls.ui.child

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/**
 * The step's main area, staged per prompt level (EXPERIENCE "Prompt staging"):
 * - [StepStage.CHARACTER_HERO] (level 1, time up, step-end reaction): the side character large and centred in a soft
 *   frame, the time ring around that frame; no step card.
 * - [StepStage.HANDOFF] (entering level 2-3): the character moves toward the start side while pointing across to
 *   where the card appears, then shrinks and fades; the card fades in on its still in the centre.
 * - [StepStage.CLIP]: the demonstration card large (larger at level 3), looping its clip, the ring along its edge.
 * Calm effect level or system reduced motion: crossfades only, nothing moves or scales. Works the same in portrait
 * and landscape (both layers size by the area's height, capped by its width).
 */
@Composable
internal fun StepStageArea(state: StepUiState.Step, modifier: Modifier = Modifier, onCharacterCentre: (Offset) -> Unit = {}) {
    val noMovement = state.calm || rememberReducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val visual = state.visual
    // No card media (dev catalogue): the character stays the hero at every level.
    val stage = if (visual == null) StepStage.CHARACTER_HERO else state.stage
    val transition = updateTransition(stage, label = "stepStage")
    val heroAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState == StepStage.HANDOFF) tween(FADE_MS, delayMillis = POINT_HOLD_MS) else tween(FADE_MS)
        },
        label = "heroAlpha"
    ) { if (it == StepStage.CHARACTER_HERO) 1f else 0f }
    val heroScale by transition.animateFloat(
        transitionSpec = {
            if (targetState == StepStage.HANDOFF) tween(FADE_MS, delayMillis = POINT_HOLD_MS) else tween(FADE_MS)
        },
        label = "heroScale"
    ) { if (it == StepStage.CHARACTER_HERO || noMovement) 1f else HERO_SHRUNK }
    val heroShift by transition.animateFloat(
        transitionSpec = { tween(MOVE_MS, easing = FastOutSlowInEasing) },
        label = "heroShift"
    ) { if (it == StepStage.CHARACTER_HERO || noMovement) 0f else 1f }
    val cardAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState == StepStage.HANDOFF) tween(FADE_MS, delayMillis = POINT_HOLD_MS) else tween(FADE_MS)
        },
        label = "cardAlpha"
    ) { if (it == StepStage.CHARACTER_HERO) 0f else 1f }
    val cardScale by transition.animateFloat(
        transitionSpec = { tween(FADE_MS + POINT_HOLD_MS, easing = FastOutSlowInEasing) },
        label = "cardScale"
    ) { if (it == StepStage.CHARACTER_HERO && !noMovement) CARD_ENTER_SCALE else 1f }
    val cardFraction by animateFloatAsState(
        if (visual?.large == true) 1f else LEVEL2_HEIGHT,
        animationSpec = tween(FADE_MS, easing = FastOutSlowInEasing),
        label = "cardFraction"
    )

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shiftPx = with(LocalDensity.current) { (maxWidth * SHIFT_PART).toPx() }
        // Start side: right in RTL, left in LTR (graphicsLayer translation is not mirrored by the layout direction).
        val towardStart = if (rtl) shiftPx else -shiftPx

        if (visual != null && (stage != StepStage.CHARACTER_HERO || cardAlpha > 0f)) {
            Box(
                Modifier
                    .fillMaxHeight(cardFraction)
                    .aspectRatio(CLIP_ASPECT, matchHeightConstraintsFirst = true)
                    .graphicsLayer {
                        alpha = cardAlpha
                        scaleX = cardScale
                        scaleY = cardScale
                    }
            ) {
                val cardModifier =
                    Modifier
                        .fillMaxSize()
                        .shadow(CARD_ELEVATION, RoundedCornerShape(Radius.Lg))
                        .clip(RoundedCornerShape(Radius.Lg))
                        .background(ChildColors.Surface)
                // The still stays underneath, so the clip starting never flashes an empty card.
                CharacterView(clip = null, loop = false, still = visual.still, stillMode = true, modifier = cardModifier)
                if (visual.animate && visual.clip != null) {
                    CharacterView(clip = visual.clip, loop = true, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.Lg)))
                }
                val ring = state.ring
                if (ring != null && stage != StepStage.CHARACTER_HERO) TimeRingOverlay(ring, Modifier.matchParentSize())
            }
        }

        if (stage != StepStage.CLIP || heroAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxHeight(HERO_HEIGHT)
                    .aspectRatio(HERO_ASPECT, matchHeightConstraintsFirst = true)
                    .onGloballyPositioned {
                        onCharacterCentre(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = heroAlpha
                            scaleX = heroScale
                            scaleY = heroScale
                            translationX = towardStart * heroShift
                        }
                ) {
                    // A soft frame gives the ring an edge to run along; it moves and fades with the character.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(Radius.Lg))
                            .background(ChildColors.Surface)
                    )
                    // The art faces right; in RTL it is mirrored so at the start side it points across to the card.
                    SideCharacter(
                        clip = state.reactionClip,
                        modifier = Modifier.fillMaxSize().padding(Spacing.S3),
                        mirrored = rtl,
                        snapKey = state.stepIndex to stage
                    )
                    val ring = state.ring
                    if (ring != null && stage == StepStage.CHARACTER_HERO) TimeRingOverlay(ring, Modifier.matchParentSize())
                }
            }
        }
    }
}

/** Card height at level 2; level 3 (and a missing clip's still) fills the area. */
private const val LEVEL2_HEIGHT = 0.88f
private const val HERO_HEIGHT = 0.96f

/** Width / height of the hero frame around the side character. */
private const val HERO_ASPECT = 0.8f
private const val HERO_SHRUNK = 0.55f
private const val CARD_ENTER_SCALE = 0.92f
private const val SHIFT_PART = 0.3f

/** The handoff (VM [StepViewModel.HANDOFF_MS] = 1 200 ms): point ~600 ms, then fade/shrink ~600 ms. */
private const val POINT_HOLD_MS = 600
private const val FADE_MS = 600
private const val MOVE_MS = 450
private val CARD_ELEVATION = 2.dp
