package com.aitherapist.dls.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.ui.theme.isPortrait

/**
 * Two (or more) panels side by side in landscape and stacked, centred, in portrait (Story 6.3). Children must not use
 * weights, since the scope differs between modes.
 */
@Composable
fun SideBySideOrStacked(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (isPortrait()) {
        Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.S6, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { content() }
    }
}
