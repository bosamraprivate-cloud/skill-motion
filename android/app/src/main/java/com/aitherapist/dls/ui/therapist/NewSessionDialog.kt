package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.ui.child.CharacterView
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import java.util.Locale

/**
 * New session for one child: pick a task tile by area (jacket: on, off or both), then [onStart] opens child mode.
 * Each tile shows the first step's still for [character]'s variant, or the character's idle still when it is missing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewSessionDialog(
    onStart: (taskId: String, thenTaskId: String?) -> Unit,
    onDismiss: () -> Unit,
    character: ChildCharacter? = null,
    viewModel: NewSessionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = state as? NewSessionUiState.Content
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(Radius.Lg),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(Spacing.S5).widthIn(max = DIALOG_MAX_WIDTH),
        title = { Text(stringResource(R.string.new_session_title), style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.S3)
            ) {
                when (val s = state) {
                    NewSessionUiState.Loading -> Skeleton(rows = 4)
                    is NewSessionUiState.Error -> Text(stringResource(R.string.new_session_error, s.reason))
                    is NewSessionUiState.Content -> {
                        s.byArea.forEach { (area, options) ->
                            Text(
                                s.areaNames[area] ?: area,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = Spacing.S2)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.S3),
                                verticalArrangement = Arrangement.spacedBy(Spacing.S3)
                            ) {
                                options.forEach { option ->
                                    TaskTile(option, character, option.id == s.selectedId) {
                                        viewModel.onEvent(NewSessionEvent.Select(option.id))
                                    }
                                }
                            }
                        }
                        s.selected?.takeIf { it.isPair }?.let { pair -> PairChoiceRow(pair, s.choice, viewModel::onEvent) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = content?.selected != null,
                shape = RoundedCornerShape(Radius.Sm),
                modifier = Modifier.height(48.dp).widthIn(min = 120.dp),
                onClick = { viewModel.plan()?.let { (first, then) -> onStart(first, then) } }
            ) { Text(stringResource(R.string.new_session_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.new_session_cancel)) }
        }
    )
}

@Composable
private fun TaskTile(option: TaskOption, character: ChildCharacter?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val variant = character?.name?.lowercase(Locale.ROOT)
    val picture = variant?.let { option.pictures[it] }
    Card(
        modifier = Modifier.width(TILE_WIDTH).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(Radius.Md),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.primaryContainer else colors.surface)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TILE_PICTURE_HEIGHT)
                .padding(Spacing.S1)
                .background(ChildColors.ClipBackdrop, RoundedCornerShape(Radius.Sm))
        ) {
            CharacterView(
                clip = if (picture == null) character?.let(::characterStillPath) else null,
                still = picture,
                loop = false,
                stillMode = true,
                modifier = Modifier.fillMaxWidth().height(TILE_PICTURE_HEIGHT).padding(Spacing.S1)
            )
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.S1).background(colors.surface, CircleShape).size(24.dp)
                )
            }
        }
        Text(
            if (option.isPair) "${option.nameAr} / ${option.offNameAr}" else option.nameAr,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S2)
        )
    }
}

@Composable
private fun PairChoiceRow(option: TaskOption, choice: PairChoice, onEvent: (NewSessionEvent) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.S2)) {
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

private val DIALOG_MAX_WIDTH = 880.dp
private val TILE_WIDTH = 168.dp
private val TILE_PICTURE_HEIGHT = 120.dp
