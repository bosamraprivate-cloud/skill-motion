package com.aitherapist.dls.ui.child

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The new-sticker moment on the task reward: the sticker grows into the centre with a soft glow and "New sticker!",
 * then shrinks away to the reward card. Full: a spring pop with one gentle turn and a slow glow pulse; calm: a slow
 * fade and scale only. Any tap ends it early; it never blocks the reward.
 */
@Composable
fun StickerUnlockOverlay(stickerId: String, full: Boolean, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val scale = remember { Animatable(0f) }
    val turn = remember { Animatable(if (full) -180f else 0f) }
    val veil = remember { Animatable(0f) }
    LaunchedEffect(stickerId) {
        launch { veil.animateTo(1f, tween(if (full) 300 else 800)) }
        launch { if (full) turn.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
        if (full) {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        } else {
            scale.animateTo(1f, tween(1_000, easing = LinearEasing))
        }
        delay(HOLD_MS)
        launch { veil.animateTo(0f, tween(EXIT_MS)) }
        scale.animateTo(0f, tween(EXIT_MS, easing = FastOutSlowInEasing))
        done()
    }
    val glow =
        if (full) {
            rememberInfiniteTransition(label = "glow")
                .animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glowScale").value
        } else {
            1f
        }
    val label = stringResource(R.string.reward_sticker_new)
    Box(
        Modifier
            .fillMaxSize()
            .background(ChildColors.Ink.copy(alpha = 0.45f * veil.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S5)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(380.dp)
                        .graphicsLayer {
                            scaleX = scale.value * glow
                            scaleY = scale.value * glow
                        }.alpha(0.55f)
                        .background(ChildColors.Star.copy(alpha = 0.5f), CircleShape)
                )
                StickerArt.Sticker(
                    stickerId,
                    earned = true,
                    modifier =
                    Modifier.size(300.dp).graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        rotationZ = turn.value
                    }
                )
            }
            Text(
                label,
                style = ChildType.Count,
                color = ChildColors.Surface,
                modifier = Modifier.alpha(scale.value.coerceIn(0f, 1f))
            )
        }
    }
}

private const val HOLD_MS = 2_200L
private const val EXIT_MS = 500
