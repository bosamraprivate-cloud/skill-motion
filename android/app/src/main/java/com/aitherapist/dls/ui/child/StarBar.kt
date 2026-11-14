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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.Spacing

/**
 * Star bar pill (Story 4.7): the stars earned in the current session, as one gold star and the count in the
 * locale's digits. Calm colours only (no red/orange); the Row follows the layout direction (RTL in Arabic).
 * Effects (star flight, sounds) come in Story 4.8.
 */
@Composable
fun StarBar(stars: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(ChildColors.Surface, CircleShape).padding(horizontal = Spacing.S3, vertical = Spacing.S1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = if (stars > 0) ChildColors.Star else ChildColors.StarEmpty,
            modifier = Modifier.size(32.dp)
        )
        Text(childNumber(stars.coerceAtLeast(0)), style = ChildType.Button, color = ChildColors.Ink)
    }
}
