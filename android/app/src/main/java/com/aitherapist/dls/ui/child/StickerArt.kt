package com.aitherapist.dls.ui.child

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.rewards.Stickers
import com.aitherapist.dls.ui.theme.ChildColors

/**
 * The one sticker lookup (Story 4.9). Sticker art does not exist yet (Story 2.6 deferred), so every id is a calm
 * vector badge (Material icon in the child palette, no red/orange). Real art replaces [Sticker] here only.
 * Locked stickers are soft outlines (no lock icon).
 */
object StickerArt {
    private val icons: List<ImageVector> =
        listOf(
            Icons.Filled.Star,
            Icons.Filled.Favorite,
            Icons.Filled.Face,
            Icons.Filled.Home,
            Icons.Filled.ThumbUp,
            Icons.Filled.Place,
            Icons.Filled.Build,
            Icons.Filled.Notifications,
            Icons.Filled.CheckCircle,
            Icons.Filled.ShoppingCart
        )
    private val tints = listOf(ChildColors.Done, ChildColors.Help, ChildColors.Repeat)

    /** Icon and tint of [stickerId]; placeholders cycle by index; an unknown id falls back to the first badge. */
    fun badge(stickerId: String): Pair<ImageVector, Color> {
        val index = Stickers.indexOf(stickerId) ?: 0
        return icons[index % icons.size] to tints[(index / icons.size) % tints.size]
    }

    @Composable
    fun Sticker(stickerId: String, earned: Boolean, modifier: Modifier = Modifier) {
        val (icon, tint) = badge(stickerId)
        val look =
            if (earned) {
                Modifier.background(ChildColors.Surface, CircleShape).border(BorderStroke(6.dp, tint), CircleShape)
            } else {
                Modifier.border(BorderStroke(3.dp, ChildColors.StarEmpty), CircleShape)
            }
        Box(modifier.then(look).padding(18.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (earned) tint else ChildColors.StarEmpty, modifier = Modifier.fillMaxSize())
        }
    }
}
