package com.aitherapist.dls.ui.sample

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme

/** Stateful entry: wires the ViewModel to the stateless screen. */
@Composable
fun SampleRoute(viewModel: SampleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SampleScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun SampleScreen(state: SampleUiState, onEvent: (SampleEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.sample_title), style = MaterialTheme.typography.headlineMedium)
            Crossfade(targetState = state, label = "sampleState") { current ->
                when (current) {
                    SampleUiState.Loading -> Skeleton(rows = 3)
                    SampleUiState.Empty ->
                        EmptyState(
                            icon = Icons.Filled.Info,
                            message = stringResource(R.string.sample_empty_message),
                            primaryActionLabel = stringResource(R.string.sample_empty_action),
                            onPrimaryAction = { onEvent(SampleEvent.Retry) }
                        )
                    is SampleUiState.Error ->
                        ErrorState(
                            message = stringResource(R.string.sample_error_message),
                            onRetry = { onEvent(SampleEvent.Retry) }
                        )
                    is SampleUiState.Content ->
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(current.items) { item ->
                                Card { Text(item, modifier = Modifier.padding(16.dp)) }
                            }
                        }
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun SampleScreenLoadingPreview() {
    DlsTheme { SampleScreen(state = SampleUiState.Loading, onEvent = {}) }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun SampleScreenEmptyPreview() {
    DlsTheme { SampleScreen(state = SampleUiState.Empty, onEvent = {}) }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun SampleScreenContentPreview() {
    DlsTheme { SampleScreen(state = SampleUiState.Content(listOf("Item 1", "Item 2")), onEvent = {}) }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun SampleScreenErrorPreview() {
    DlsTheme { SampleScreen(state = SampleUiState.Error("IOException"), onEvent = {}) }
}
