package com.aitherapist.dls.ui.therapist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.BuildConfig
import com.aitherapist.dls.R
import com.aitherapist.dls.data.Zone
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.SectionCard
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.LevelColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.ui.theme.TSurfaceMuted
import com.aitherapist.dls.ui.theme.Warning
import com.aitherapist.dls.ui.theme.ZoneColors

/** Pages reached from Settings; everything except [MAIN] and [ZONES] is a debug-only advanced tool. */
private enum class SettingsPage { MAIN, ZONES, DIAGNOSTICS, REPLAY, BLIND_REVIEW, ABOUT, METRICS }

/** What the Connection card says about reaching the cloud check. */
enum class ConnectionStatus { NOT_SET_UP, READY, OFFLINE, LAST_CHECK_FAILED }

fun connectionStatus(configured: Boolean, banner: NetworkBanner): ConnectionStatus = when {
    !configured -> ConnectionStatus.NOT_SET_UP
    banner == NetworkBanner.OFFLINE -> ConnectionStatus.OFFLINE
    banner == NetworkBanner.PROXY_FAILED -> ConnectionStatus.LAST_CHECK_FAILED
    else -> ConnectionStatus.READY
}

@Composable
fun SettingsRoute(
    onOpenSample: () -> Unit = {},
    onOpenCharacterPreview: () -> Unit = {},
    onOpenChildMode: (childId: String, taskId: String) -> Unit = { _, _ -> },
    viewModel: SettingsViewModel = hiltViewModel(),
    zonesViewModel: ZoneEditorViewModel = hiltViewModel(),
    networkViewModel: NetworkBannerViewModel = hiltViewModel(),
    exportViewModel: ResultsExportViewModel = hiltViewModel()
) {
    val exportStatus by exportViewModel.status.collectAsStateWithLifecycle()
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            exportViewModel.onPicked(uri?.toString())
        }
    DisposableEffect(exportViewModel) { onDispose { exportViewModel.reset() } }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zonesState by zonesViewModel.uiState.collectAsStateWithLifecycle()
    val banner by networkViewModel.banner.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    var showTryStep by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
    // DONE/FAILED clears when the therapist opens another page or leaves Settings.
    LaunchedEffect(page) { if (page != SettingsPage.MAIN) exportViewModel.reset() }
    val debug = BuildConfig.DEBUG
    if (showTryStep && debug) {
        TryStepScreenDialog(
            onStart = { childId, taskId ->
                showTryStep = false
                onOpenChildMode(childId, taskId)
            },
            onDismiss = { showTryStep = false }
        )
    }
    when {
        page == SettingsPage.ZONES ->
            ZoneEditorRoute(onDone = {
                page = SettingsPage.MAIN
            }, doneLabel = stringResource(R.string.zones_done), viewModel = zonesViewModel)
        // Advanced tools are debug-only; release builds have no entry.
        page == SettingsPage.REPLAY && debug -> SubPage(onBack = { page = SettingsPage.MAIN }) { ReplayRoute() }
        // Diagnostics is a therapist page in release too (Story 5.5); its developer tools stay debug-only inside it.
        page == SettingsPage.DIAGNOSTICS ->
            SubPage(onBack = { page = SettingsPage.MAIN }) {
                DiagnosticsRoute(onOpenSample = onOpenSample, onOpenSettings = { page = SettingsPage.MAIN })
            }
        // Blind review is for the evaluating therapist in release too (Story 5.7), behind the PIN like all of Settings.
        page == SettingsPage.BLIND_REVIEW -> SubPage(onBack = { page = SettingsPage.MAIN }) { BlindReviewRoute() }
        page == SettingsPage.METRICS -> SubPage(onBack = { page = SettingsPage.MAIN }) { MetricsRoute() }
        // About is visible in release too (Story 5.10).
        page == SettingsPage.ABOUT -> SubPage(onBack = { page = SettingsPage.MAIN }) { AboutRoute() }
        else ->
            SettingsScreen(
                state = state,
                onEvent = viewModel::onEvent,
                zones = (zonesState as? ZoneEditorUiState.Content)?.zones,
                banner = banner,
                onOpenZones = { page = SettingsPage.ZONES },
                onDiagnostics = { page = SettingsPage.DIAGNOSTICS },
                onBlindReview = { page = SettingsPage.BLIND_REVIEW },
                onMetrics = { page = SettingsPage.METRICS },
                exportBroken = exportViewModel.brokenSessions,
                onAbout = { page = SettingsPage.ABOUT },
                exportStatus = exportStatus,
                onExport = { exportLauncher.launch(RESULTS_FILE_NAME) },
                advanced =
                if (debug) {
                    AdvancedTools(
                        onReplay = { page = SettingsPage.REPLAY },
                        onTryStepScreen = { showTryStep = true },
                        onCharacterPreview = onOpenCharacterPreview
                    )
                } else {
                    null
                }
            )
    }
}

