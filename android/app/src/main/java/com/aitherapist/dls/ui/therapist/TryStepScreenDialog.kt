package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.Skeleton

/** Therapist-only test entry: pick a child and a task, then start locked child mode on it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TryStepScreenDialog(
    onStart: (childId: String, taskId: String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TryStepViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = state as? TryStepUiState.Content
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.try_step_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (val s = state) {
                    TryStepUiState.Loading -> Skeleton(rows = 3)
                    is TryStepUiState.Error -> Text(stringResource(R.string.try_step_error, s.reason))
                    is TryStepUiState.Content -> {
                        Text(stringResource(R.string.try_step_child), style = MaterialTheme.typography.titleMedium)
                        if (s.children.isEmpty()) Text(stringResource(R.string.try_step_no_children))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.children.forEach { child ->
                                FilterChip(
                                    selected = child.id == s.childId,
                                    onClick = { viewModel.onEvent(TryStepEvent.SelectChild(child.id)) },
                                    label = { Text(child.name) }
                                )
                            }
                        }
                        Text(stringResource(R.string.try_step_task), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.taskIds.forEach { id ->
                                FilterChip(
                                    selected = id == s.taskId,
                                    onClick = { viewModel.onEvent(TryStepEvent.SelectTask(id)) },
                                    label = { Text(s.taskNames[id] ?: id) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = content?.canStart == true,
                onClick = {
                    val c = content ?: return@TextButton
                    val child = c.childId ?: return@TextButton
                    val task = c.taskId ?: return@TextButton
                    onStart(child, task)
                }
            ) { Text(stringResource(R.string.try_step_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.try_step_cancel)) } }
    )
}
