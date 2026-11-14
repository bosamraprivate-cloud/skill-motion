package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.scoring.Metrics
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.SectionCard
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Spacing
import java.util.Locale

@Composable
fun MetricsRoute(viewModel: MetricsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MetricsScreen(state, onSelect = viewModel::select, onRetry = viewModel::retry)
}

/** Western digits regardless of locale. */
private fun percent(value: Double): String = String.format(Locale.ROOT, "%.1f %%", value * 100)

private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

/** Accuracy dashboard (Story 5.8): Goal 8 cards, excluded counts and per-step confusion. Read-only. */
@Composable
fun MetricsScreen(state: MetricsUiState, onSelect: (Boolean) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.S4),
        verticalArrangement = Arrangement.spacedBy(Spacing.S4)
    ) {
        PageHeader(stringResource(if (state.volunteer) R.string.metrics_title_volunteer else R.string.metrics_title_live))
        Text(
            stringResource(R.string.metrics_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            FilterChip(state.volunteer, onClick = { onSelect(true) }, label = { Text(stringResource(R.string.metrics_filter_volunteer)) })
            FilterChip(!state.volunteer, onClick = { onSelect(false) }, label = { Text(stringResource(R.string.metrics_filter_live)) })
        }
        when (state) {
            is MetricsUiState.Loading -> Skeleton(rows = 3)
            is MetricsUiState.Empty -> EmptyState(Icons.Filled.Info, stringResource(R.string.metrics_empty))
            is MetricsUiState.Error -> ErrorState(stringResource(R.string.metrics_error, state.reason), onRetry)
            is MetricsUiState.MissingCodes ->
                ErrorState(pluralStringResource(R.plurals.metrics_missing_codes, state.count, state.count.toString()), onRetry)
            is MetricsUiState.Content -> MetricsContent(state.metrics, state.excluded)
        }
    }
}

@Composable
private fun MetricsContent(m: Metrics.Result, excluded: Int) {
    val width = Modifier.widthIn(max = 760.dp).fillMaxWidth()
    if (m.rated == 0) {
        Text(stringResource(R.string.metrics_no_blind_hint), style = MaterialTheme.typography.bodyMedium, modifier = width)
    }
    FlowRow(width, horizontalArrangement = Arrangement.spacedBy(Spacing.S3), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        MetricCard(
            stringResource(R.string.metrics_agreement),
            m.agreement?.let { "${percent(it)} (${m.agree}/${m.decided})" },
            stringResource(R.string.metrics_target_min, percent(Metrics.AGREEMENT_TARGET)),
            m.agreementMet
        )
        MetricCard(
            stringResource(R.string.metrics_kappa),
            m.kappa?.let {
                decimal(it) + " (" +
                    pluralStringResource(R.plurals.metrics_steps, m.kappaPairs.size, m.kappaPairs.size.toString()) +
                    ")"
            },
            stringResource(R.string.metrics_target_min, Metrics.KAPPA_TARGET.toString()),
            m.kappaMet
        )
        MetricCard(
            stringResource(R.string.metrics_cannot_tell),
            m.cannotTellRate?.let { "${percent(it)} (${m.appCannotTell}/${m.attempts})" },
            stringResource(R.string.metrics_target_max, percent(Metrics.CANNOT_TELL_MAX)),
            m.cannotTellMet
        )
    }
    SectionCard(stringResource(R.string.metrics_counts_title), width) {
        Text(stringResource(R.string.metrics_counts_attempts, m.attempts.toString(), m.rated.toString(), m.unrated.toString()))
        Text(stringResource(R.string.metrics_counts_cannot_tell, m.eitherCannotTell.toString()))
        Text(
            stringResource(
                R.string.metrics_counts_steps,
                m.steps.toString(),
                m.kappaPairs.size.toString(),
                m.partialSteps.toString(),
                m.notScoredSteps.toString(),
                m.appOpenSteps.toString(),
                m.blindOpenSteps.toString()
            )
        )
        if (excluded > 0) Text(pluralStringResource(R.plurals.metrics_excluded_sessions, excluded, excluded.toString()))
        Text(stringResource(R.string.metrics_counts_corrections, m.correctedSteps.toString(), m.correctedChanged.toString()))
    }
    if (m.confusion.isNotEmpty()) ConfusionTable(m.confusion, width)
}

