package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.Spacing

private const val MS_PER_SECOND = 1_000L

@Composable
fun TrainingHistoryRoute(childId: String, viewModel: TrainingHistoryViewModel = hiltViewModel(key = "training-history-$childId")) {
    LaunchedEffect(childId) { viewModel.load(childId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrainingHistoryScreen(state, viewModel::retry)
}

@Composable
fun TrainingHistoryScreen(state: TrainingHistoryUiState, onRetry: () -> Unit) {
    when (state) {
        TrainingHistoryUiState.Loading -> Skeleton(rows = 3)
        is TrainingHistoryUiState.Error -> ErrorState(stringResource(R.string.training_history_error), onRetry, Modifier.fillMaxWidth())
        TrainingHistoryUiState.Empty ->
            TCard {
                CardHeading(stringResource(R.string.training_history_title))
                EmptyState(icon = Icons.Filled.Info, message = stringResource(R.string.training_history_empty))
            }
        is TrainingHistoryUiState.Content ->
            TCard(spacing = Spacing.S2) {
                CardHeading(stringResource(R.string.training_history_title))
                state.rows.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TrainingRowItem(row)
                }
            }
    }
}

@Composable
private fun TrainingRowItem(row: TrainingRow) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.S2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            MutedText(formatDateTime(row.at))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            Text(
                row.count?.let { stringResource(R.string.training_history_count, westernDigits(it)) }
                    ?: stringResource(R.string.training_history_guided),
                style = MaterialTheme.typography.labelLarge
            )
            MutedText(formatDuration(row.durationSec * MS_PER_SECOND))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            Icon(
                if (row.criterionMet) Icons.Filled.CheckCircle else Icons.Filled.Refresh,
                contentDescription = null,
                tint = if (row.criterionMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                stringResource(if (row.criterionMet) R.string.training_history_met else R.string.training_history_not_met),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
