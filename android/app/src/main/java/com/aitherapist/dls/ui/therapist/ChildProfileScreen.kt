package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Switch
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
import com.aitherapist.dls.rewards.Stickers
import com.aitherapist.dls.ui.child.CharacterView
import com.aitherapist.dls.ui.child.StickerArt
import com.aitherapist.dls.ui.child.StickerBookRoute
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

/** A request from outside the profile to show [tab] of [childId] (e.g. after the session summary); [nonce] repeats it. */
data class ProfileTabRequest(val childId: String, val tab: ProfileTab, val nonce: Long = System.nanoTime())

/** Add ([childId] null) or edit a child. [onResult] fires once per save or delete. */
@Composable
fun ChildProfileRoute(
    childId: String?,
    onResult: (ProfileResult) -> Unit,
    onNewSession: (taskId: String, thenTaskId: String?) -> Unit = { _, _ -> },
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean = { _, _ -> false },
    tabRequest: ProfileTabRequest? = null,
    onContinue: (taskId: String, sessionId: String) -> Unit = { _, _ -> },
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
    ChildProfileScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onNewSession = onNewSession,
        onStartTraining = onStartTraining,
        tabRequest = tabRequest,
        onContinue = onContinue
    )
}

private const val NEW_CHILD_KEY = "child-profile-new"

@Composable
fun ChildProfileScreen(
    state: ChildProfileUiState,
    onEvent: (ChildProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
    onNewSession: (taskId: String, thenTaskId: String?) -> Unit = { _, _ -> },
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean = { _, _ -> false },
    tabRequest: ProfileTabRequest? = null,
    onContinue: (taskId: String, sessionId: String) -> Unit = { _, _ -> }
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
            is ChildProfileUiState.Content -> ChildProfileForm(state, onEvent, onNewSession, onStartTraining, tabRequest, onContinue)
        }
    }
}

@Composable
private fun ChildProfileForm(
    state: ChildProfileUiState.Content,
    onEvent: (ChildProfileEvent) -> Unit,
    onNewSession: (taskId: String, thenTaskId: String?) -> Unit,
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean,
    tabRequest: ProfileTabRequest?,
    onContinue: (taskId: String, sessionId: String) -> Unit
) {
    val form = state.form
    var newSessionOpen by rememberSaveable { mutableStateOf(false) }
    var stickerBookOpen by rememberSaveable { mutableStateOf(false) }
    var newSessionCount by rememberSaveable { mutableStateOf(0) }
    var summarySession by rememberSaveable(state.childId) { mutableStateOf<String?>(null) }
    // View mode by default for a saved child; a new child is always the form. A successful save returns to view mode.
    var editing by rememberSaveable(state.childId) { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) editing = false }
    val showForm = state.isNew || editing
    var tab by rememberSaveable(state.childId) { mutableStateOf(ProfileTab.OVERVIEW) }
    LaunchedEffect(tabRequest) {
        if (tabRequest != null && tabRequest.childId == state.childId) {
            editing = false
            tab = tabRequest.tab
        }
    }
    val startSession: () -> Unit = {
        newSessionCount++
        newSessionOpen = true
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.S4).widthIn(max = 880.dp)) {
        PageHeader(
            stringResource(if (state.isNew) R.string.child_new_title else R.string.child_edit_title),
            modifier = Modifier.padding(top = Spacing.S3)
        ) {
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
        val childId = state.childId
        if (showForm || childId == null) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = Spacing.S3),
                verticalArrangement = Arrangement.spacedBy(Spacing.S3)
            ) { ProfileEditCard(state, onEvent) }
        } else {
            ProfileTabs(tab) { tab = it }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    ProfileTab.PLAN -> PlanRoute(childId, onStartTraining, onStartSession = startSession)
                    else ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Spacing.S4),
                            verticalArrangement = Arrangement.spacedBy(Spacing.S4)
                        ) {
                            when (tab) {
                                ProfileTab.OVERVIEW -> {
                                    val statsVm: SessionsViewModel = hiltViewModel(key = "sessions-$childId")
                                    val statsState by statsVm.uiState.collectAsStateWithLifecycle()
                                    ProfileHeaderCard(
                                        form,
                                        stickers = (statsState as? SessionsUiState.Content)?.stats?.stickers,
                                        enabled = !state.busy,
                                        onStartSession = startSession,
                                        onStickerBook = { stickerBookOpen = true }
                                    )
                                    state.message?.let { MessageText(it) }
                                    OverviewStatsSection(
                                        childId,
                                        onStickerBook = { stickerBookOpen = true },
                                        onOpenSession = { summarySession = it },
                                        onStartSession = startSession
                                    )
                                }
                                ProfileTab.SESSIONS -> {
                                    val vm: SessionsViewModel = hiltViewModel(key = "sessions-$childId")
                                    LaunchedEffect(childId) { vm.load(childId) }
                                    val sessions by vm.uiState.collectAsStateWithLifecycle()
                                    SessionsScreen(
                                        sessions,
                                        onRetry = vm::retry,
                                        onOpenSummary = { summarySession = it },
                                        onStartSession = startSession
                                    )
                                }
                                ProfileTab.REPORT -> ReportRoute(childId, onStartSession = startSession)
                                ProfileTab.PROGRESS -> ProgressRoute(childId, onStartSession = startSession)
                                else ->
                                    TCard {
                                        EmptyState(
                                            icon = tab.icon,
                                            message = stringResource(R.string.profile_tab_later, stringResource(tab.label))
                                        )
                                    }
                            }
                        }
                }
            }
        }
    }
    val detailChild = state.childId
    summarySession?.let { sessionId ->
        if (detailChild != null) {
            SessionDetailDialog(
                childId = detailChild,
                sessionId = sessionId,
                onDismiss = { summarySession = null },
                onOpenReport = {
                    summarySession = null
                    editing = false
                    tab = ProfileTab.REPORT
                },
                onContinue = { taskId, id ->
                    summarySession = null
                    onContinue(taskId, id)
                }
            )
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

/** Overview quick stats (last assessment, tasks finished, stickers) and the sticker book entry. */
@Composable
private fun OverviewStatsSection(
    childId: String,
    onStickerBook: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    onStartSession: () -> Unit
) {
    val vm: SessionsViewModel = hiltViewModel(key = "sessions-$childId")
    LaunchedEffect(childId) { vm.load(childId) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val content = state as? SessionsUiState.Content
    val stats = content?.stats
    // Newest finished assessment: the "last assessment" tile opens its session detail (UX pass C).
    val lastSession = content?.items?.firstOrNull { it.kind == HistoryKind.ASSESSMENT && it.state == HistoryState.DONE }
    val noSessions = content != null && content.items.isEmpty()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        StatTile(
            Icons.Filled.DateRange,
            stringResource(R.string.overview_last_assessment),
            stats?.let { s -> s.lastAssessmentAt?.let { formatDate(it) } ?: stringResource(R.string.overview_none_yet) },
            Modifier.weight(1f).fillMaxHeight(),
            action = when {
                lastSession != null -> stringResource(R.string.uxc_open_session)
                noSessions -> stringResource(R.string.new_session)
                else -> null
            },
            onAction = {
                if (lastSession != null) onOpenSession(lastSession.id) else onStartSession()
            }
        )
        StatTile(
            Icons.Filled.CheckCircle,
            stringResource(R.string.overview_tasks_finished),
            stats?.let { westernDigits(it.finishedTasks) },
            Modifier.weight(1f).fillMaxHeight()
        )
        StatTile(
            Icons.Filled.Star,
            stringResource(R.string.overview_stickers),
            stats?.let { westernDigits(it.stickers) },
            Modifier.weight(1f).fillMaxHeight(),
            action = stringResource(R.string.profile_sticker_book),
            onAction = onStickerBook
        )
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String?,
    modifier: Modifier,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    TCard(modifier = modifier, spacing = Spacing.S2) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.Sm)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Placeholder dash while loading keeps the tile height stable.
        Text(value ?: "—", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(action) }
        }
    }
}

