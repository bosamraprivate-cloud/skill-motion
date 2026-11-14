package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/** Plan tab of the child profile (Story 4.3); reloads every time it opens. */
@Composable
fun PlanRoute(
    childId: String,
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean,
    viewModel: PlanViewModel = hiltViewModel(key = "plan-$childId")
) {
    LaunchedEffect(childId) { viewModel.load(childId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var refused by rememberSaveable(childId) { mutableStateOf(false) }
    PlanScreen(
        state,
        onToggle = viewModel::toggle,
        onStart = { ids ->
            // The child comes from the same source as the plan (this route's childId).
            refused = !onStartTraining(childId, ids)
            if (!refused) viewModel.onTrainingStarted()
        },
        onRetry = viewModel::retry,
        startRefused = refused
    )
}

@Composable
fun PlanScreen(
    state: PlanUiState,
    onToggle: (String) -> Unit,
    onStart: (List<String>) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    startRefused: Boolean = false
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        when (state) {
            PlanUiState.Loading -> Skeleton(rows = 5)
            PlanUiState.Empty -> PlanCard { EmptyState(icon = Icons.Filled.DateRange, message = stringResource(R.string.plan_empty)) }
            PlanUiState.AllStrengths ->
                PlanCard { EmptyState(icon = Icons.Filled.ThumbUp, message = stringResource(R.string.plan_all_strengths)) }
            is PlanUiState.Error -> ErrorState(stringResource(R.string.plan_error), onRetry, Modifier.fillMaxWidth())
            is PlanUiState.Content -> {
                if (state.reloadError) ErrorState(stringResource(R.string.plan_error), onRetry, Modifier.fillMaxWidth())
                StartBar(state.selectedInOrder, onStart)
                if (startRefused) Muted(stringResource(R.string.plan_start_refused))
                state.sections.forEach { PlanSectionCard(it, state.selected, onToggle) }
            }
        }
    }
}

@Composable
private fun StartBar(selected: List<String>, onStart: (List<String>) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        Text(
            pluralStringResource(R.plurals.plan_selected_count, selected.size, westernDigits(selected.size)),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { onStart(selected) },
            enabled = selected.isNotEmpty(),
            shape = RoundedCornerShape(Radius.Sm),
            modifier = Modifier.height(48.dp)
        ) { Text(stringResource(R.string.plan_start_training)) }
    }
}

@Composable
private fun PlanSectionCard(section: PlanSectionUi, selected: Set<String>, onToggle: (String) -> Unit) {
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            Text(
                domainName(section.domain),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Badge(stringResource(planLabel(section.planLevel)), planColor(section.planLevel).copy(alpha = 0.15f))
        }
        section.sources.forEach { source ->
            val avg = source.average?.let(::formatAverage) ?: NO_AVERAGE
            Muted(stringResource(R.string.plan_source, bidi(source.taskName), bidi(avg)))
        }
        if (section.activities.isEmpty()) {
            Muted(stringResource(R.string.plan_no_activities))
        }
        section.activities.forEachIndexed { i, activity ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActivityRow(section.domain, activity, activity.id in selected) { onToggle(activity.id) }
        }
    }
}

@Composable
private fun ActivityRow(sectionKey: String, activity: PlanActivity, checked: Boolean, onToggle: () -> Unit) {
    var expanded by rememberSaveable(sectionKey, activity.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S2)
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(activity.textAr, style = MaterialTheme.typography.bodyLarge)
                Text(
                    activity.title,
                    style = LtrContent.merge(MaterialTheme.typography.bodyMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (activity.cameraCounted) {
                Badge(stringResource(R.string.plan_camera_counted), MaterialTheme.colorScheme.secondaryContainer)
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.plan_collapse else R.string.plan_expand)
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = Spacing.S4 + Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                Detail(R.string.plan_tools, activity.tools)
                Detail(R.string.plan_how, activity.howToPerform)
                Detail(R.string.plan_goal, activity.goal)
                Detail(R.string.plan_criterion, activity.criterion)
            }
        }
    }
}

@Composable
private fun Detail(label: Int, text: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        // Catalogue detail text is English: content direction, left aligned, even in the Arabic UI.
        Text(text, style = LtrContent.merge(MaterialTheme.typography.bodyMedium), modifier = Modifier.fillMaxWidth())
    }
}

private val LtrContent = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Left)

/** Shown when a source task has no average for the domain (never 0.0). */
private const val NO_AVERAGE = "—"

private fun bidi(text: String): String = BidiFormatter.getInstance().unicodeWrap(text)

@Composable
private fun Badge(text: String, background: Color) {
    Text(
        bidi(text),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.background(background, RoundedCornerShape(Radius.Sm)).padding(horizontal = Spacing.S2, vertical = Spacing.S1)
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PlanCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(Radius.Md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S2), content = content)
    }
}
