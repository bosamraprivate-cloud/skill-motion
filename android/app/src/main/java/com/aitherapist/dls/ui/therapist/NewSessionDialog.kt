package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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

/** New session for one child: pick a task by area (jacket: on, off or both), then [onStart] opens child mode. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewSessionDialog(
    onStart: (taskId: String, thenTaskId: String?) -> Unit,
    onDismiss: () -> Unit,
    viewModel: NewSessionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = state as? NewSessionUiState.Content
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_session_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (val s = state) {
                    NewSessionUiState.Loading -> Skeleton(rows = 4)
                    is NewSessionUiState.Error -> Text(stringResource(R.string.new_session_error, s.reason))
                    is NewSessionUiState.Content -> {
                        s.byArea.forEach { (area, options) ->
                            Text(s.areaNames[area] ?: area, style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEach { option ->
                                    FilterChip(
                                        selected = option.id == s.selectedId,
                                        onClick = { viewModel.onEvent(NewSessionEvent.Select(option.id)) },
                                        label = { Text(if (option.isPair) "${option.nameAr} / ${option.offNameAr}" else option.nameAr) }
                                    )
                                }
                            }
                        }
                        s.selected?.takeIf { it.isPair }?.let { pair -> PairChoiceRow(pair, s.choice, viewModel::onEvent) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = content?.selected != null,
                onClick = { viewModel.plan()?.let { (first, then) -> onStart(first, then) } }
            ) { Text(stringResource(R.string.new_session_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.new_session_cancel)) } }
    )
}

@Composable
private fun PairChoiceRow(option: TaskOption, choice: PairChoice, onEvent: (NewSessionEvent) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        PairChoice.entries.forEachIndexed { index, c ->
            SegmentedButton(
                selected = choice == c,
                onClick = { onEvent(NewSessionEvent.Choose(c)) },
                shape = SegmentedButtonDefaults.itemShape(index, PairChoice.entries.size)
            ) {
                Text(
                    when (c) {
                        PairChoice.ON -> option.nameAr
                        PairChoice.OFF -> option.offNameAr.orEmpty()
                        PairChoice.BOTH -> stringResource(R.string.new_session_both)
                    }
                )
            }
        }
    }
}