/** Agreed child-profile tabs (EXPERIENCE.md, therapist IA); Progress fills in Epic 5. */
enum class ProfileTab(val label: Int, val icon: ImageVector) {
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

/**
 * View mode: avatar, name with the boy/girl mark, age and "Start session" on one side; the child's sticker cabinet
 * (earned stickers in colour, the rest dimmed) on the other. Effect level and time ring live in the edit form.
 */
@Composable
private fun ProfileHeaderCard(form: ChildForm, stickers: Int?, enabled: Boolean, onStartSession: () -> Unit, onStickerBook: () -> Unit) {
    TCard {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.S5)) {
            Column(Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
                    form.character?.let { CharacterAvatar(it, size = 96.dp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                            Text(
                                form.name,
                                style = MaterialTheme.typography.headlineMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            form.character?.let { CharacterMark(it) }
                        }
                        form.age.trim().toIntOrNull()?.let { age ->
                            Text(
                                stringResource(R.string.child_row_details, westernDigits(age)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Button(
                    onClick = onStartSession,
                    enabled = enabled,
                    shape = RoundedCornerShape(Radius.Sm),
                    modifier = Modifier.padding(top = Spacing.S4).fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        stringResource(R.string.new_session),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Spacing.S2)
                    )
                }
            }
            StickerCabinet(stickers ?: 0, Modifier.weight(0.6f), onStickerBook)
        }
    }
}

/** Small boy/girl mark beside the name: icon plus the word, never colour alone. */
@Composable
private fun CharacterMark(character: ChildCharacter) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(Radius.Sm))
            .padding(horizontal = Spacing.S2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S1)
    ) {
        Icon(Icons.Filled.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            stringResource(characterLabel(character)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** The child's sticker cabinet: every slot; earned stickers open in colour, the rest stay dimmed. */
@Composable
private fun StickerCabinet(earned: Int, modifier: Modifier, onStickerBook: () -> Unit) {
    val slots = maxOf(Stickers.BOOK_SLOTS, earned)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.overview_stickers), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${westernDigits(earned)} / ${westernDigits(slots)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onStickerBook) { Text(stringResource(R.string.profile_sticker_book)) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(Radius.Sm))
                .padding(Spacing.S2),
            verticalArrangement = Arrangement.spacedBy(Spacing.S1)
        ) {
            (0 until slots).chunked(CABINET_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                    row.forEach { index ->
                        StickerArt.Sticker(Stickers.idFor(index), earned = index < earned, modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                    repeat(CABINET_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val CABINET_COLUMNS = 10

/** Edit mode (and the add form): fields, character picker, effect level, Save; Delete for a saved child. */
@Composable
private fun ProfileEditCard(state: ChildProfileUiState.Content, onEvent: (ChildProfileEvent) -> Unit) {
    val form = state.form
    TCard {
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

        // Story 6.2: per-child time ring switch (the whole row toggles; the step time still closes the window when off).
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = form.showTimeRing,
                    role = Role.Switch,
                    enabled = !state.busy,
                    onValueChange = { onEvent(ChildProfileEvent.TimeRingChanged(it)) }
                ).padding(vertical = Spacing.S2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.child_time_ring), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.child_time_ring_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = form.showTimeRing, onCheckedChange = null, enabled = !state.busy)
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
