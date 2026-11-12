package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.ui.child.CharacterView
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme

@Composable
fun ChildListRoute(selectedId: String?, onSelect: (String) -> Unit, onAdd: () -> Unit, viewModel: ChildListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChildListScreen(
        state = state,
        selectedId = selectedId,
        onQueryChange = viewModel::onQueryChange,
        onSelect = onSelect,
        onAdd = onAdd,
        onRetry = viewModel::retry
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
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            ChildListUiState.Loading -> Skeleton(rows = 4, modifier = Modifier.padding(24.dp))
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
            is ChildListUiState.Content -> ChildListContent(state, selectedId, onQueryChange, onSelect, onAdd)
        }
    }
}

@Composable
private fun ChildListContent(
    state: ChildListUiState.Content,
    selectedId: String?,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.children_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.children_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.children.isEmpty()) {
            Text(stringResource(R.string.children_no_match), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.children, key = { it.id }) { row -> ChildListRow(row, row.id == selectedId) { onSelect(row.id) } }
        }
    }
}

@Composable
private fun ChildListRow(row: ChildRow, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(row.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(stringResource(R.string.child_row_details, row.ageYears)) },
        leadingContent = {
            CharacterView(
                clip = characterStillPath(row.character),
                loop = false,
                stillMode = true,
                modifier = Modifier.size(56.dp)
            )
        },
        colors =
        ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

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
                )
            ),
            selectedId = "1",
            onQueryChange = {},
            onSelect = {},
            onAdd = {},
            onRetry = {}
        )
    }
}
