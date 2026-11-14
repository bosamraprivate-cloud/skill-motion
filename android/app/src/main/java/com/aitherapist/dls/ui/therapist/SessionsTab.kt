package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/** Sessions tab: the child's assessments and training runs, newest first; read-only. A done assessment opens its summary. */
@Composable
fun SessionsScreen(
    state: SessionsUiState,
    onRetry: () -> Unit,
    onOpenSummary: (sessionId: String) -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S4)) {
        when (state) {
            SessionsUiState.Loading -> Skeleton(rows = 5)
            is SessionsUiState.Error -> ErrorState(stringResource(R.string.sessions_error), onRetry, Modifier.fillMaxWidth())
            is SessionsUiState.Content ->
                if (state.items.isEmpty()) {
                    TCard {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.List,
                            message = stringResource(R.string.sessions_empty),
                            primaryActionLabel = stringResource(R.string.new_session),
                            onPrimaryAction = onStartSession
                        )
                    }
                } else {
                    TCard(spacing = 0.dp) {
                        state.items.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            SessionRow(item, onOpenSummary)
                        }
                    }
                }
        }
    }
}

@Composable
private fun SessionRow(item: SessionListItem, onOpenSummary: (String) -> Unit) {
    val clickable = item.opensSummary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .then(if (clickable) Modifier.clickable(role = Role.Button) { onOpenSummary(item.id) } else Modifier)
            .padding(vertical = Spacing.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
    ) {
        KindIcon(item.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val kind =
                if (item.kind == HistoryKind.ASSESSMENT) {
                    stringResource(R.string.sessions_kind_assessment)
                } else {
                    stringResource(R.string.sessions_kind_training) + " · " +
                        pluralStringResource(R.plurals.sessions_activities, item.activities, westernDigits(item.activities))
                }
            MutedText("$kind · ${formatDateTime(item.startedAt)}")
        }
        StarCount(item.stars)
        HistoryStateChip(item.state)
        Box(Modifier.size(24.dp)) {
            if (clickable) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.sessions_open_summary),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KindIcon(kind: HistoryKind) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.Sm)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (kind == HistoryKind.ASSESSMENT) Icons.Filled.CheckCircle else Icons.Filled.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Stars earned, star icon + Western number (therapist view of the child's stars). */
@Composable
internal fun StarCount(stars: Int, modifier: Modifier = Modifier) {
    val label = pluralStringResource(R.plurals.sessions_stars, stars, westernDigits(stars))
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S1)
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = ChildColors.Star, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
