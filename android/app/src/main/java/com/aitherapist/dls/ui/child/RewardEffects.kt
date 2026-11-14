package com.aitherapist.dls.ui.child

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.audio.AssetAudioPlayer
import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter

/**
 * The one step-end effect entry point (AD-11, Story 4.8), driven by runner state, never by taps. Full: the star
 * flies to the star bar with twinkles (at most 3 flashes/s, [FlashSchedule]), a Konfetti burst around the visual
 * and the chime. Calm (default, and whenever the system removes animations): the star fades in slowly, soft sound,
 * no particles. Encourage: the encouraging line shown calmly, no sound. [StepViewModel] switches the character clip
 * and bounds the duration. Takes no touches, so the buttons underneath stay usable.
 */
@Composable
fun RewardEffects(effect: StepEffect?, modifier: Modifier = Modifier, starTarget: Offset? = null, characterCentre: Offset? = null) {
    effect ?: return
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Re-read per effect and configuration change, so a changed "remove animations" setting applies at once.
    val reducedMotion = remember(effect.id, configuration) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    val level = if (reducedMotion) EffectLevel.CALM else effect.level
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = starTarget?.minus(origin)
    // The celebration happens on the side character: the star and the burst start at its centre.
    // Captured once when the effect starts, so later layout passes never restart the burst.
    val from = remember(effect.id) { characterCentre?.minus(origin) }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        when (effect.kind) {
            StepEffect.Kind.ENCOURAGE -> EncourageLine(effect.id, Modifier.align(Alignment.BottomCenter))
            StepEffect.Kind.CELEBRATE -> {
                RewardSound(effect.id, RewardSounds.forCelebration(level))
                if (level ==
                    EffectLevel.FULL
                ) {
                    FullCelebration(effect.id, target, from)
                } else {
                    CalmStar(effect.id, target, from, reducedMotion)
                }
            }
        }
    }
}

/** Plays [path] once per effect; a missing asset plays nothing (silent fallback). Stops when the effect ends. */
@Composable
private fun RewardSound(id: Int, path: String) {
    val context = LocalContext.current
    val player = remember(id) { AssetAudioPlayer(context.applicationContext) }
    DisposableEffect(id) {
        player.play(path) {}
        onDispose { player.stop() }
    }
}

@Composable
private fun FullCelebration(id: Int, target: Offset?, from: Offset?) {
    val progress = remember(id) { Animatable(0f) }
    val twinkle = remember(id) { Animatable(1f) }
    LaunchedEffect(id) {
        launch { progress.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing)) }
        var last = 0L
        for (at in FlashSchedule.times(TWINKLES, StepEffect.FULL_MS - FLIGHT_TAIL_MS)) {
            delay(at - last)
            last = at
            twinkle.snapTo(TWINKLE_SCALE)
            twinkle.animateTo(1f, tween(TWINKLE_MS, easing = LinearEasing))
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val parties = remember(id) {
        listOf(
            Party(
                speed = 0f,
                maxSpeed = CONFETTI_SPEED,
                damping = CONFETTI_DAMPING,
                spread = FULL_CIRCLE,
                colors = CONFETTI_COLORS,
                position = from?.let { Position.Absolute(it.x, it.y) }
                    ?: Position.Relative(visualX(rtl).toDouble(), VISUAL_Y.toDouble()),
                emitter = Emitter(duration = BURST_MS, TimeUnit.MILLISECONDS).max(PARTICLES)
            )
        )
    }
    KonfettiView(modifier = Modifier.fillMaxSize(), parties = parties)
    FlyingStar(progress.value, twinkle.value, alpha = 1f, target = target, from = from)
}

/**
 * Calm: the star fades in on the side character, then glides slowly to the bar. With animations removed it does not
 * travel: it shows on the character, then appears at the bar.
 */
@Composable
private fun CalmStar(id: Int, target: Offset?, from: Offset?, reducedMotion: Boolean) {
    val fade = remember(id) { Animatable(0f) }
    val travel = remember(id) { Animatable(0f) }
    LaunchedEffect(id) {
        fade.animateTo(1f, tween(CALM_FADE_MS / 2, easing = LinearEasing))
        if (reducedMotion) travel.snapTo(1f) else travel.animateTo(1f, tween(CALM_FADE_MS, easing = FastOutSlowInEasing))
    }
    FlyingStar(progress = travel.value, scale = 1f, alpha = fade.value, target = target, from = from)
}

/**
 * The star moves from the visual (progress 0) to [target], the measured star bar centre in this overlay's
 * coordinates (progress 1). Absolute coordinates: measured positions are already physical, so nothing is mirrored.
 */
@Composable
private fun FlyingStar(progress: Float, scale: Float, alpha: Float, target: Offset?, from: Offset? = null) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val half = with(LocalDensity.current) { STAR_SIZE.toPx() / 2 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val start = from ?: Offset(w * visualX(rtl), h * VISUAL_Y)
        // No measurement yet: the star bar sits in the top start corner.
        val end = target ?: Offset(if (rtl) w - half * 2 else half * 2, half * 2)
        val at = start + (end - start) * progress
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = ChildColors.Star,
            modifier = Modifier
                .align(AbsoluteAlignment.TopLeft)
                .absoluteOffset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
                .size(STAR_SIZE)
                .scale(scale)
                .alpha(alpha)
        )
    }
}

/** The step visual sits on the start side: left in LTR, right in RTL. */
private fun visualX(rtl: Boolean): Float = if (rtl) 1f - VISUAL_X else VISUAL_X

@Composable
private fun EncourageLine(id: Int, modifier: Modifier) {
    val fade = remember(id) { Animatable(0f) }
    LaunchedEffect(id) { fade.animateTo(1f, tween(ENCOURAGE_FADE_MS, easing = LinearEasing)) }
    Text(
        text = stringResource(R.string.child_encourage),
        style = ChildType.Instruction,
        color = ChildColors.Ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(bottom = ENCOURAGE_BOTTOM)
            .alpha(fade.value)
            .background(ChildColors.Surface, RoundedCornerShape(Radius.Lg))
            .padding(horizontal = Spacing.S5, vertical = Spacing.S3)
    )
}

private val STAR_SIZE = 96.dp
private val ENCOURAGE_BOTTOM = 200.dp
private const val VISUAL_X = 0.3f
private const val VISUAL_Y = 0.45f
private const val FLIGHT_MS = 1_200
private const val FLIGHT_TAIL_MS = 400L
private const val TWINKLES = 3
private const val TWINKLE_SCALE = 1.25f
private const val TWINKLE_MS = 250
private const val CALM_FADE_MS = 1_200
private const val ENCOURAGE_FADE_MS = 600
private const val BURST_MS = 300L
private const val PARTICLES = 60
private const val CONFETTI_SPEED = 18f
private const val CONFETTI_DAMPING = 0.9f
private const val FULL_CIRCLE = 360

// Calm palette only: gold, green, blue, lilac, mint (no red/orange).
private val CONFETTI_COLORS = listOf(0xE8C26A, 0x5E9C8A, 0x7FA7C9, 0xB9A6D3, 0xA9CFC2)
