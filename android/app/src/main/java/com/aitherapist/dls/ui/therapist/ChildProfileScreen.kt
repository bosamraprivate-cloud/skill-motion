package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.ui.child.CharacterView
import com.aitherapist.dls.ui.child.StickerBookRoute
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/** Add ([childId] null) or edit a child. [onResult] fires once per save or delete. */
@Composable
fun ChildProfileRoute(
    childId: String?,
    onResult: (ProfileResult) -> Unit,
    onNewSession: (taskId: String, thenTaskId: String?) -> Unit = { _, _ -> },
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean = { _, _ -> false },
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
    ChildProfileScreen(state = state, onEvent = viewModel::onEvent, onNewSession = onNewSession, onStartTraining = onStartTraining)
}

private const val NEW_CHILD_KEY = "child-profile-new"

@Composable
fun ChildProfileScreen(
    state: ChildProfileUiState,
    onEvent: (ChildProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
    onNewSession: (taskId: String, thenTaskId: String?) -> Unit = { _, _ -> },
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean = { _, _ -> false }
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            ChildProfileUiState.Loading -> Skeleton(rows = 5, modifier = Modifier.padding(Spacing.S4))
            is ChildProfileUiState.Error ->
                ErrorState(
                    stringResource(R.string.child_error),
                    { onEvent(ChildProfileEvent.Retry) },
                    Modifier.align(Alignment.Center)
                )
            ChildProfileUiState.Deleted -> Unit
            is ChildProfileUiState.Content -> ChildProfileForm(state, onEvent, onNewSession, onStartTraining)
        }
    }
}

