package com.aitherapist.dls.ui.child

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.aitherapist.dls.ui.theme.Spacing

/** True when the system removed animations (animator duration scale 0): every switch is then instant. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/**
 * The child's guide character (EXPERIENCE "Side character"): always on screen, looping its reaction clip, with a soft
 * cross-fade whenever the state (talk, idle, point, celebrateSmall, encourage) changes. The clips are drawn facing
 * the viewer's right; [mirrored] flips them so the character faces (and points toward) the main area.
 */
@Composable
fun SideCharacter(clip: String?, modifier: Modifier = Modifier, mirrored: Boolean = false, snapKey: Any? = null) {
    val reduced = rememberReducedMotion()
    // A change of [snapKey] (the prompt level) switches the clip at once, so only one character decoder runs while
    // the main area's transition plays (memory on low-end tablets).
    var lastKey by remember { mutableStateOf(snapKey) }
    val snapNow = snapKey != lastKey
    SideEffect { lastKey = snapKey }
    Crossfade(
        targetState = clip,
        animationSpec = if (reduced || snapNow) snap() else tween(CHARACTER_FADE_MS, easing = FastOutSlowInEasing),
        modifier = modifier,
        label = "sideCharacter"
    ) { current ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            CharacterView(
                clip = current,
                loop = true,
                modifier = Modifier.fillMaxSize().graphicsLayer { if (mirrored) scaleX = -1f }
            )
        }
    }
}

/**
 * Two fixed zones under the top band: the side character on the start side (right in RTL) and the main area beside
 * it. Landscape puts them side by side; a tall (phone portrait) area stacks the character above the main area at a
 * smaller size, so nothing ever covers the bottom band. [onCharacterCentre] reports the character's centre in root
 * coordinates (the Story 4.8 effects start there).
 */
@Composable
fun SideCharacterLayout(
    clip: String?,
    modifier: Modifier = Modifier,
    portraitClip: String? = clip,
    snapKey: Any? = null,
    onCharacterCentre: (Offset) -> Unit = {},
    main: @Composable (Modifier) -> Unit
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth >= maxHeight
        val measured =
            Modifier.onGloballyPositioned {
                onCharacterCentre(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
            }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
                // The art faces right: in RTL the main area is on the left, so the character is mirrored to face it.
                SideCharacter(clip, measured.weight(CHARACTER_WEIGHT).fillMaxHeight(), mirrored = rtl, snapKey = snapKey)
                main(Modifier.weight(1f - CHARACTER_WEIGHT).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                // Above the main area `point` would aim sideways, so portrait uses talk/idle ([portraitClip]); the
                // demonstration sits directly under the character.
                SideCharacter(portraitClip, measured.weight(PORTRAIT_CHARACTER_WEIGHT).fillMaxWidth(), snapKey = snapKey)
                main(Modifier.weight(1f - PORTRAIT_CHARACTER_WEIGHT).fillMaxWidth())
            }
        }
    }
}

private const val CHARACTER_FADE_MS = 450
private const val CHARACTER_WEIGHT = 0.3f
private const val PORTRAIT_CHARACTER_WEIGHT = 0.3f
