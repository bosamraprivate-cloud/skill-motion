package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/** Report tab of the child profile; reloads every time it opens (no stored report). */
@Composable
fun ReportRoute(childId: String, onStartSession: () -> Unit = {}, viewModel: ReportViewModel = hiltViewModel(key = "report-$childId")) {
    LaunchedEffect(childId) { viewModel.load(childId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportScreen(state, onRetry = viewModel::retry, onStartSession = onStartSession)
}

@Composable
fun ReportScreen(state: ReportUiState, onRetry: () -> Unit, modifier: Modifier = Modifier, onStartSession: () -> Unit = {}) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S4)) {
        when (state) {
            ReportUiState.Loading -> Skeleton(rows = 5)
            ReportUiState.Empty ->
                TCard {
                    EmptyState(
                        icon = Icons.Filled.Info,
                        message = stringResource(R.string.report_empty),
                        primaryActionLabel = stringResource(R.string.new_session),
                        onPrimaryAction = onStartSession
                    )
                }
            is ReportUiState.Error -> ErrorState(stringResource(R.string.report_error), onRetry, Modifier.fillMaxWidth())
            is ReportUiState.Content -> state.tasks.forEach { TaskSection(it) }
        }
    }
}

@Composable
private fun TaskSection(task: TaskReport) {
    TCard(spacing = Spacing.S3) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            Text(
                task.taskName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (task.assessedAt > 0L) MutedText(stringResource(R.string.report_assessed_on, formatDate(task.assessedAt)))
        }
        LevelTally(task)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CardHeading(stringResource(R.string.report_strengths))
        if (task.strengths.isEmpty()) {
            MutedText(stringResource(R.string.report_no_strengths))
        } else {
            StepChips(task.strengths)
        }
        CardHeading(stringResource(R.string.report_needs), Modifier.padding(top = Spacing.S2))
        if (task.needs.isEmpty()) {
            MutedText(stringResource(R.string.report_no_needs))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S2)) { task.needs.forEach { NeedRow(it) } }
        }
        HorizontalDivider(Modifier.padding(top = Spacing.S2), color = MaterialTheme.colorScheme.outlineVariant)
        CardHeading(stringResource(R.string.report_domains))
        DomainChart(task.domains)
        DomainTable(task.domains)
        if (task.untaggedCount > 0) {
            MutedText(pluralStringResource(R.plurals.report_untagged_note, task.untaggedCount, westernDigits(task.untaggedCount)))
        }
        if (task.unknownCount > 0) {
            MutedText(pluralStringResource(R.plurals.report_unknown_note, task.unknownCount, westernDigits(task.unknownCount)))
        }
    }
}

/** One-line count of strengths, needs and not-scored steps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelTally(task: TaskReport) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        val two = task.needs.count { it.level == StepLevel.TWO }
        val one = task.needs.count { it.level == StepLevel.ONE }
        TallyPill(StepLevel.THREE, task.strengths.size)
        TallyPill(StepLevel.TWO, two)
        TallyPill(StepLevel.ONE, one)
        if (task.notScoredCount > 0) {
            NeutralPill(pluralStringResource(R.plurals.report_not_scored, task.notScoredCount, westernDigits(task.notScoredCount)))
        }
    }
}

@Composable
private fun TallyPill(level: StepLevel, count: Int) {
    ColorPill(levelColor(level), "${stringResource(levelWord(level))}: ${westernDigits(count)}")
}

/** Strength steps as a wrap of readable chips (step number and text). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepChips(steps: List<StepLine>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        steps.forEach { step ->
            Row(
                Modifier
                    .background(levelColor(StepLevel.THREE).copy(alpha = 0.10f), RoundedCornerShape(Radius.Sm))
                    .padding(horizontal = Spacing.S3, vertical = Spacing.S2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S2)
            ) {
                step.number?.let { StepNumber(it) }
                Text(step.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun StepNumber(number: Int) {
    Text(
        westernDigits(number),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(24.dp)
    )
}

/** A need: level chip, step text, and its domains as small neutral chips ("may be related to"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NeedRow(need: NeedLine) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(Radius.Sm))
            .padding(Spacing.S3),
        verticalArrangement = Arrangement.spacedBy(Spacing.S2)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            need.number?.let { StepNumber(it) }
            Text(need.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            LevelChip(need.level)
        }
        if (need.domains.isEmpty()) {
            MutedText(stringResource(R.string.report_no_domain))
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S2),
                verticalArrangement = Arrangement.spacedBy(Spacing.S1),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                MutedText(stringResource(R.string.report_may_relate_label))
                need.domains.forEach { NeutralPill(domainName(it), background = MaterialTheme.colorScheme.surface) }
            }
        }
    }
}

/** Domain table in the chart's order: name, average (Western, one decimal), plan word with its colour, scored steps. */
@Composable
private fun DomainTable(rows: List<DomainRow>) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.S2).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
        ) {
            TableHeader(stringResource(R.string.report_col_domain), Modifier.weight(1f))
            TableHeader(stringResource(R.string.report_col_average), Modifier.width(AVG_COL))
            TableHeader(stringResource(R.string.report_col_plan), Modifier.width(PLAN_COL))
            TableHeader(stringResource(R.string.report_col_steps), Modifier.width(STEPS_COL))
        }
        rows.forEach {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DomainTableRow(it)
        }
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** One domain row; TalkBack reads it as one node with the full description (the accessible path to the chart data). */
@Composable
private fun DomainTableRow(row: DomainRow) {
    val name = domainName(row.domain)
    val description = domainDescription(row, name)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val avg = row.average
        val plan = row.planLevel
        if (!row.covered || avg == null || plan == null) {
            MutedText(
                stringResource(if (!row.covered) R.string.report_not_covered else R.string.report_no_scored_steps),
                Modifier.width(AVG_COL + PLAN_COL + STEPS_COL + Spacing.S3 * 2)
            )
        } else {
            Text(formatAverage(avg), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(AVG_COL))
            Row(Modifier.width(PLAN_COL)) { ColorPill(planColor(plan), stringResource(planLabel(plan))) }
            Text(westernDigits(row.scoredSteps), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(STEPS_COL))
        }
    }
}

private val AVG_COL = 72.dp
private val PLAN_COL = 176.dp
private val STEPS_COL = 64.dp

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
                        unknownCount = 0,
                        assessedAt = 1_700_000_000_000L
                    )
                )
            ),
            onRetry = {}
        )
    }
}
