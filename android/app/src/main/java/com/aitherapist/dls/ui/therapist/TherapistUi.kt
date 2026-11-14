package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.scoring.StepLevel
import com.aitherapist.dls.ui.theme.LevelColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** t-card (DESIGN.md): white surface, rounded.md, 1dp t-border, no shadow; 24dp padding, 16dp rhythm. */
@Composable
internal fun TCard(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = Spacing.S3,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(Radius.Md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** Section heading inside a card (t-title), marked as a heading for TalkBack. */
@Composable
internal fun CardHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

@Composable
internal fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Pill with a colour dot and its word: colour is never the only signal (DESIGN.md level chip). */
@Composable
internal fun ColorPill(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(color.copy(alpha = PILL_TINT), RoundedCornerShape(percent = 50))
            .padding(horizontal = Spacing.S3, vertical = Spacing.S1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S2)
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Neutral pill (no level colour), e.g. a state or a badge. */
@Composable
internal fun NeutralPill(label: String, modifier: Modifier = Modifier, background: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.background(background, RoundedCornerShape(percent = 50)).padding(horizontal = Spacing.S3, vertical = Spacing.S1)
    )
}

private const val PILL_TINT = 0.16f

/** Step level colour (therapist only; never in child mode). */
internal fun levelColor(level: StepLevel?): Color = when (level) {
    StepLevel.THREE -> LevelColors.Level3
    StepLevel.TWO -> LevelColors.Level2
    StepLevel.ONE -> LevelColors.Level1
    else -> LevelColors.NotScored
}

internal fun levelWord(level: StepLevel?): Int = when (level) {
    StepLevel.THREE -> R.string.level_three
    StepLevel.TWO -> R.string.level_two
    StepLevel.ONE -> R.string.level_one
    else -> R.string.level_not_scored
}

/** Rule 1 score of a level; null for not scored. */
internal fun levelNumber(level: StepLevel?): Int? = when (level) {
    StepLevel.THREE -> 3
    StepLevel.TWO -> 2
    StepLevel.ONE -> 1
    else -> null
}

/** Level pill: Western number + word + colour (a not-scored step has no number). */
@Composable
internal fun LevelChip(level: StepLevel?, modifier: Modifier = Modifier) {
    val number = levelNumber(level)
    val word = stringResource(levelWord(level))
    ColorPill(levelColor(level), number?.let { "${westernDigits(it)} $word" } ?: word, modifier)
}

/** Medium date in the UI language with Western digits (therapist data rule). */
@Composable
internal fun formatDate(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, westernLocale(locale)) }
    return format.format(Date(millis))
}

/** Date and short time, Western digits. */
@Composable
internal fun formatDateTime(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, westernLocale(locale)) }
    return format.format(Date(millis))
}

internal fun westernLocale(locale: Locale): Locale = Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()

private const val MS_PER_MINUTE = 60_000L
private const val MS_PER_SECOND = 1_000L

/** "12 minutes" / "45 seconds" with plurals and Western digits. */
@Composable
internal fun formatDuration(ms: Long): String {
    val minutes = (ms / MS_PER_MINUTE).toInt()
    return if (minutes >= 1) {
        pluralStringResource(R.plurals.duration_minutes, minutes, westernDigits(minutes))
    } else {
        val seconds = (ms / MS_PER_SECOND).toInt()
        pluralStringResource(R.plurals.duration_seconds, seconds, westernDigits(seconds))
    }
}

/** Neutral state chip of a history row (no level colours: a state is not a score). */
@Composable
internal fun HistoryStateChip(state: HistoryState) {
    val (label, color) =
        when (state) {
            HistoryState.DONE -> R.string.history_state_done to MaterialTheme.colorScheme.secondaryContainer
            HistoryState.PAUSED -> R.string.history_state_paused to MaterialTheme.colorScheme.surfaceVariant
            HistoryState.ABANDONED -> R.string.history_state_abandoned to MaterialTheme.colorScheme.surfaceVariant
            HistoryState.NOT_FINISHED -> R.string.history_state_not_finished to MaterialTheme.colorScheme.surfaceVariant
        }
    NeutralPill(stringResource(label), background = color)
}
