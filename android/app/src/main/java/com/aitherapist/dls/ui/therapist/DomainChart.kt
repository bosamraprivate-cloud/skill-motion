package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.scoring.PlanLevel
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.ui.theme.LevelColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/** Plan-level colour (DESIGN.md plan ramp: calm, no red); always shown with the word and number. */
fun planColor(level: PlanLevel): Color = when (level) {
    PlanLevel.STRENGTH -> LevelColors.PlanStrength
    PlanLevel.MILD -> LevelColors.PlanMild
    PlanLevel.MODERATE -> LevelColors.PlanModerate
    PlanLevel.SEVERE -> LevelColors.PlanSevere
}

fun planLabel(level: PlanLevel): Int = when (level) {
    PlanLevel.STRENGTH -> R.string.plan_strength
    PlanLevel.MILD -> R.string.plan_mild
    PlanLevel.MODERATE -> R.string.plan_moderate
    PlanLevel.SEVERE -> R.string.plan_severe
}

/** Arabic/English UI name of a scale domain tag; an unknown tag shows as written. */
@Composable
fun domainName(tag: String): String = when (tag) {
    RulesEngine.MOTOR_COORDINATION -> stringResource(R.string.domain_motor_coordination)
    "Motor planning" -> stringResource(R.string.domain_motor_planning)
    "Range of motion" -> stringResource(R.string.domain_range_of_motion)
    "Hand skills" -> stringResource(R.string.domain_hand_skills)
    "Midline crossing" -> stringResource(R.string.domain_midline_crossing)
    else -> tag
}

/** Short x-axis label of a domain (full names stay in the table); an unknown tag shows as written. */
@Composable
fun domainShortName(tag: String): String = when (tag) {
    RulesEngine.MOTOR_COORDINATION -> stringResource(R.string.domain_short_motor_coordination)
    "Motor planning" -> stringResource(R.string.domain_short_motor_planning)
    "Range of motion" -> stringResource(R.string.domain_short_range_of_motion)
    "Hand skills" -> stringResource(R.string.domain_short_hand_skills)
    "Midline crossing" -> stringResource(R.string.domain_short_midline_crossing)
    else -> tag
}

/** Full spoken description of one domain row (also used for the chart's single description). */
@Composable
fun domainDescription(row: DomainRow, name: String = domainName(row.domain)): String {
    val avg = row.average
    val plan = row.planLevel
    return when {
        !row.covered -> stringResource(R.string.report_domain_not_covered, name)
        avg == null || plan == null -> stringResource(R.string.report_domain_no_scored, name)
        else ->
            pluralStringResource(
                R.plurals.report_domain_description,
                row.scoredSteps,
                westernDigits(row.scoredSteps),
                name,
                formatAverage(avg),
                stringResource(planLabel(plan))
            )
    }
}

private const val MAX_AVERAGE = 3.0

/**
 * Vico bar chart of the domains that have scored steps: each bar in its plan-level colour, labelled with the average
 * (one decimal, rounded down) and the plan word. TalkBack reads one description listing every bar; the domain table
 * rows are the per-domain accessible path.
 */
@Composable
fun DomainChart(rows: List<DomainRow>, modifier: Modifier = Modifier) {
    val bars = rows.filter { it.average != null && it.planLevel != null }
    if (bars.isEmpty()) return
    val shortNames = bars.map { domainShortName(it.domain) }
    val words = PlanLevel.entries.associateWith { stringResource(planLabel(it)) }
    val description = stringResource(R.string.report_chart_description) + ": " + bars.map { domainDescription(it) }.joinToString("; ")
    val producer = remember { CartesianChartModelProducer() }
    val values = bars.map { it.average!! }
    LaunchedEffect(values) { producer.runTransaction { columnSeries { series(values) } } }
    val levels = bars.map { it.planLevel!! }
    val columns = remember(levels) { levels.map { LineComponent(Fill(planColor(it)), 28.dp, RectangleShape) } }
    val provider =
        remember(columns) {
            object : ColumnCartesianLayer.ColumnProvider {
                override fun getColumn(entry: ColumnCartesianLayerModel.Entry, seriesIndex: Int, extraStore: ExtraStore): LineComponent =
                    columns[entry.x.toInt().coerceIn(columns.indices)]

                override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore): LineComponent = columns.first()
            }
        }
    val dataLabelFormatter =
        remember(words) {
            object : CartesianValueFormatter {
                override fun format(
                    context: CartesianMeasuringContext,
                    value: Double,
                    verticalAxisPosition: Axis.Position.Vertical?
                ): CharSequence = "${formatAverage(value)} ${words.getValue(RulesEngine.planLevel(value))}"
            }
        }
    val xFormatter =
        remember(shortNames) {
            object : CartesianValueFormatter {
                override fun format(
                    context: CartesianMeasuringContext,
                    value: Double,
                    verticalAxisPosition: Axis.Position.Vertical?
                ): CharSequence = shortNames.getOrElse(value.toInt()) { "" }
            }
        }
    val text = rememberTextComponent(MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface))
    CartesianChartHost(
        chart =
        rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = provider,
                dataLabel = text,
                dataLabelValueFormatter = dataLabelFormatter,
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = MAX_AVERAGE)
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xFormatter)
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(260.dp).clearAndSetSemantics { contentDescription = description }
    )
}