/** Entries of the debug-only "Advanced tools" card. */
class AdvancedTools(val onReplay: () -> Unit, val onTryStepScreen: () -> Unit, val onCharacterPreview: () -> Unit)

/** An advanced tool page with a quiet "back to Settings" row above it. */
@Composable
private fun SubPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.S3, top = Spacing.S2)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.settings_back), modifier = Modifier.padding(start = Spacing.S2))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    zones: Map<String, Zone>? = null,
    banner: NetworkBanner = NetworkBanner.NONE,
    onOpenZones: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onBlindReview: () -> Unit = {},
    onMetrics: () -> Unit = {},
    exportBroken: Int = 0,
    exportStatus: ExportStatus = ExportStatus.IDLE,
    onExport: () -> Unit = {},
    advanced: AdvancedTools? = null,
    onAbout: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.S4),
        verticalArrangement = Arrangement.spacedBy(Spacing.S4)
    ) {
        PageHeader(stringResource(R.string.shell_settings))
        when (state) {
            SettingsUiState.Loading -> Skeleton(rows = 4)
            is SettingsUiState.Error ->
                ErrorState(stringResource(R.string.settings_error, state.reason), onRetry = { onEvent(SettingsEvent.Retry) })
            is SettingsUiState.Content -> {
                GroupTitle(stringResource(R.string.settings_group_security))
                PinSection(state, onEvent)
                GroupTitle(stringResource(R.string.settings_group_connection))
                ProxySection(state, onEvent, connectionStatus(state.proxyUrlValid && state.secretSet, banner))
                GroupTitle(stringResource(R.string.settings_group_zones))
                ZonesSection(zones, onOpenZones)
                GroupTitle(stringResource(R.string.diagnostics_title))
                SectionCard(stringResource(R.string.diagnostics_title), CARD_WIDTH, icon = Icons.Filled.Settings) {
                    ToolRow(Icons.Filled.Settings, R.string.diag_health_title, R.string.settings_tool_diagnostics_hint, onDiagnostics)
                }
                GroupTitle(stringResource(R.string.settings_group_evaluation))
                SectionCard(stringResource(R.string.settings_group_evaluation), CARD_WIDTH, icon = Icons.Filled.Search) {
                    ToolRow(Icons.Filled.Search, R.string.blind_title, R.string.settings_tool_blind_hint, onBlindReview)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ToolRow(Icons.Filled.Check, R.string.settings_tool_metrics_title, R.string.settings_tool_metrics_hint, onMetrics)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ToolRow(Icons.Filled.DateRange, R.string.export_title, R.string.export_hint) {
                        if (exportStatus != ExportStatus.WORKING) onExport()
                    }
                    if (exportStatus == ExportStatus.MISSING_CODES) {
                        Text(
                            pluralStringResource(R.plurals.export_missing_codes, exportBroken, exportBroken.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    exportMessage(exportStatus)?.let { (text, error) ->
                        Text(
                            stringResource(text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                advanced?.let {
                    GroupTitle(stringResource(R.string.settings_advanced_section))
                    AdvancedSection(it)
                }
                GroupTitle(stringResource(R.string.about_title))
                SectionCard(stringResource(R.string.about_title), CARD_WIDTH, icon = Icons.Filled.Info) {
                    ToolRow(Icons.Filled.Info, R.string.about_app_name, R.string.about_row_hint, onAbout)
                }
            }
        }
    }
}

/** Default name offered by the system file picker for the results export (Story 5.6). */
const val RESULTS_FILE_NAME = "results.csv"

/** The calm line under "Export results", with whether it is an error. */
private fun exportMessage(status: ExportStatus): Pair<Int, Boolean>? = when (status) {
    ExportStatus.IDLE -> null
    ExportStatus.WORKING -> R.string.export_working to false
    ExportStatus.DONE -> R.string.export_done to false
    ExportStatus.FAILED -> R.string.export_failed to true
    ExportStatus.MISSING_CODES -> null
}

private val CARD_WIDTH = Modifier.widthIn(max = 760.dp).fillMaxWidth()
private val FIELD_WIDTH = Modifier.widthIn(min = 280.dp, max = 440.dp)

@Composable
private fun GroupTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.S2, start = Spacing.S1).then(CARD_WIDTH)
    )
}

@Composable
private fun PinSection(state: SettingsUiState.Content, onEvent: (SettingsEvent) -> Unit) {
    SectionCard(
        stringResource(R.string.settings_pin_section),
        CARD_WIDTH,
        icon = Icons.Filled.Lock,
        subtitle = stringResource(R.string.settings_pin_subtitle)
    ) {
        SecretField(
            state.currentPin,
            { onEvent(SettingsEvent.CurrentPinChanged(it)) },
            stringResource(R.string.settings_current_pin),
            FIELD_WIDTH,
            numeric = true,
            isError = state.pinError == PinChangeError.WRONG_CURRENT
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            SecretField(
                state.newPin,
                { onEvent(SettingsEvent.NewPinChanged(it)) },
                stringResource(R.string.settings_new_pin),
                Modifier.weight(1f),
                numeric = true
            )
            SecretField(
                state.confirmPin,
                { onEvent(SettingsEvent.ConfirmPinChanged(it)) },
                stringResource(R.string.settings_confirm_pin),
                Modifier.weight(1f),
                numeric = true,
                isError = state.pinError == PinChangeError.MISMATCH
            )
        }
        when (state.pinError) {
            PinChangeError.WRONG_CURRENT -> Text(
                stringResource(R.string.settings_pin_wrong_current),
                color = MaterialTheme.colorScheme.error
            )
            PinChangeError.MISMATCH -> Text(stringResource(R.string.settings_pin_mismatch), color = MaterialTheme.colorScheme.error)
            null -> if (state.pinChanged) Text(stringResource(R.string.settings_pin_changed), color = MaterialTheme.colorScheme.primary)
        }
        Button(
            onClick = { onEvent(SettingsEvent.ChangePin) },
            enabled = state.canChangePin,
            shape = RoundedCornerShape(Radius.Sm),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.settings_change_pin))
        }
    }
}

@Composable
private fun ProxySection(state: SettingsUiState.Content, onEvent: (SettingsEvent) -> Unit, status: ConnectionStatus) {
    SectionCard(
        stringResource(R.string.settings_proxy_section),
        CARD_WIDTH,
        icon = Icons.Filled.Share,
        subtitle = stringResource(R.string.settings_proxy_subtitle)
    ) {
        StatusLine(status)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), verticalAlignment = Alignment.Top) {
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
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { onEvent(SettingsEvent.SaveProxyUrl) },
                enabled = state.canSaveProxy,
                shape = RoundedCornerShape(Radius.Sm),
                modifier = Modifier.padding(top = Spacing.S2).heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.settings_save)) }
        }
        Text(
            stringResource(
                R.string.settings_secret_current,
                stringResource(if (state.secretSet) R.string.settings_secret_masked else R.string.settings_secret_not_set)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), verticalAlignment = Alignment.CenterVertically) {
            SecretField(state.newSecret, {
                onEvent(SettingsEvent.SecretChanged(it))
            }, stringResource(R.string.settings_secret_new), Modifier.weight(1f))
            OutlinedButton(
                onClick = { onEvent(SettingsEvent.SaveSecret) },
                enabled = state.canSaveSecret,
                shape = RoundedCornerShape(Radius.Sm),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.settings_secret_replace))
            }
        }
        if (state.secretSaved) Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StatusLine(status: ConnectionStatus) {
    val (color, text) =
        when (status) {
            ConnectionStatus.READY -> LevelColors.Level3 to R.string.settings_status_ready
            ConnectionStatus.NOT_SET_UP -> LevelColors.NotScored to R.string.settings_status_not_set_up
            ConnectionStatus.OFFLINE -> Warning to R.string.settings_status_offline
            ConnectionStatus.LAST_CHECK_FAILED -> Warning to R.string.settings_status_failed
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ZonesSection(zones: Map<String, Zone>?, onOpenZones: () -> Unit) {
    SectionCard(
        stringResource(R.string.settings_zones_section),
        CARD_WIDTH,
        icon = Icons.Filled.LocationOn,
        subtitle = stringResource(R.string.settings_zones_body)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S4), verticalAlignment = Alignment.CenterVertically) {
            ZoneMiniPreview(zones.orEmpty(), Modifier.width(200.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                ZONE_NAMES.forEach { name ->
                    val drawn = zones?.containsKey(name) == true
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                        Box(
                            Modifier.size(
                                12.dp
                            ).clip(RoundedCornerShape(3.dp)).background(if (drawn) ZoneColors.of(name) else TSurfaceMuted)
                        )
                        Text(zoneLabel(name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(if (drawn) R.string.zones_drawn else R.string.zones_not_drawn),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = onOpenZones,
                    shape = RoundedCornerShape(Radius.Sm),
                    modifier = Modifier.padding(top = Spacing.S2).heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.settings_zones_open)) }
            }
        }
    }
}

/** Saved zones on a 4:3 picture placeholder, in the same display orientation as the editor. */
@Composable
fun ZoneMiniPreview(zones: Map<String, Zone>, modifier: Modifier = Modifier) {
    val frame = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .aspectRatio(DEFAULT_PREVIEW_ASPECT)
            .clip(RoundedCornerShape(Radius.Sm))
            .background(TSurfaceMuted)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(frame, style = Stroke(width = 1.dp.toPx()))
            zones.forEach { (name, zone) ->
                val r = ZoneMath.toDisplay(zone)
                val color: Color = ZoneColors.of(name)
                val topLeft = Offset(r.left * size.width, r.top * size.height)
                val s = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height)
                drawRect(color.copy(alpha = 0.22f), topLeft, s)
                drawRect(color, topLeft, s, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun AdvancedSection(tools: AdvancedTools) {
    SectionCard(
        stringResource(R.string.settings_advanced_section),
        CARD_WIDTH,
        icon = Icons.Filled.Build,
        subtitle = stringResource(R.string.settings_advanced_subtitle)
    ) {
        Column {
            ToolRow(Icons.Filled.Refresh, R.string.settings_open_replay, R.string.settings_tool_replay_hint, tools.onReplay)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ToolRow(
                Icons.Filled.PlayArrow,
                R.string.diagnostics_try_step_screen,
                R.string.settings_tool_try_step_hint,
                tools.onTryStepScreen
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ToolRow(
                Icons.Filled.Face,
                R.string.diagnostics_open_character_preview,
                R.string.settings_tool_character_hint,
                tools.onCharacterPreview
            )
        }
    }
}

@Composable
private fun ToolRow(icon: ImageVector, title: Int, hint: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(vertical = Spacing.S2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val DEFAULT_PREVIEW_ASPECT = 4f / 3f

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
            {},
            zones = mapOf("mat" to Zone(0.2f, 0.6f, 0.7f, 0.9f)),
            advanced = AdvancedTools({}, {}, {})
        )
    }
}
