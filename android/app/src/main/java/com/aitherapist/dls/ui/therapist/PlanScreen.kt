package com.aitherapist.dls.ui.therapist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    modifier: Modifier = Modifier,
    onStartSession: () -> Unit = {},
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
        modifier = modifier,
        startRefused = refused,
        onStartSession = onStartSession
    )
}

/** Scrolling plan sections above a sticky "Start training (n)" bar. */
@Composable
fun PlanScreen(
    state: PlanUiState,
    onToggle: (String) -> Unit,
    onStart: (List<String>) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    startRefused: Boolean = false,
    onStartSession: () -> Unit = {}
) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = Spacing.S4),
            verticalArrangement = Arrangement.spacedBy(Spacing.S4)
        ) {
            when (state) {
                PlanUiState.Loading -> Skeleton(rows = 5)
                PlanUiState.Empty ->
                    TCard {
                        EmptyState(
                            icon = Icons.Filled.DateRange,
                            message = stringResource(R.string.plan_empty),
                            primaryActionLabel = stringResource(R.string.new_session),
                            onPrimaryAction = onStartSession
                        )
                    }
                PlanUiState.AllStrengths ->
                    TCard { EmptyState(icon = Icons.Filled.ThumbUp, message = stringResource(R.string.plan_all_strengths)) }
                is PlanUiState.Error -> ErrorState(stringResource(R.string.plan_error), onRetry, Modifier.fillMaxWidth())
                is PlanUiState.Content -> {
                    if (state.reloadError) ErrorState(stringResource(R.string.plan_error), onRetry, Modifier.fillMaxWidth())
                    MutedText(stringResource(R.string.plan_intro))
                    state.sections.forEach { PlanSectionCard(it, state.selected, onToggle) }
                }
            }
        }
        if (state is PlanUiState.Content) StartBar(state.selectedInOrder, startRefused, onStart)
    }
}

/** Sticky bottom bar: selection count and "Start training (n)", disabled with nothing selected. */
@Composable
private fun StartBar(selected: List<String>, refused: Boolean, onStart: (List<String>) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.S3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(R.plurals.plan_selected_count, selected.size, westernDigits(selected.size)),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (refused) MutedText(stringResource(R.string.plan_start_refused))
                }
                Button(
                    onClick = { onStart(selected) },
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(Radius.Sm),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.plan_start_training_count, westernDigits(selected.size)),
                        modifier = Modifier.padding(start = Spacing.S2)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanSectionCard(section: PlanSectionUi, selected: Set<String>, onToggle: (String) -> Unit) {
    TCard(spacing = Spacing.S2) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            Text(
                domainName(section.domain),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            ColorPill(planColor(section.planLevel), stringResource(planLabel(section.planLevel)))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            section.sources.forEach { source ->
                val avg = source.average?.let(::formatAverage) ?: NO_AVERAGE
                NeutralPill(stringResource(R.string.plan_source, bidi(source.taskName), bidi(avg)))
            }
        }
        if (section.activities.isEmpty()) {
            MutedText(stringResource(R.string.plan_no_activities))
        }
        Column(Modifier.padding(top = Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            section.activities.forEach { activity ->
                ActivityRow(section.domain, activity, activity.id in selected) { onToggle(activity.id) }
            }
        }
    }
}

@Composable
private fun ActivityRow(sectionKey: String, activity: PlanActivity, checked: Boolean, onToggle: () -> Unit) {
    var expanded by rememberSaveable(sectionKey, activity.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.Sm)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (checked) colors.primaryContainer else colors.surface)
            .border(1.dp, if (checked) colors.primary else colors.outlineVariant, shape)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { expanded = !expanded }.padding(end = Spacing.S2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S2)
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(vertical = Spacing.S2)) {
                Text(activity.textAr, style = MaterialTheme.typography.bodyLarge)
                Text(activity.title, style = LtrContent.merge(MaterialTheme.typography.bodyMedium), color = colors.onSurfaceVariant)
            }
            if (activity.cameraCounted) {
                NeutralPill(stringResource(R.string.plan_camera_counted), background = colors.secondaryContainer)
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.plan_collapse else R.string.plan_expand),
                tint = colors.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = Spacing.S4, vertical = Spacing.S3),
                verticalArrangement = Arrangement.spacedBy(Spacing.S3)
            ) {
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        // Catalogue detail text is English: content direction, left aligned, even in the Arabic UI.
        Text(text, style = LtrContent.merge(MaterialTheme.typography.bodyLarge), modifier = Modifier.fillMaxWidth())
    }
}

private val LtrContent = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Left)

/** Shown when a source task has no average for the domain (never 0.0). */
private const val NO_AVERAGE = "—"

private fun bidi(text: String): String = BidiFormatter.getInstance().unicodeWrap(text)
