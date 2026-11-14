package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PinDots
import com.aitherapist.dls.ui.components.PinPad
import com.aitherapist.dls.ui.theme.DlsTheme

/** Stateful entry: the PIN gate; calls [onUnlocked] after a right PIN. */
@Composable
fun PinRoute(onUnlocked: () -> Unit, viewModel: PinViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == PinUiState.Unlocked) onUnlocked() }
    PinScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun PinScreen(state: PinUiState, onEvent: (PinEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        val inner = Modifier.fillMaxSize().padding(padding).padding(24.dp)
        when (state) {
            is PinUiState.Content ->
                Row(modifier = inner, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 360.dp)) {
                        Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.headlineMedium)
                        PinDots(filled = state.entered.length, total = PinRules.MAX_LENGTH)
                        if (state.locked) {
                            // Calm, not alarming: neutral colour, a plain countdown (owner decision 2026-11-14).
                            Text(
                                pluralStringResource(R.plurals.pin_locked_wait, state.lockedSeconds, state.lockedSeconds),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        } else if (state.wrong) {
                            Text(stringResource(R.string.pin_wrong), color = MaterialTheme.colorScheme.error)
                        }
                        Button(onClick = { onEvent(PinEvent.Submit) }, enabled = state.canSubmit) {
                            Text(stringResource(R.string.pin_unlock))
                        }
                    }
                    PinPad(
                        onDigit = { onEvent(PinEvent.Digit(it)) },
                        onDelete = { onEvent(PinEvent.Delete) },
                        enabled = !state.checking && !state.locked
                    )
                }
            PinUiState.Unlocked -> Unit
            is PinUiState.Error ->
                ErrorState(stringResource(R.string.pin_error, state.reason), onRetry = { onEvent(PinEvent.Retry) }, modifier = inner)
        }
    }
}

@Preview(device = Devices.TABLET)
@Composable
private fun PinScreenPreview() {
    DlsTheme { PinScreen(PinUiState.Content(entered = "12", wrong = true), {}) }
}
