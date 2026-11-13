package com.aitherapist.dls.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Spacing

/** Star bar pill (display only): [stars] filled out of [slots]. Star logic and effects come in Epic 4. */
@Composable
fun StarBar(stars: Int, modifier: Modifier = Modifier, slots: Int = SLOTS) {
    Row(
        modifier = modifier.background(ChildColors.Surface, CircleShape).padding(horizontal = Spacing.S3, vertical = Spacing.S1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(slots) { i ->
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (i < stars) ChildColors.Star else ChildColors.StarEmpty,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private const val SLOTS = 3
