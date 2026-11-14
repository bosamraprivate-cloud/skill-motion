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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.scoring.PlanLevel
import com.aitherapist.dls.scoring.StepLevel
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.LevelColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/** Report tab of the child profile; reloads every time it opens (no stored report). */
@Composable
fun ReportRoute(childId: String, viewModel: ReportViewModel = hiltViewModel(key = "report-$childId")) {
    LaunchedEffect(childId) { viewModel.load(childId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportScreen(state, onRetry = viewModel::retry)
}

@Composable
fun ReportScreen(state: ReportUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        when (state) {
            ReportUiState.Loading -> Skeleton(rows = 5)
            ReportUiState.Empty -> ReportCard { EmptyState(icon = Icons.Filled.Info, message = stringResource(R.string.report_empty)) }
            is ReportUiState.Error -> ErrorState(stringResource(R.string.report_error), onRetry, Modifier.fillMaxWidth())
            is ReportUiState.Content -> state.tasks.forEach { TaskSection(it) }
        }
    }
}

@Composable
private fun TaskSection(task: TaskReport) {
    ReportCard {
        Text(task.taskName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        SubHeading(stringResource(R.string.report_strengths))
        if (task.strengths.isEmpty()) {
            MutedText(stringResource(R.string.report_no_strengths))
        } else {
            task.strengths.forEach { StepRow(it.number, it.text, StepLevel.THREE) }
        }
        SubHeading(stringResource(R.string.report_needs))
        if (task.needs.isEmpty()) {
            MutedText(stringResource(R.string.report_no_needs))
        } else {
            task.needs.forEach { NeedRow(it) }
        }
        if (task.notScoredCount > 0) {
            MutedText(pluralStringResource(R.plurals.report_not_scored, task.notScoredCount, westernDigits(task.notScoredCount)))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SubHeading(stringResource(R.string.report_domains))
        DomainChart(task.domains)
        task.domains.forEach { DomainTableRow(it) }
        if (task.untaggedCount > 0) {
            MutedText(pluralStringResource(R.plurals.report_untagged_note, task.untaggedCount, westernDigits(task.untaggedCount)))
        }
        if (task.unknownCount > 0) {
            MutedText(pluralStringResource(R.plurals.report_unknown_note, task.unknownCount, westernDigits(task.unknownCount)))
        }
    }
}

@Composable
private fun NeedRow(need: NeedLine) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        StepRow(need.number, need.text, need.level)
        val related =
            if (need.domains.isEmpty()) {
                stringResource(R.string.report_no_domain)
            } else {
                stringResource(
                    R.string.report_may_relate,
                    need.domains.map {
                        domainName(it)
                    }.joinToString(stringResource(R.string.report_list_separator))
                )
            }
        MutedText(related, Modifier.padding(start = Spacing.S4))
    }
}

@Composable
private fun StepRow(number: Int?, text: String, level: StepLevel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        LevelChip(level)
        val prefix = number?.let { stringResource(R.string.report_step_number, westernDigits(it)) + " · " }.orEmpty()
        Text(prefix + text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Level pill: number + word, never colour alone. */
@Composable
private fun LevelChip(level: StepLevel) {
    val (score, word, color) =
        when (level) {
            StepLevel.THREE -> Triple(3, R.string.level_three, LevelColors.Level3)
            StepLevel.TWO -> Triple(2, R.string.level_two, LevelColors.Level2)
            else -> Triple(1, R.string.level_one, LevelColors.Level1)
        }
    ColorPill(color, "${westernDigits(score)} ${stringResource(word)}")
}

@Composable
private fun ColorPill(color: Color, label: String) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(Radius.Sm))
            .padding(horizontal = Spacing.S2, vertical = Spacing.S1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S1)
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** One domain row; TalkBack reads it as one node with the full description (the accessible path to the chart data). */
@Composable
private fun DomainTableRow(row: DomainRow) {
    val name = domainName(row.domain)
    val description = domainDescription(row, name)
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.S1).clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        val avg = row.average
        val plan = row.planLevel
        when {
            !row.covered -> MutedText(stringResource(R.string.report_not_covered))
            avg == null || plan == null -> MutedText(stringResource(R.string.report_no_scored_steps))
            else -> {
                Text(formatAverage(avg), style = MaterialTheme.typography.titleMedium)
                ColorPill(planColor(plan), stringResource(planLabel(plan)))
                MutedText(pluralStringResource(R.plurals.report_scored_steps, row.scoredSteps, westernDigits(row.scoredSteps)))
            }
        }
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.S2).semantics { heading() })
}

@Composable
private fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun ReportCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(Radius.Md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S2), content = content)
    }
}

@Preview(device = Devices.TABLET)
@Composable
private fun ReportPreview() {
    DlsTheme {
        ReportScreen(
            ReportUiState.Content(
                listOf(
                    TaskReport(
                        taskId = "t",
                        taskName = "Task",
                        strengths = listOf(StepLine("a", 1, "Step one")),
                        needs = listOf(NeedLine("b", 2, "Step two", StepLevel.TWO, listOf("Hand skills"))),
                        notScoredCount = 1,
                        domains = listOf(DomainRow("Hand skills", 2.5, PlanLevel.MILD, 2), DomainRow("Midline crossing", null, null, 0)),
                        untaggedCount = 0,
                        unknownCount = 0
                    )
                )
            ),
            onRetry = {}
        )
    }
}
