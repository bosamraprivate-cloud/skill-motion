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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.aitherapist.dls.rewards.Stickers
import com.aitherapist.dls.ui.theme.ChildColors

/**
 * The one sticker lookup (Story 4.9). Earned stickers show the generated art from `assets/stickers/`; an id without
 * art falls back to a calm vector badge (Material icon in the child palette).
 * Locked stickers show the art dimmed and grey (no lock icon).
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
    private val LOCKED_FILTER = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    private const val LOCKED_ALPHA = 0.35f
    private val tints = listOf(ChildColors.Done, ChildColors.Help, ChildColors.Repeat)

    /** Icon and tint of [stickerId]; placeholders cycle by index; an unknown id falls back to the first badge. */
    fun badge(stickerId: String): Pair<ImageVector, Color> {
        val index = Stickers.indexOf(stickerId) ?: 0
        return icons[index % icons.size] to tints[(index / icons.size) % tints.size]
    }

    /** Asset path of [stickerId]'s art (`assets/stickers/sticker.NN.webp`, exported by `tools/art/export_stickers.py`). */
    fun assetUri(stickerId: String): String = "file:///android_asset/stickers/$stickerId.webp"

    @Composable
    fun Sticker(stickerId: String, earned: Boolean, modifier: Modifier = Modifier) {
        var failed by remember(stickerId) { mutableStateOf(false) }
        if (failed) {
            if (earned) Badge(stickerId, modifier) else Box(modifier.border(BorderStroke(3.dp, ChildColors.StarEmpty), CircleShape))
            return
        }
        AsyncImage(
            model = assetUri(stickerId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { if (it is AsyncImagePainter.State.Error) failed = true },
            // Not earned yet: the art stays visible but dimmed and grey (no lock icon), so the child sees what is coming.
            colorFilter = if (earned) null else LOCKED_FILTER,
            alpha = if (earned) 1f else LOCKED_ALPHA,
            modifier = modifier
        )
    }

    /** Fallback when a sticker id has no art (an id beyond the exported set). */
    @Composable
    private fun Badge(stickerId: String, modifier: Modifier) {
        val (icon, tint) = badge(stickerId)
        Box(
            modifier.background(ChildColors.Surface, CircleShape).border(BorderStroke(6.dp, tint), CircleShape).padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())
        }
    }
}
