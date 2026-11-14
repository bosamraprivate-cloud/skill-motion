package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.aitherapist.dls.R
import com.aitherapist.dls.scoring.StepLevel
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

private val CHOICES = listOf(StepLevel.THREE, StepLevel.TWO, StepLevel.ONE, StepLevel.NOT_SCORED)

/**
 * Correct sheet (Story 5.2): 3 / 2 / 1 / not scored, showing the automatic level; preselects the current
 * effective level (none for an untried step). Save upserts the correction;
 * "Remove correction" (only when one exists) restores the automatic level.
 */
@Composable
fun CorrectSheet(step: StepDetail, onSave: (StepLevel) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var choice by rememberSaveable(step.stepId) { mutableStateOf(step.effective) }
    // Nothing chosen, or re-saving the automatic level as a first correction, would change nothing.
    val canSave = choice != null && !(choice == step.automatic && !step.corrected)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Radius.Lg),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 360.dp, max = 520.dp)
        ) {
            Column(Modifier.padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                Text(
                    stringResource(R.string.correct_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
                Text(step.text, style = MaterialTheme.typography.bodyLarge)
                MutedText(stringResource(R.string.correct_automatic, levelLabel(step.automatic)))
                Column(Modifier.selectableGroup()) {
                    CHOICES.forEach { level ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = choice == level, role = Role.RadioButton) { choice = level },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.S2)
                        ) {
                            RadioButton(selected = choice == level, onClick = null)
                            Text(levelLabel(level), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S2, Alignment.End)) {
                    if (step.corrected) {
                        TextButton(onClick = onRemove, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.correct_remove)) }
                    }
                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(Radius.Sm), modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.correct_cancel))
                    }
                    Button(onClick = {
                        choice?.let(onSave)
                    }, enabled = canSave, shape = RoundedCornerShape(Radius.Sm), modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.correct_save))
                    }
                }
            }
        }
    }
}
