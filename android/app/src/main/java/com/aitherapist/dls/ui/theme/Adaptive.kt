package com.aitherapist.dls.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** How the current window is arranged. Tokens never change between modes; only arrangement does. */
enum class LayoutMode { Landscape, Portrait }

/** Pure rule: a window taller than it is wide is portrait; square counts as landscape. */
fun layoutModeFor(widthDp: Int, heightDp: Int): LayoutMode = if (heightDp > widthDp) LayoutMode.Portrait else LayoutMode.Landscape

/** The single shared orientation check for every screen (Story 6.3). */
@Composable
fun layoutMode(): LayoutMode {
    val config = LocalConfiguration.current
    return layoutModeFor(config.screenWidthDp, config.screenHeightDp)
}

/** True when the window is taller than wide. */
@Composable
fun isPortrait(): Boolean = layoutMode() == LayoutMode.Portrait
