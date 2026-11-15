package com.aitherapist.dls.ui.theme

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection

/**
 * Calm motion for the therapist UI (Story 6.3 polish): short (200 to 300 ms), no bounce, a short slide plus fade.
 * Children get a plain crossfade only. Everything collapses to no animation when system animations are off.
 */
object Motion {
    const val SLIDE_MS = 260
    const val FADE_MS = 200
    const val CROSSFADE_MS = 240

    /** Slide distance as a fraction of the container width: a hint of direction, not a full page sweep. */
    const val SLIDE_FRACTION_DIVISOR = 12
}

/**
 * Pure rule for the sign of the horizontal offset the *entering* content starts from. Forward moves the way the
 * language reads: in LTR new content comes from the right (+), in RTL (Arabic) from the left (-). Back is mirrored.
 */
fun enterOffsetSign(forward: Boolean, rtl: Boolean): Int = (if (forward) 1 else -1) * (if (rtl) -1 else 1)

/** Pure rule: moving to a tab at a higher index is forward (towards the reading end). */
fun isForwardTab(from: Int, to: Int): Boolean = to >= from

/** True when the user turned system animations off (animator duration scale 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

@Composable
fun isRtl(): Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl

/** No animation at all (reduced motion). */
fun noMotion(): ContentTransform = ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

/** Short directional slide plus fade, RTL-aware; [forward] false plays the mirrored "back" motion. */
fun calmSlide(forward: Boolean, rtl: Boolean, reduced: Boolean): ContentTransform {
    if (reduced) return noMotion()
    val sign = enterOffsetSign(forward, rtl)
    val slide = tween<IntOffset>(Motion.SLIDE_MS, easing = FastOutSlowInEasing)
    val enter =
        slideInHorizontally(slide) { width -> sign * width / Motion.SLIDE_FRACTION_DIVISOR } +
            fadeIn(tween(Motion.FADE_MS, delayMillis = Motion.SLIDE_MS - Motion.FADE_MS, easing = LinearEasing))
    val exit =
        slideOutHorizontally(slide) { width -> -sign * width / Motion.SLIDE_FRACTION_DIVISOR } +
            fadeOut(tween(Motion.FADE_MS - 60, easing = LinearEasing))
    return enter togetherWith exit
}

/** Gentle crossfade (child mode, or where direction has no meaning). */
fun calmCrossfade(reduced: Boolean): ContentTransform {
    if (reduced) return noMotion()
    return fadeIn(tween(Motion.CROSSFADE_MS, easing = LinearEasing)) togetherWith
        fadeOut(tween(Motion.CROSSFADE_MS, easing = LinearEasing))
}
