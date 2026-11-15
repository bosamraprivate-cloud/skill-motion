package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PinDots
import com.aitherapist.dls.ui.components.PinPad
import com.aitherapist.dls.ui.components.SideBySideOrStacked
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.ui.theme.isPortrait

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
        val inner = Modifier.fillMaxSize().padding(padding).padding(Spacing.S4)
        when (state) {
            is PinUiState.Content ->
                SideBySideOrStacked(modifier = inner) {
                    // Portrait: title, dots, message and button centred on the same axis as the centred keypad below.
                    val portrait = isPortrait()
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.S3),
                        horizontalAlignment = if (portrait) Alignment.CenterHorizontally else Alignment.Start,
                        modifier = Modifier.widthIn(max = 360.dp)
                    ) {
                        Text(
                            stringResource(R.string.pin_title),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = if (portrait) TextAlign.Center else TextAlign.Start
                        )
                        PinDots(filled = state.entered.length, total = PinRules.MAX_LENGTH)
                        if (state.locked) {
                            // Calm, not alarming: neutral colour, a plain countdown (owner decision 2026-11-14).
                            Text(
                                pluralStringResource(R.plurals.pin_locked_wait, state.lockedSeconds, westernDigits(state.lockedSeconds)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = if (portrait) TextAlign.Center else TextAlign.Start,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        } else if (state.wrong) {
                            Text(
                                stringResource(R.string.pin_wrong),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = if (portrait) TextAlign.Center else TextAlign.Start
                            )
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

@Preview(widthDp = 800, heightDp = 1280)
@Composable
private fun PinScreenPortraitPreview() {
    PinScreenPreview()
}
