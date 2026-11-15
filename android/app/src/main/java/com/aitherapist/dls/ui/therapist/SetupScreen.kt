package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

/** Stateful entry: first-run setup; calls [onFinished] once everything is saved. */
@Composable
fun SetupRoute(onFinished: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == SetupUiState.Done) onFinished() }
    SetupScreen(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@Composable
fun SetupScreen(state: SetupUiState, onEvent: (SetupEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        val inner = Modifier.fillMaxSize().padding(padding).padding(Spacing.S4)
        when (state) {
            is SetupUiState.Content ->
                when (state.step) {
                    SetupStep.WELCOME -> WelcomeStep(onEvent, inner)
                    SetupStep.PIN, SetupStep.CONFIRM_PIN -> PinStep(state, onEvent, inner)
                    SetupStep.PROXY -> ProxyStep(state, onEvent, inner)
                }
            SetupUiState.Done -> Unit
            is SetupUiState.Error ->
                ErrorState(stringResource(R.string.setup_error, state.reason), onRetry = { onEvent(SetupEvent.Retry) }, modifier = inner)
        }
    }
}

@Composable
private fun WelcomeStep(onEvent: (SetupEvent) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Text(stringResource(R.string.setup_welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.setup_welcome_body), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { onEvent(SetupEvent.Continue) }) { Text(stringResource(R.string.setup_start)) }
    }
}

@Composable
private fun PinStep(state: SetupUiState.Content, onEvent: (SetupEvent) -> Unit, modifier: Modifier) {
    val confirm = state.step == SetupStep.CONFIRM_PIN
    val value = if (confirm) state.confirmPin else state.pin
    SideBySideOrStacked(modifier = modifier) {
        // Portrait: title, dots and buttons centred on the same axis as the centred keypad below.
        val portrait = isPortrait()
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.S3),
            horizontalAlignment = if (portrait) Alignment.CenterHorizontally else Alignment.Start,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Text(
                stringResource(if (confirm) R.string.setup_confirm_title else R.string.setup_pin_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = if (portrait) TextAlign.Center else TextAlign.Start
            )
            PinDots(filled = value.length, total = PinRules.MAX_LENGTH)
            if (state.pinMismatch) {
                Text(
                    stringResource(R.string.setup_pin_mismatch),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = if (portrait) TextAlign.Center else TextAlign.Start
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { onEvent(SetupEvent.Back) }) { Text(stringResource(R.string.action_back)) }
                Button(onClick = { onEvent(SetupEvent.Continue) }, enabled = state.canContinue) {
                    Text(stringResource(R.string.action_continue))
                }
            }
        }
        PinPad(
            onDigit = { onEvent(SetupEvent.Digit(it)) },
            onDelete = { onEvent(SetupEvent.Delete) }
        )
    }
}

@Composable
private fun ProxyStep(state: SetupUiState.Content, onEvent: (SetupEvent) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.setup_proxy_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.proxyUrl,
            onValueChange = { onEvent(SetupEvent.ProxyUrlChanged(it)) },
            label = { Text(stringResource(R.string.setup_proxy_url)) },
            supportingText = { if (!state.proxyUrlValid) Text(stringResource(R.string.setup_proxy_url_hint)) },
            isError = state.proxyUrl.isNotEmpty() && !state.proxyUrlValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.widthIn(min = 360.dp, max = 560.dp)
        )
        SecretField(
            value = state.secret,
            onValueChange = { onEvent(SetupEvent.SecretChanged(it)) },
            label = stringResource(R.string.setup_secret),
            modifier = Modifier.widthIn(min = 360.dp, max = 560.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { onEvent(SetupEvent.Back) }, enabled = !state.saving) { Text(stringResource(R.string.action_back)) }
            Button(onClick = { onEvent(SetupEvent.Continue) }, enabled = state.canContinue) { Text(stringResource(R.string.setup_finish)) }
        }
    }
}

/** Masked text field for secrets and PINs; the value is never shown in clear. */
@Composable
internal fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password),
        modifier = modifier
    )
}

@Preview(device = Devices.TABLET)
@Composable
private fun SetupPinPreview() {
    DlsTheme { SetupScreen(SetupUiState.Content(step = SetupStep.CONFIRM_PIN, pin = "1234", confirmPin = "12", pinMismatch = true), {}) }
}

@Preview(device = Devices.TABLET)
@Composable
private fun SetupProxyPreview() {
    DlsTheme { SetupScreen(SetupUiState.Content(step = SetupStep.PROXY, proxyUrl = "http://x"), {}) }
}

@Preview(widthDp = 800, heightDp = 1280, locale = "ar")
@Composable
private fun SetupPinPortraitPreview() {
    SetupPinPreview()
}
