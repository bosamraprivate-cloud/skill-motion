package com.aitherapist.dls.ui.child

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/**
 * Task start (UX "Task start"): the side character pointing toward the task picture (first step's still), the task
 * name, and one big Start. Nothing else to press; the adult corner stays in the top band.
 */
@Composable
fun TaskStartContent(
    state: StepUiState.Start,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    onCharacterCentre: (Offset) -> Unit = {}
) {
    SideCharacterLayout(
        clip = state.reactionClip,
        modifier = modifier.padding(vertical = Spacing.S3),
        portraitClip = state.portraitClip,
        onCharacterCentre = onCharacterCentre
    ) { mainModifier ->
        Column(mainModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                state.picture?.let { picture ->
                    CharacterView(
                        clip = null,
                        loop = false,
                        still = picture,
                        stillMode = true,
                        modifier = Modifier.fillMaxHeight().aspectRatio(CLIP_ASPECT, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(Radius.Lg))
                    )
                }
            }
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
