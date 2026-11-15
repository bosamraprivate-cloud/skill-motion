package com.aitherapist.dls.ui.child

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/**
 * Task start (UX "Task start"): the child's guide character large and centred, the task name, and one big Start.
 * The character (not the first step's picture) is kept: the picture was the same character again and showed only
 * step 1, while the guide is who the child follows on every step (level 1 starts with it as the hero), so the start
 * screen hands straight into the first step without a jump. The adult corner stays in the top band.
 */
@Composable
fun TaskStartContent(
    state: StepUiState.Start,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    onCharacterCentre: (Offset) -> Unit = {}
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxSize().padding(vertical = Spacing.S3), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            SideCharacter(
                clip = state.reactionClip,
                mirrored = rtl,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(START_CHARACTER_ASPECT, matchHeightConstraintsFirst = true)
                    .onGloballyPositioned {
                        onCharacterCentre(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
                    }
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // No name text when the catalogue has none (never the raw task id).
            state.taskName?.let { name ->
                Text(
                    text = name,
                    style = ChildType.Title,
                    color = ChildColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S3)
                )
            }
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(Radius.Lg),
                colors = ButtonDefaults.buttonColors(containerColor = ChildColors.Done, contentColor = ChildColors.Surface),
                modifier = Modifier.sizeIn(minWidth = 320.dp, minHeight = 160.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.child_start), style = ChildType.Button)
                }
            }
        }
    }
}

/** Width / height of the start screen character. */
private const val START_CHARACTER_ASPECT = 0.8f
