package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.scoring.StepLevel
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.LevelColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/**
 * Session summary sheet (therapist): after a child-mode visit returns through the PIN ([sessionIds] = that visit's
 * sessions; a jacket chain gives two sections), or from a Sessions row. Read-only overview; corrections are made in the Session detail (Story 5.2) and show here on the next load.
 */
@Composable
fun SessionSummaryDialog(
    childId: String,
    sessionIds: List<String>,
    onOpenReport: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: SessionSummaryViewModel = hiltViewModel(key = "summary-$childId-${sessionIds.joinToString(",")}")
) {
    LaunchedEffect(childId, sessionIds) { viewModel.load(childId, sessionIds) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Radius.Lg),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 480.dp, max = 640.dp)
        ) {
            SessionSummaryContent(state, onOpenReport = onOpenReport, onDismiss = onDismiss)
        }
    }
}

@Composable
fun SessionSummaryContent(state: SessionSummaryUiState, onOpenReport: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        Text(
            stringResource(R.string.summary_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        when (state) {
            SessionSummaryUiState.Loading -> Skeleton(rows = 3)
            SessionSummaryUiState.Empty -> EmptyState(icon = Icons.Filled.Info, message = stringResource(R.string.summary_empty))
            is SessionSummaryUiState.Error -> ErrorState(stringResource(R.string.summary_error), onDismiss, Modifier.fillMaxWidth())
            SessionSummaryUiState.NotFinished -> MutedText(stringResource(R.string.summary_not_finished))
            is SessionSummaryUiState.Content ->
                Column(
                    Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S3)
                ) {
                    state.summaries.forEachIndexed { i, summary ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SummaryBody(summary)
                    }
                }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S3, Alignment.End)) {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(Radius.Sm), modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.summary_close))
            }
            if (state is SessionSummaryUiState.Content) {
                Button(onClick = onOpenReport, shape = RoundedCornerShape(Radius.Sm), modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.summary_open_report), modifier = Modifier.padding(start = Spacing.S2))
                }
            }
        }
    }
}

@Composable
private fun SummaryBody(summary: SessionSummary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(summary.taskName, style = MaterialTheme.typography.titleLarge)
            val duration = summary.durationMs?.let { " · " + formatDuration(it) }.orEmpty()
            MutedText(formatDateTime(summary.startedAt) + duration)
        }
        HistoryStateChip(summary.state)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        LevelTile(StepLevel.THREE, summary.levelThree, Modifier.weight(1f))
        LevelTile(StepLevel.TWO, summary.levelTwo, Modifier.weight(1f))
        LevelTile(StepLevel.ONE, summary.levelOne, Modifier.weight(1f))
        LevelTile(StepLevel.NOT_SCORED, summary.notScored, Modifier.weight(1f))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
        MutedText(pluralStringResource(R.plurals.summary_steps, summary.steps, westernDigits(summary.steps)))
        if (summary.notReached > 0) {
            NeutralPill(stringResource(R.string.summary_not_reached, westernDigits(summary.notReached)))
        }
        ColorPill(LevelColors.CannotTell, stringResource(R.string.summary_cannot_tell, westernDigits(summary.cannotTell)))
        StarCount(summary.stars)
    }
}

/** One Rule 1 level with its count: Western number large, level number + word under it, level colour as a tint. */
@Composable
private fun LevelTile(level: StepLevel, count: Int, modifier: Modifier) {
    val color: Color = levelColor(level)
    val word = stringResource(levelWord(level))
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(Radius.Md),
        modifier = modifier.clearAndSetSemantics { contentDescription = "$word: $count" }
    ) {
        Column(
            Modifier.padding(Spacing.S3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.S1)
        ) {
            Text(westernDigits(count), style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                val number = levelNumber(level)
                Text(
                    number?.let { "${westernDigits(it)} · $word" } ?: word,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(device = Devices.TABLET)
@Composable
private fun SessionSummaryPreview() {
    DlsTheme {
        SessionSummaryContent(
            SessionSummaryUiState.Content(
                listOf(
                    SessionSummary("s", "c", "Drinking from a cup", 1_700_000_000_000L, 540_000L, 8, 3, 1, 1, 0, 1, 12, HistoryState.DONE)
                )
            ),
            onOpenReport = {},
            onDismiss = {}
        )
    }
}