@Composable
private fun MetricCard(title: String, value: String?, target: String, met: Boolean?) {
    val result =
        stringResource(
            when (met) {
                true -> R.string.metrics_met
                false -> R.string.metrics_below
                null -> R.string.metrics_na
            }
        )
    val shown = value ?: stringResource(R.string.metrics_na)
    SectionCard(title, Modifier.width(220.dp).semantics(mergeDescendants = true) {}) {
        Text(shown, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The word carries the result; the icon and colour only repeat it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            if (met != null) {
                Icon(
                    if (met) Icons.Filled.Check else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Text(
                result,
                style = MaterialTheme.typography.labelLarge,
                color = if (met == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private val SHORT = mapOf(Metrics.DONE to "d", Metrics.NOT_DONE to "n", Metrics.CANNOT_TELL to "c")

@Composable
private fun ConfusionTable(confusion: Map<String, Map<Pair<String, String>, Int>>, modifier: Modifier) {
    val cells = Metrics.VERDICTS.flatMap { b -> Metrics.VERDICTS.map { a -> b to a } }
    val names =
        mapOf(
            Metrics.DONE to stringResource(R.string.metrics_verdict_done),
            Metrics.NOT_DONE to stringResource(R.string.metrics_verdict_not_done),
            Metrics.CANNOT_TELL to stringResource(R.string.metrics_verdict_cannot_tell)
        )
    val cellFormat = stringResource(R.string.metrics_cell_description)
    SectionCard(stringResource(R.string.metrics_confusion_title), modifier) {
        Text(stringResource(R.string.metrics_confusion_legend), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            val headerFormat = stringResource(R.string.metrics_cell_header)
            Row {
                Cell(stringResource(R.string.metrics_step), STEP_WIDTH, header = stringResource(R.string.metrics_step))
                cells.forEach { (b, a) ->
                    Cell("${SHORT[b]}/${SHORT[a]}", CELL_WIDTH, header = String.format(headerFormat, names[b], names[a]))
                }
            }
            HorizontalDivider()
            confusion.forEach { (step, counts) ->
                val description =
                    step + ": " +
                        cells.joinToString("; ") { (b, a) ->
                            String.format(cellFormat, names[b], names[a], (counts[b to a] ?: 0).toString())
                        }
                Row(Modifier.clearAndSetSemantics { contentDescription = description }) {
                    Cell(step, STEP_WIDTH)
                    cells.forEach { Cell((counts[it] ?: 0).toString(), CELL_WIDTH) }
                }
            }
        }
    }
}

private val STEP_WIDTH = 140.dp
private val CELL_WIDTH = 52.dp

@Composable
private fun Cell(text: String, width: androidx.compose.ui.unit.Dp, header: String? = null) {
    // A header cell is a column heading for TalkBack, read with its full name (blind verdict / app verdict).
    Text(
        text,
        modifier =
        Modifier.width(width).padding(vertical = Spacing.S1).let { m ->
            if (header != null) {
                m.clearAndSetSemantics {
                    heading()
                    contentDescription = header
                }
            } else {
                m
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (header != null) FontWeight.SemiBold else FontWeight.Normal
    )
}

@Preview(widthDp = 900, heightDp = 900)
@Composable
private fun MetricsScreenPreview() {
    DlsTheme {
        MetricsScreen(
            MetricsUiState.Content(
                true,
                Metrics.compute(
                    Metrics.parse(
                        "sessionId,stepId,attemptId,attemptNumber,promptLevel,verdict,level,correctedLevel,blindVerdict,clipFile\n" +
                            "s,a,a1,1,1,done,3,,done,\ns,b,b1,1,1,cannot_tell,,,done,\n"
                    )
                )
            ),
            onSelect = {},
            onRetry = {}
        )
    }
}
