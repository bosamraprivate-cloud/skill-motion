package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.scoring.StepLevel
import com.aitherapist.dls.ui.theme.LevelColors
import com.aitherapist.dls.ui.theme.Spacing

private const val CHIP_TINT = 0.14f

/**
 * "3 · Independent", or the word alone for not scored. A step without an automatic level (null: never tried, or
 * still open) reads "not tried / open", distinct from the Rule 1 "not scored".
 */
@Composable
internal fun levelLabel(level: StepLevel?): String {
    if (level == null) return stringResource(R.string.detail_level_open)
    val word = stringResource(levelWord(level))
    return levelNumber(level)?.let { "${westernDigits(it)} · $word" } ?: word
}

/**
 * Level chip (UX-DR13): the step's level as number + word on its level colour. A corrected step shows the automatic
 * level struck through and the corrected level in the `corrected` colour with the word "reviewed" (never colour alone).
 */
@Composable
fun LevelChip(automatic: StepLevel?, corrected: Boolean, correctedLevel: StepLevel?, modifier: Modifier = Modifier) {
    val autoLabel = levelLabel(automatic)
    if (!corrected) {
        Pill(levelColor(automatic), modifier.clearAndSetSemantics { contentDescription = autoLabel }) {
            Text(autoLabel, style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    val newLabel = levelLabel(correctedLevel)
    val reviewed = stringResource(R.string.detail_reviewed)
    val automaticWord = stringResource(R.string.detail_automatic, autoLabel)
    Row(
        modifier.clearAndSetSemantics { contentDescription = "$newLabel, $reviewed, $automaticWord" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S2)
    ) {
        Text(
            levelNumber(automatic)?.let(::westernDigits) ?: autoLabel,
            style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.LineThrough),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Pill(LevelColors.Corrected, Modifier) {
            Text(newLabel, style = MaterialTheme.typography.labelLarge, color = LevelColors.Corrected)
            Text("· $reviewed", style = MaterialTheme.typography.labelMedium, color = LevelColors.Corrected)
        }
    }
}

@Composable
private fun Pill(color: Color, modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .background(color.copy(alpha = CHIP_TINT), RoundedCornerShape(percent = 50))
            .padding(horizontal = Spacing.S3, vertical = Spacing.S1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S1)
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        content()
    }
}
