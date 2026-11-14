package com.aitherapist.dls.ui.child

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Radius
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Story 6.2: the calm time ring drawn along the step card's edge. It starts at the top centre and fills clockwise
 * (counter-clockwise in RTL, so it grows from the reading start) in sage over the step time. No digits, no sound.
 * FULL: gentle pulse in the last 3 s and a small sparkle when Done comes first. CALM (or reduced motion): static,
 * with a soft glow instead of the sparkle. Time up only leaves the ring full; never a warning colour.
 */
@Composable
fun TimeRingOverlay(ring: TimeRing, modifier: Modifier = Modifier, cornerRadius: Dp = Radius.Lg) {
    val reduced = rememberReducedMotion()
    val full = ring.level == EffectLevel.FULL && !reduced
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = remember(ring.key) { Animatable(ring.startFraction) }
    LaunchedEffect(ring) {
        progress.snapTo(ring.startFraction)
        if (ring.running && ring.remainingMs > 0) {
            progress.animateTo(1f, tween(ring.remainingMs.toInt(), easing = LinearEasing))
        }
    }
    val pulsing = full && ring.pulses(progress.value)
    val pulse by rememberInfiniteTransition(label = "ringPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "ringPulseValue"
    )
    val ended = remember(ring.key, ring.ending) { Animatable(if (ring.ending == TimeRing.Ending.DONE_EARLY) 0f else 1f) }
    LaunchedEffect(ring.key, ring.ending) {
        if (ring.ending == TimeRing.Ending.DONE_EARLY) ended.animateTo(1f, tween(END_MS))
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val strokePx = STROKE.toPx() * (if (pulsing) 1f + PULSE_GROW * pulse else 1f)
        val inset = STROKE.toPx() * (1f + PULSE_GROW) / 2f
        val path = cardPath(size, inset, cornerRadius.toPx())
        val measure = PathMeasure().apply { setPath(path, false) }
        val length = measure.length
        val drawn = Path()
        val fraction = progress.value.coerceIn(0f, 1f)
        if (fraction > 0f) measure.getSegment(0f, length * fraction, drawn, true)
        scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
            // Faint track so the ring's shape (not only its colour) shows how much time has passed.
            drawPath(path, ChildColors.StarEmpty, style = Stroke(STROKE.toPx(), cap = StrokeCap.Round))
            drawPath(drawn, ChildColors.Done, style = Stroke(strokePx, cap = StrokeCap.Round))
            if (ring.ending == TimeRing.Ending.DONE_EARLY && ended.value < 1f) {
                val fade = 1f - ended.value
                if (full) {
                    // Small sparkle at the ring's tip.
                    val tip = measure.getPosition(length * fraction)
                    val spread = SPARKLE_SPREAD.toPx() * ended.value
                    for (i in 0 until SPARKLE_DOTS) {
                        val angle = Math.toRadians(i * 360.0 / SPARKLE_DOTS)
                        val at = Offset(tip.x + spread * cos(angle).toFloat(), tip.y + spread * sin(angle).toFloat())
                        drawCircle(ChildColors.Star.copy(alpha = fade), radius = SPARKLE_DOT.toPx(), center = at)
                    }
                } else {
                    // Soft glow along the filled arc.
                    val glow = ChildColors.Done.copy(alpha = GLOW_ALPHA * fade)
                    drawPath(drawn, glow, style = Stroke(strokePx * GLOW_WIDTH, cap = StrokeCap.Round))
                }
            }
        }
    }
}

/** Rounded rectangle path inset by [inset], starting at the top centre and running clockwise. */
private fun cardPath(size: Size, inset: Float, corner: Float): Path {
    val l = inset
    val t = inset
    val r = size.width - inset
    val b = size.height - inset
    val c = min(corner, min(r - l, b - t) / 2f).coerceAtLeast(0f)
    val d = c * 2f
    return Path().apply {
        moveTo((l + r) / 2f, t)
        lineTo(r - c, t)
        arcTo(Rect(r - d, t, r, t + d), -90f, 90f, false)
        lineTo(r, b - c)
        arcTo(Rect(r - d, b - d, r, b), 0f, 90f, false)
        lineTo(l + c, b)
        arcTo(Rect(l, b - d, l + d, b), 90f, 90f, false)
        lineTo(l, t + c)
        arcTo(Rect(l, t, l + d, t + d), 180f, 90f, false)
        lineTo((l + r) / 2f, t)
    }
}

private val STROKE = 6.dp
private val SPARKLE_SPREAD = 18.dp
private val SPARKLE_DOT = 3.dp
private const val SPARKLE_DOTS = 6
private const val PULSE_MS = 700
private const val PULSE_GROW = 0.5f
private const val END_MS = 700
private const val GLOW_ALPHA = 0.35f
private const val GLOW_WIDTH = 2.5f
