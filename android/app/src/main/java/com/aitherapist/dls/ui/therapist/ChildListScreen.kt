package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.ui.child.CharacterView
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

@Composable
fun ChildListRoute(
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onContinue: (childId: String, taskId: String, sessionId: String) -> Unit = { _, _, _ -> },
    viewModel: ChildListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChildListScreen(
        state = state,
        selectedId = selectedId,
        onQueryChange = viewModel::onQueryChange,
        onSelect = onSelect,
        onAdd = onAdd,
        onRetry = viewModel::retry,
        onContinue = { row -> onContinue(row.childId, row.taskId, row.sessionId) }
    )
}

@Composable
fun ChildListScreen(
    state: ChildListUiState,
    selectedId: String?,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onContinue: (ContinueRow) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        PageHeader(stringResource(R.string.shell_children)) {
            if (state is ChildListUiState.Content) {
                Button(onClick = onAdd, shape = RoundedCornerShape(Radius.Sm), modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.children_add), modifier = Modifier.padding(start = Spacing.S2))
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                ChildListUiState.Loading -> Skeleton(rows = 4)
                ChildListUiState.Empty ->
                    EmptyState(
                        icon = Icons.Filled.Face,
                        message = stringResource(R.string.children_empty),
                        primaryActionLabel = stringResource(R.string.children_add),
                        onPrimaryAction = onAdd,
                        modifier = Modifier.align(Alignment.Center)
                    )
                is ChildListUiState.Error ->
                    ErrorState(stringResource(R.string.children_error), onRetry, Modifier.align(Alignment.Center))
                is ChildListUiState.Content -> ChildListContent(state, selectedId, onQueryChange, onSelect, onContinue)
            }
        }
    }
}

@Composable
private fun ChildListContent(
    state: ChildListUiState.Content,
    selectedId: String?,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onContinue: (ContinueRow) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.children_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.Sm),
                colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.S2)
            )
        }
        items(state.continueRows, key = { "continue-${it.sessionId}" }) { row -> ContinueCard(row) { onContinue(row) } }
        if (state.continueRows.isNotEmpty()) item(key = "gap") { Spacer(Modifier.height(Spacing.S2)) }
        if (state.children.isEmpty()) {
            item(key = "no-match") {
                Text(
                    stringResource(R.string.children_no_match),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.S3)
                )
            }
        }
        items(state.children, key = { it.id }) { row -> ChildListRow(row, row.id == selectedId) { onSelect(row.id) } }
    }
}

/** Child row card: avatar in a circle, name, age (Western digits). Selected: soft primary tint and primary outline. */
@Composable
private fun ChildListRow(row: ChildRow, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.Md),
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.primaryContainer else colors.surface),
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(Spacing.S3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
        ) {
            CharacterAvatar(row.character, size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.child_row_details, westernDigits(row.ageYears)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

/** "Continue" card for an interrupted session: child name and task only (never levels or results). */
@Composable
private fun ContinueCard(row: ContinueRow, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.Md),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(Spacing.S3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
        ) {
            Box(Modifier.size(48.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.children_continue_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.children_continue_item, row.childName, row.taskName),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(onClick = onClick, shape = RoundedCornerShape(Radius.Sm), modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.children_continue))
            }
        }
    }
}

/** The child's character still in a circle on the clip backdrop. */
@Composable
fun CharacterAvatar(character: ChildCharacter, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(ChildColors.ClipBackdrop),
        contentAlignment = Alignment.TopCenter
    ) {
        // The idle still is a full-body portrait: scale it up and anchor near the top so the circle shows the face.
        CharacterView(
            clip = characterStillPath(character),
            loop = false,
            stillMode = true,
            modifier = Modifier.requiredSize(size * AVATAR_ZOOM).offset(y = size * AVATAR_TOP)
        )
    }
}

/** Ages and counts in therapist data use Western digits (DESIGN typography), whatever the locale. */
fun westernDigits(value: Int): String = value.toString()

private const val AVATAR_ZOOM = 2.6f
private const val AVATAR_TOP = 0.75f

/** The character's idle clip; in still mode [CharacterView] shows its first frame, the picture the child will see. */
fun characterStillPath(character: ChildCharacter): String = "character/${character.name.lowercase()}/react/idle.webp"

@Preview(device = Devices.TABLET)
@Composable
private fun ChildListPreview() {
    DlsTheme {
        ChildListScreen(
            state =
            ChildListUiState.Content(
                query = "",
                children = listOf(
                    ChildRow("1", "Youssef", 6, ChildCharacter.BOY),
                    ChildRow("2", "Mariam", 8, ChildCharacter.GIRL)
                ),
                continueRows = listOf(ContinueRow("s1", "1", "Youssef", "task"))
            ),
            selectedId = "1",
            onQueryChange = {},
            onSelect = {},
            onAdd = {},
            onRetry = {}
        )
    }
}
