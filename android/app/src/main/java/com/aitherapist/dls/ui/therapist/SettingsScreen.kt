package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        PageHeader(stringResource(R.string.shell_settings))
        when (state) {
            SettingsUiState.Loading -> Skeleton(rows = 4)
            is SettingsUiState.Error ->
                ErrorState(stringResource(R.string.settings_error, state.reason), onRetry = { onEvent(SettingsEvent.Retry) })
            is SettingsUiState.Content -> {
                PinSection(state, onEvent)
                ProxySection(state, onEvent)
            }
        }
    }
}

private val FIELD_WIDTH = Modifier.widthIn(min = 320.dp, max = 520.dp)

@Composable
private fun PinSection(state: SettingsUiState.Content, onEvent: (SettingsEvent) -> Unit) {
    Section(stringResource(R.string.settings_pin_section)) {
        SecretField(
            state.currentPin,
            { onEvent(SettingsEvent.CurrentPinChanged(it)) },
            stringResource(R.string.settings_current_pin),
            FIELD_WIDTH,
            numeric = true,
            isError = state.pinError == PinChangeError.WRONG_CURRENT
        )
        SecretField(state.newPin, {
            onEvent(SettingsEvent.NewPinChanged(it))
        }, stringResource(R.string.settings_new_pin), FIELD_WIDTH, numeric = true)
        SecretField(
            state.confirmPin,
            { onEvent(SettingsEvent.ConfirmPinChanged(it)) },
            stringResource(R.string.settings_confirm_pin),
            FIELD_WIDTH,
            numeric = true,
            isError = state.pinError == PinChangeError.MISMATCH
        )
        when (state.pinError) {
            PinChangeError.WRONG_CURRENT -> Text(
                stringResource(R.string.settings_pin_wrong_current),
                color = MaterialTheme.colorScheme.error
            )
            PinChangeError.MISMATCH -> Text(stringResource(R.string.settings_pin_mismatch), color = MaterialTheme.colorScheme.error)
            null -> if (state.pinChanged) Text(stringResource(R.string.settings_pin_changed), color = MaterialTheme.colorScheme.primary)
        }
        Button(onClick = { onEvent(SettingsEvent.ChangePin) }, enabled = state.canChangePin) {
            Text(stringResource(R.string.settings_change_pin))
        }
    }
}

@Composable
private fun ProxySection(state: SettingsUiState.Content, onEvent: (SettingsEvent) -> Unit) {
    Section(stringResource(R.string.settings_proxy_section)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.proxyUrl,
                onValueChange = { onEvent(SettingsEvent.ProxyUrlChanged(it)) },
                label = { Text(stringResource(R.string.setup_proxy_url)) },
                supportingText = {
                    when {
                        !state.proxyUrlValid -> Text(stringResource(R.string.setup_proxy_url_hint))
                        state.proxySaved -> Text(stringResource(R.string.settings_saved))
                    }
                },
                isError = state.proxyUrl.isNotEmpty() && !state.proxyUrlValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = FIELD_WIDTH
            )
            Button(onClick = {
                onEvent(SettingsEvent.SaveProxyUrl)
            }, enabled = state.canSaveProxy) { Text(stringResource(R.string.settings_save)) }
        }
        Text(
            stringResource(
                R.string.settings_secret_current,
                stringResource(if (state.secretSet) R.string.settings_secret_masked else R.string.settings_secret_not_set)
            ),
            style = MaterialTheme.typography.bodyLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SecretField(state.newSecret, {
                onEvent(SettingsEvent.SecretChanged(it))
            }, stringResource(R.string.settings_secret_new), FIELD_WIDTH)
            Button(onClick = { onEvent(SettingsEvent.SaveSecret) }, enabled = state.canSaveSecret) {
                Text(stringResource(R.string.settings_secret_replace))
            }
        }
        if (state.secretSaved) Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        shape = RoundedCornerShape(Radius.Md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Preview(device = Devices.TABLET)
@Composable
private fun SettingsPreview() {
    DlsTheme {
        SettingsScreen(
            SettingsUiState.Content(
                proxyUrl = "https://proxy.example.workers.dev",
                secretSet = true,
                pinError = PinChangeError.WRONG_CURRENT
            ),
            {
            }
        )
    }
}
