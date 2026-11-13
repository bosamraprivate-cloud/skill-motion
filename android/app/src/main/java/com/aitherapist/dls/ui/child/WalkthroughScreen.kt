package com.aitherapist.dls.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/**
 * Closing character walkthrough (UX "Character walkthrough"): one step at a time, large, the demonstration clip
 * playing once (or its still, or the reaction character when the step has no media), with the sentence under it.
 * No buttons; only the adult corner in the top band.
 */
@Composable
fun WalkthroughContent(state: StepUiState.Walkthrough, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(vertical = Spacing.S3), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Radius.Lg)).background(ChildColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            // A new key per step restarts the one-shot clip for every step.
            key(state.stepIndex) {
                val visual = state.visual
                if (visual != null) {
                    CharacterView(
                        clip = visual.clip,
                        loop = false,
                        still = visual.still,
                        stillMode = !visual.animate,
                        modifier = Modifier.fillMaxSize().padding(Spacing.S3)
                    )
                } else {
                    CharacterView(clip = state.reactionClip, loop = true, modifier = Modifier.fillMaxSize(0.8f))
                }
            }
        }
        Text(
            text = state.sentence,
            style = ChildType.Instruction,
            color = ChildColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.S3, bottom = 8.dp)
        )
    }
}
