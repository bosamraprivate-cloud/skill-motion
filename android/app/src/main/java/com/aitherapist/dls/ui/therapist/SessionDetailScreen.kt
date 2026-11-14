package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.scoring.StepLevel
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.ErrorColor
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/** Session detail (Stories 5.1, 5.2) as a full-screen dialog over the child profile. */
@Composable
fun SessionDetailDialog(
    childId: String,
    sessionId: String,
    onDismiss: () -> Unit,
    onOpenReport: (() -> Unit)? = null,
    onContinue: ((taskId: String, sessionId: String) -> Unit)? = null,
    viewModel: SessionDetailViewModel = hiltViewModel(key = "detail-$childId-$sessionId")
) {
    LaunchedEffect(childId, sessionId) { viewModel.load(childId, sessionId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            SessionDetailScreen(
                state,
                onBack = onDismiss,
                onRetry = viewModel::retry,
                onCorrect = viewModel::correct,
                onRemoveCorrection = viewModel::removeCorrection,
                loadFrame = viewModel::image,
                onOpenReport = onOpenReport,
                onContinue = onContinue
            )
        }
    }
}

@Composable
fun SessionDetailScreen(
    state: SessionDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCorrect: (stepId: String, level: StepLevel) -> Unit,
    onRemoveCorrection: (stepId: String) -> Unit,
    loadFrame: FrameLoader,
    modifier: Modifier = Modifier,
    onOpenReport: (() -> Unit)? = null,
    onContinue: ((taskId: String, sessionId: String) -> Unit)? = null
) {
    var correcting by rememberSaveable { mutableStateOf<String?>(null) }
    // Content capped and centred on a wide tablet so step cards stay readable (UX pass C; 8dp grid, 24dp gutters).
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 1100.dp).padding(horizontal = Spacing.S4)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.S3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S2)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                }
                Text(
                    stringResource(R.string.detail_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                val content = state as? SessionDetailUiState.Content
                if (content != null &&
                    onContinue != null &&
                    content.detail.state == HistoryState.PAUSED &&
                    content.detail.taskId.isNotBlank()
                ) {
                    Button(
                        onClick = { onContinue(content.detail.taskId, content.detail.sessionId) },
                        shape = RoundedCornerShape(Radius.Sm),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.uxc_continue_session), modifier = Modifier.padding(start = Spacing.S2))
                    }
                }
                if (content != null && onOpenReport != null) {
                    OutlinedButton(
                        onClick = onOpenReport,
                        shape = RoundedCornerShape(Radius.Sm),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.uxc_open_report), modifier = Modifier.padding(start = Spacing.S2))
                    }
                }
            }
            when (state) {
                SessionDetailUiState.Loading -> Skeleton(rows = 5)
                SessionDetailUiState.Empty -> TCard {
                    EmptyState(icon = Icons.Filled.Info, message = stringResource(R.string.detail_empty))
                }
                is SessionDetailUiState.Error -> ErrorState(stringResource(R.string.detail_error), onRetry, Modifier.fillMaxWidth())
                is SessionDetailUiState.Content -> {
                    val detail = state.detail
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                        item(key = "header") {
                            TCard {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(detail.taskName, style = MaterialTheme.typography.titleLarge)
                                        MutedText(
                                            stringResource(R.string.sessions_kind_assessment) + " · " + formatDateTime(detail.startedAt)
                                        )
                                    }
                                    HistoryStateChip(detail.state)
                                }
                                if (detail.state != HistoryState.DONE) MutedText(stringResource(R.string.detail_paused_note))
                                if (state.writeFailed) {
                                    Text(
                                        stringResource(R.string.detail_write_failed),
                                        color = ErrorColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        items(detail.steps, key = { it.stepId }) { step ->
                            StepCard(step, saving = state.saving, onCorrect = { correcting = step.stepId }, loadFrame = loadFrame)
                        }
                        item(key = "end") { Row(Modifier.height(Spacing.S4)) {} }
                    }
                    val step = detail.steps.firstOrNull { it.stepId == correcting }
                    if (step != null) {
                        CorrectSheet(
                            step,
                            onSave = { level ->
                                onCorrect(step.stepId, level)
                                correcting = null
                            },
                            onRemove = {
                                onRemoveCorrection(step.stepId)
                                correcting = null
                            },
                            onDismiss = { correcting = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: StepDetail, saving: Boolean, onCorrect: () -> Unit, loadFrame: FrameLoader) {
    var expanded by rememberSaveable(step.stepId) { mutableStateOf(false) }
    TCard(spacing = Spacing.S2) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                step.number?.let { MutedText(stringResource(R.string.detail_step_no, westernDigits(it))) }
                Text(step.text, style = MaterialTheme.typography.titleMedium)
            }
            LevelChip(step.automatic, step.corrected, step.correctedLevel)
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.detail_hide_step else R.string.detail_open_step),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            if (!step.canCorrect) MutedText(stringResource(R.string.detail_open_step_note), Modifier.weight(1f))
            OutlinedButton(
                onClick = onCorrect,
                enabled = step.canCorrect && !saving,
                shape = RoundedCornerShape(Radius.Sm),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.detail_correct), modifier = Modifier.padding(start = Spacing.S2))
            }
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (step.attempts.isEmpty()) {
                MutedText(stringResource(R.string.detail_no_attempts))
            } else {
                step.attempts.forEachIndexed { i, attempt ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AttemptBlock(attempt, loadFrame)
                }
            }
        }
    }
}

@Composable
private fun AttemptBlock(attempt: AttemptEntity, loadFrame: FrameLoader) {
    Column(Modifier.padding(vertical = Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            Text(stringResource(R.string.detail_attempt, westernDigits(attempt.number)), style = MaterialTheme.typography.titleSmall)
            NeutralPill(stringResource(R.string.detail_prompt_level, westernDigits(attempt.promptLevel)))
            if (attempt.helpPressed) NeutralPill(stringResource(R.string.detail_help))
            NeutralPill(stringResource(verdictWord(attempt.verdict)))
            NeutralPill(stringResource(R.string.detail_method, verifierLabel(attempt)))
            NeutralPill(stringResource(R.string.detail_latency, westernDigits(attempt.latencyMs)))
        }
        MutedText(stringResource(R.string.detail_reason, attempt.reason))
        attempt.modelReason?.takeIf { it.isNotBlank() }?.let { MutedText(stringResource(R.string.detail_model_reason, it)) }
        EvidenceStrip(attempt.framePaths, loadFrame)
    }
}

private fun verdictWord(verdict: AttemptVerdict): Int = when (verdict) {
    AttemptVerdict.DONE -> R.string.detail_verdict_done
    AttemptVerdict.NOT_DONE -> R.string.detail_verdict_not_done
    AttemptVerdict.CANNOT_TELL -> R.string.detail_verdict_cannot_tell
}

/** Verifier kind (pose/hand/cloud/none), with the model id when the cloud verifier answered. */
private fun verifierLabel(attempt: AttemptEntity): String {
    val kind = attempt.verifier.name.lowercase()
    return attempt.modelId?.let { "$kind ($it)" } ?: kind
}
