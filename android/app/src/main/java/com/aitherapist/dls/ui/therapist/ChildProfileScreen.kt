package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.ui.child.CharacterView
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme

/** Add ([childId] null) or edit a child. [onResult] fires once per save or delete. */
@Composable
fun ChildProfileRoute(
    childId: String?,
    onResult: (ProfileResult) -> Unit,
    viewModel: ChildProfileViewModel = hiltViewModel(
        key =
        childId ?: NEW_CHILD_KEY
    )
) {
    LaunchedEffect(childId) { viewModel.load(childId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val result = (state as? ChildProfileUiState.Content)?.result
    LaunchedEffect(result) {
        result?.let {
            onResult(it)
            viewModel.consumeResult()
        }
    }
    LaunchedEffect(state) { if (state == ChildProfileUiState.Deleted) onResult(ProfileResult.Deleted) }
    ChildProfileScreen(state = state, onEvent = viewModel::onEvent)
}

private const val NEW_CHILD_KEY = "child-profile-new"

@Composable
fun ChildProfileScreen(state: ChildProfileUiState, onEvent: (ChildProfileEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            ChildProfileUiState.Loading -> Skeleton(rows = 5, modifier = Modifier.padding(24.dp))
            is ChildProfileUiState.Error ->
                ErrorState(
                    stringResource(R.string.child_error),
                    { onEvent(ChildProfileEvent.Retry) },
                    Modifier.align(Alignment.Center)
                )
            ChildProfileUiState.Deleted -> Unit
            is ChildProfileUiState.Content -> ChildProfileForm(state, onEvent)
        }
    }
}

@Composable
private fun ChildProfileForm(state: ChildProfileUiState.Content, onEvent: (ChildProfileEvent) -> Unit) {
    val form = state.form
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(if (state.isNew) R.string.child_new_title else R.string.child_edit_title),
            style = MaterialTheme.typography.headlineSmall
        )
        FormField(form.name, R.string.child_name, state.errors.name, KeyboardType.Text) { onEvent(ChildProfileEvent.NameChanged(it)) }
        FormField(form.code, R.string.child_code, state.errors.code, KeyboardType.Text) { onEvent(ChildProfileEvent.CodeChanged(it)) }
        FormField(form.age, R.string.child_age, state.errors.age, KeyboardType.Number) { onEvent(ChildProfileEvent.AgeChanged(it)) }

        Text(stringResource(R.string.child_character), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChildCharacter.entries.forEach { character ->
                CharacterCard(character, form.character == character, Modifier.weight(1f)) {
                    onEvent(ChildProfileEvent.CharacterChanged(character))
                }
            }
        }
        state.errors.character?.let {
            Text(stringResource(R.string.child_error_character), color = MaterialTheme.colorScheme.error)
        }

        Text(stringResource(R.string.child_effect), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            EffectLevel.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = form.effectLevel == level,
                    onClick = { onEvent(ChildProfileEvent.EffectChanged(level)) },
                    shape = SegmentedButtonDefaults.itemShape(index, EffectLevel.entries.size)
                ) { Text(stringResource(effectLabel(level))) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onEvent(ChildProfileEvent.Save) }, enabled = !state.busy) { Text(stringResource(R.string.child_save)) }
            if (state.saved) Text(stringResource(R.string.child_saved))
        }
        state.message?.let {
            Text(
                stringResource(if (it == ProfileMessage.SAVE_FAILED) R.string.child_save_failed else R.string.child_delete_failed),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!state.isNew) {
            OutlinedButton(onClick = { onEvent(ChildProfileEvent.RequestDelete) }, enabled = !state.busy) {
                Text(stringResource(R.string.child_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(ChildProfileEvent.CancelDelete) },
            title = { Text(stringResource(R.string.child_delete_title, form.name.trim())) },
            text = { Text(stringResource(R.string.child_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(ChildProfileEvent.ConfirmDelete) }) {
                    Text(stringResource(R.string.child_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(ChildProfileEvent.CancelDelete) }) { Text(stringResource(R.string.child_delete_cancel)) }
            }
        )
    }
}

@Composable
private fun FormField(value: String, label: Int, error: ChildFieldError?, keyboard: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(errorText(it))) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CharacterCard(character: ChildCharacter, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        border = if (selected) {
            BorderStroke(
                4.dp,
                MaterialTheme.colorScheme.primary
            )
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        colors =
        CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CharacterView(
                clip = characterStillPath(character),
                loop = false,
                effectLevel = EffectLevel.OFF,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
            Text(
                stringResource(if (character == ChildCharacter.BOY) R.string.child_character_boy else R.string.child_character_girl),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun effectLabel(level: EffectLevel): Int = when (level) {
    EffectLevel.FULL -> R.string.child_effect_full
    EffectLevel.CALM -> R.string.child_effect_calm
    EffectLevel.OFF -> R.string.child_effect_off
}

private fun errorText(error: ChildFieldError): Int = when (error) {
    ChildFieldError.REQUIRED -> R.string.child_error_required
    ChildFieldError.CODE_IN_USE -> R.string.child_error_code_in_use
    ChildFieldError.AGE_RANGE -> R.string.child_error_age
    ChildFieldError.INVALID -> R.string.child_error_code_chars
    ChildFieldError.TOO_LONG -> R.string.child_error_too_long
}

@Preview(device = Devices.TABLET)
@Composable
private fun ChildProfilePreview() {
    DlsTheme {
        ChildProfileScreen(
            state =
            ChildProfileUiState.Content(
                childId = null,
                form = ChildForm(name = "Mariam", code = "A1", age = "x"),
                errors = ChildFormErrors(age = ChildFieldError.AGE_RANGE, character = ChildFieldError.REQUIRED)
            ),
            onEvent = {}
        )
    }
}