@Composable
private fun ChildProfileForm(
    state: ChildProfileUiState.Content,
    onEvent: (ChildProfileEvent) -> Unit,
    onNewSession: (taskId: String, thenTaskId: String?) -> Unit,
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean
) {
    val form = state.form
    var newSessionOpen by rememberSaveable { mutableStateOf(false) }
    var stickerBookOpen by rememberSaveable { mutableStateOf(false) }
    var newSessionCount by rememberSaveable { mutableStateOf(0) }
    // View mode by default for a saved child; a new child is always the form. A successful save returns to view mode.
    var editing by rememberSaveable(state.childId) { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) editing = false }
    val showForm = state.isNew || editing
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.S4).widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.S3)
    ) {
        PageHeader(stringResource(if (state.isNew) R.string.child_new_title else R.string.child_edit_title)) {
            if (!showForm) {
                OutlinedButton(
                    onClick = { editing = true },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(Radius.Sm),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.child_edit), modifier = Modifier.padding(start = Spacing.S2))
                }
            }
        }
        if (showForm) {
            ProfileEditCard(state, onEvent)
        } else {
            var tab by rememberSaveable(state.childId) { mutableStateOf(ProfileTab.OVERVIEW) }
            ProfileTabs(tab) { tab = it }
            if (tab == ProfileTab.OVERVIEW) {
                ProfileHeaderCard(form, enabled = !state.busy) {
                    newSessionCount++
                    newSessionOpen = true
                }
                state.message?.let { MessageText(it) }
                if (state.childId != null) {
                    OutlinedButton(onClick = { stickerBookOpen = true }, shape = RoundedCornerShape(Radius.Sm)) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.profile_sticker_book), modifier = Modifier.padding(start = Spacing.S2))
                    }
                }
            } else if (tab == ProfileTab.REPORT && state.childId != null) {
                ReportRoute(state.childId)
            } else if (tab == ProfileTab.PLAN && state.childId != null) {
                PlanRoute(state.childId, onStartTraining)
            } else {
                OutlinedSurfaceCard {
                    EmptyState(icon = tab.icon, message = stringResource(R.string.profile_tab_later, stringResource(tab.label)))
                }
            }
        }
    }
    val bookChild = state.childId
    if (stickerBookOpen && bookChild != null) {
        Dialog(onDismissRequest = { stickerBookOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            StickerBookRoute(bookChild, onBack = { stickerBookOpen = false })
        }
    }
    if (newSessionOpen) {
        NewSessionDialog(
            character = form.character,
            // Keyed per child and per opening: every dialog starts with a fresh selection.
            viewModel = hiltViewModel(key = "new-session-${state.childId}-$newSessionCount"),
            onStart = { task, then ->
                newSessionOpen = false
                onNewSession(task, then)
            },
            onDismiss = { newSessionOpen = false }
        )
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

/** Agreed child-profile tabs (EXPERIENCE.md, therapist IA); only Overview has content until later epics. */
private enum class ProfileTab(val label: Int, val icon: ImageVector) {
    OVERVIEW(R.string.profile_tab_overview, Icons.Filled.Face),
    SESSIONS(R.string.profile_tab_sessions, Icons.AutoMirrored.Filled.List),
    REPORT(R.string.profile_tab_report, Icons.Filled.Info),
    PLAN(R.string.profile_tab_plan, Icons.Filled.DateRange),
    PROGRESS(R.string.profile_tab_progress, Icons.Filled.Star)
}

@Composable
private fun ProfileTabs(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selected.ordinal,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        ProfileTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** View mode: avatar, name, age, character and effect level, with the prominent "Start session" action. */
@Composable
private fun ProfileHeaderCard(form: ChildForm, enabled: Boolean, onStartSession: () -> Unit) {
    OutlinedSurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
            form.character?.let { CharacterAvatar(it, size = 112.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                Text(form.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                form.age.trim().toIntOrNull()?.let { age ->
                    Text(
                        stringResource(R.string.child_row_details, westernDigits(age)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(Modifier.padding(top = Spacing.S2), horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                    form.character?.let { InfoChip(stringResource(R.string.child_character), stringResource(characterLabel(it))) }
                    InfoChip(stringResource(R.string.child_effect), stringResource(effectLabel(form.effectLevel)))
                }
            }
        }
        Button(
            onClick = onStartSession,
            enabled = enabled,
            shape = RoundedCornerShape(Radius.Sm),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text(
                stringResource(R.string.new_session),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Spacing.S2)
            )
        }
    }
}

/** Label and value pill (display only), neutral so colour stays reserved for levels. */
@Composable
private fun InfoChip(label: String, value: String) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.Sm))
            .padding(horizontal = Spacing.S3, vertical = Spacing.S1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S1)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

/** Edit mode (and the add form): fields, character picker, effect level, Save; Delete for a saved child. */
@Composable
private fun ProfileEditCard(state: ChildProfileUiState.Content, onEvent: (ChildProfileEvent) -> Unit) {
    val form = state.form
    OutlinedSurfaceCard {
        FormField(form.name, R.string.child_name, state.errors.name, KeyboardType.Text) { onEvent(ChildProfileEvent.NameChanged(it)) }
        FormField(form.age, R.string.child_age, state.errors.age, KeyboardType.Number) { onEvent(ChildProfileEvent.AgeChanged(it)) }

        SectionLabel(stringResource(R.string.child_character))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            ChildCharacter.entries.forEach { character ->
                CharacterCard(character, form.character == character, Modifier.width(168.dp)) {
                    onEvent(ChildProfileEvent.CharacterChanged(character))
                }
            }
        }
        state.errors.character?.let {
            Text(stringResource(R.string.child_error_character), color = MaterialTheme.colorScheme.error)
        }

        SectionLabel(stringResource(R.string.child_effect))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth()) {
            EffectLevel.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = form.effectLevel == level,
                    onClick = { onEvent(ChildProfileEvent.EffectChanged(level)) },
                    shape = SegmentedButtonDefaults.itemShape(index, EffectLevel.entries.size)
                ) { Text(stringResource(effectLabel(level))) }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = Spacing.S2), color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onEvent(ChildProfileEvent.Save) },
                enabled = !state.busy,
                shape = RoundedCornerShape(Radius.Sm),
                modifier = Modifier.height(48.dp).widthIn(min = 120.dp)
            ) { Text(stringResource(R.string.child_save)) }
            if (state.saved) Text(stringResource(R.string.child_saved), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (!state.isNew) {
                TextButton(onClick = { onEvent(ChildProfileEvent.RequestDelete) }, enabled = !state.busy) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.child_delete),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = Spacing.S2)
                    )
                }
            }
        }
        state.message?.let { MessageText(it) }
    }
}

@Composable
private fun MessageText(message: ProfileMessage) {
    Text(
        stringResource(if (message == ProfileMessage.SAVE_FAILED) R.string.child_save_failed else R.string.child_delete_failed),
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.S2))
}

/** t-card: white surface, 16dp corners, 1dp t-border outline, no shadow. */
@Composable
private fun OutlinedSurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(Radius.Md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S3), content = content)
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
        shape = RoundedCornerShape(Radius.Sm),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CharacterCard(character: ChildCharacter, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(Radius.Md),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.primaryContainer else colors.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.S2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.S1)
        ) {
            CharacterView(
                clip = characterStillPath(character),
                loop = false,
                stillMode = true,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(stringResource(characterLabel(character)), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun characterLabel(character: ChildCharacter): Int =
    if (character == ChildCharacter.BOY) R.string.child_character_boy else R.string.child_character_girl

private fun effectLabel(level: EffectLevel): Int = when (level) {
    EffectLevel.FULL -> R.string.child_effect_full
    EffectLevel.CALM -> R.string.child_effect_calm
}

private fun errorText(error: ChildFieldError): Int = when (error) {
    ChildFieldError.REQUIRED -> R.string.child_error_required
    ChildFieldError.AGE_RANGE -> R.string.child_error_age
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
                form = ChildForm(name = "Mariam", age = "x"),
                errors = ChildFormErrors(age = ChildFieldError.AGE_RANGE, character = ChildFieldError.REQUIRED)
            ),
            onEvent = {}
        )
    }
}
