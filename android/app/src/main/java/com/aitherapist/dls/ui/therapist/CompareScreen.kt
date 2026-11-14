package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.Spacing

/** Progress tab: compare two assessments (5.3) and training history (5.4); both reload each time the tab opens. */
@Composable
fun ProgressRoute(childId: String, onStartSession: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S4)) {
        CompareRoute(childId, onStartSession)
        TrainingHistoryRoute(childId)
    }
}

@Composable
fun CompareRoute(childId: String, onStartSession: () -> Unit = {}, viewModel: CompareViewModel = hiltViewModel(key = "compare-$childId")) {
    LaunchedEffect(childId) { viewModel.load(childId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompareScreen(state, viewModel::selectFirst, viewModel::selectSecond, viewModel::retry, onStartSession)
}

@Composable
fun CompareScreen(
    state: CompareUiState,
    onFirst: (String) -> Unit,
    onSecond: (String) -> Unit,
    onRetry: () -> Unit,
    onStartSession: () -> Unit = {}
) {
    when (state) {
        CompareUiState.Loading -> Skeleton(rows = 3)
        is CompareUiState.Error -> ErrorState(stringResource(R.string.compare_error), onRetry, Modifier.fillMaxWidth())
        CompareUiState.NotEnough ->
            TCard {
                CardHeading(stringResource(R.string.compare_title))
                EmptyState(
                    icon = Icons.Filled.Info,
                    message = stringResource(R.string.compare_not_enough),
                    primaryActionLabel = stringResource(R.string.new_session),
                    onPrimaryAction = onStartSession
                )
            }
        is CompareUiState.Content ->
            TCard(spacing = Spacing.S3) {
                CardHeading(stringResource(R.string.compare_title))
                MutedText(stringResource(R.string.compare_pick_first))
                SessionChips(state.sessions, state.firstId, onFirst)
                if (state.firstId != null) {
                    MutedText(stringResource(R.string.compare_pick_second))
                    SessionChips(state.secondChoices, state.secondId, onSecond)
                }
                state.result?.let { CompareTable(it) }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionChips(sessions: List<CompareSession>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        sessions.forEach { s ->
            FilterChip(
                selected = s.id == selected,
                onClick = { onSelect(s.id) },
                label = { Text("${s.taskName} · ${formatDateTime(s.at)}") }
            )
        }
    }
}

@Composable
private fun CompareTable(result: CompareResult) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    if (result.rows.isEmpty()) {
        MutedText(stringResource(R.string.compare_no_common))
        return
    }
    MutedText(pluralStringResource(R.plurals.compare_common_steps, result.commonSteps, westernDigits(result.commonSteps)))
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.S1)) {
        HeaderCell(stringResource(R.string.compare_col_domain), Modifier.weight(2f))
        HeaderCell(formatDate(result.olderAt), Modifier.weight(1f))
        HeaderCell(formatDate(result.newerAt), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.compare_col_difference), Modifier.weight(2f))
        HeaderCell(stringResource(R.string.compare_col_steps), Modifier.weight(1f))
    }
    val older = formatDate(result.olderAt)
    val newer = formatDate(result.newerAt)
    result.rows.forEach { row ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val name = domainName(row.domain)
        val word = stringResource(trendWord(row.trend))
        val change = row.difference?.let { "${ltr(it)} $word" } ?: word
        val description =
            pluralStringResource(
                R.plurals.compare_row_description,
                row.steps,
                name,
                older,
                row.averageA,
                newer,
                row.averageB,
                change,
                westernDigits(row.steps)
            )
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.S2).clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(2f))
            Text(row.averageA, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(row.averageB, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Row(
                Modifier.weight(2f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S1)
            ) {
                // Calm trend: neutral colour and a word, never red.
                trendIcon(row.trend)?.let {
                    Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(change, style = MaterialTheme.typography.bodyLarge)
            }
            Text(westernDigits(row.steps), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

private fun trendIcon(trend: Trend) = when (trend) {
    Trend.UP, Trend.SLIGHTLY_UP -> Icons.Filled.KeyboardArrowUp
    Trend.DOWN, Trend.SLIGHTLY_DOWN -> Icons.Filled.KeyboardArrowDown
    Trend.SAME -> null
}

private fun trendWord(trend: Trend): Int = when (trend) {
    Trend.UP -> R.string.compare_trend_up
    Trend.DOWN -> R.string.compare_trend_down
    Trend.SLIGHTLY_UP -> R.string.compare_trend_slightly_up
    Trend.SLIGHTLY_DOWN -> R.string.compare_trend_slightly_down
    Trend.SAME -> R.string.compare_trend_same
}

/** Keeps a signed number ("-0.4") whole inside RTL text. */
private fun ltr(text: String): String = BidiFormatter.getInstance().unicodeWrap(text, TextDirectionHeuristicsCompat.LTR)
