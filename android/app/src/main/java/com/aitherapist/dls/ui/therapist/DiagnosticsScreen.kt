package com.aitherapist.dls.ui.therapist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.BuildConfig
import com.aitherapist.dls.R
import com.aitherapist.dls.camera.CameraController
import com.aitherapist.dls.camera.FrameBuffer
import com.aitherapist.dls.camera.Mirror
import com.aitherapist.dls.data.CannotTellCounts
import com.aitherapist.dls.ui.LocalExternalHop
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.SectionCard
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.LevelColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.ui.theme.Warning
import com.aitherapist.dls.ui.theme.isPortrait
import com.aitherapist.dls.vision.FpsMeter
import com.aitherapist.dls.vision.FpsRunResult
import com.aitherapist.dls.vision.GroupVisibility
import com.aitherapist.dls.vision.InferenceDelegate
import com.aitherapist.dls.vision.LandmarkGroup
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.Outcome
import com.aitherapist.dls.vision.PoseLandmarkIndex
import com.aitherapist.dls.vision.ProxyHealth
import com.aitherapist.dls.vision.VisibilityLevel
import java.util.Locale

/**
 * Stateful entry (Story 5.5): permission, camera binding and lifecycle around the stateless screen. The camera and
 * landmarkers are released on stop and when leaving the screen; the proxy ping runs on open and on "Check again" only.
 */
@Composable
fun DiagnosticsRoute(onOpenSample: () -> Unit, onOpenSettings: () -> Unit = {}, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val externalHop = LocalExternalHop.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onEvent(DiagnosticsEvent.PermissionResult(granted))
        }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onEvent(DiagnosticsEvent.PermissionResult(true)) else launcher.launch(Manifest.permission.CAMERA)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Re-check after returning from system settings; only upgrades a denial, never re-prompts.
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted && state !is DiagnosticsUiState.Content) viewModel.onEvent(DiagnosticsEvent.PermissionResult(true))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.start() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    // Leaving the screen (back to Settings) releases the landmarkers even while the activity stays started.
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }
    // Content appears once permission is granted; start the engine then.
    LaunchedEffect(state is DiagnosticsUiState.Loading) { viewModel.start() }

    DiagnosticsScreen(
        state = state,
        health = health,
        onEvent = viewModel::onEvent,
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        onOpenAppSettings = {
            externalHop()
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        },
        onOpenSettings = onOpenSettings,
        onOpenSample = onOpenSample,
        cameraPreview = { modifier ->
            CameraPreview(onFrame = viewModel::onFrame, onCameraError = viewModel::onCameraError, modifier = modifier)
        }
    )
}

@Composable
private fun CameraPreview(
    onFrame: (android.graphics.Bitmap, Long) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }
    val previewView =
        remember {
            PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        }
    DisposableEffect(owner) {
        controller.bind(owner, previewView.surfaceProvider, onFrame, onCameraError)
        onDispose { controller.unbind() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    health: HealthState,
    onEvent: (DiagnosticsEvent) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSample: () -> Unit,
    modifier: Modifier = Modifier,
    developer: Boolean = BuildConfig.DEBUG,
    cameraPreview: @Composable (Modifier) -> Unit = {}
) {
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.S4),
            verticalArrangement = Arrangement.spacedBy(Spacing.S4)
        ) {
            PageHeader(stringResource(R.string.diagnostics_title)) {
                OutlinedButton(onClick = { onEvent(DiagnosticsEvent.CheckAgain) }, enabled = health.proxy !is ProxyCheck.Running) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.diag_check_again), modifier = Modifier.padding(start = Spacing.S2))
                }
            }
            HealthCards(health, onOpenSettings)
            SectionCard(stringResource(R.string.diag_camera_title), Modifier.fillMaxWidth()) {
                when (state) {
                    DiagnosticsUiState.Loading -> Skeleton(rows = 3)
                    DiagnosticsUiState.PermissionRequired ->
                        Message(stringResource(R.string.diagnostics_permission_needed), R.string.diagnostics_grant, onRequestPermission)
                    DiagnosticsUiState.PermissionDenied ->
                        Message(
                            stringResource(R.string.diagnostics_permission_denied),
                            R.string.diagnostics_open_settings,
                            onOpenAppSettings
                        )
                    is DiagnosticsUiState.Error ->
                        Message(stringResource(R.string.diagnostics_error, state.reason), R.string.diag_retry_camera) {
                            onEvent(DiagnosticsEvent.Retry)
                        }
                    is DiagnosticsUiState.Content -> CameraContent(state, cameraPreview)
                }
            }
            if (developer) {
                SectionCard(stringResource(R.string.diag_developer_title), Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onOpenSample) { Text(stringResource(R.string.diagnostics_open_sample)) }
                    if (state is DiagnosticsUiState.Content) DeveloperTools(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun HealthCards(health: HealthState, onOpenSettings: () -> Unit) {
    val cardWidth = Modifier.widthIn(min = 320.dp, max = 520.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S4)) {
        SectionCard(stringResource(R.string.diag_proxy_title), cardWidth) { ProxyStatus(health.proxy, onOpenSettings) }
        SectionCard(stringResource(R.string.diag_rate_title), cardWidth, subtitle = stringResource(R.string.diag_rate_hint)) {
            when (val rate = health.rate) {
                RateState.Loading -> StatusLine(Icons.Filled.Info, LevelColors.NotScored, stringResource(R.string.diag_proxy_checking))
                RateState.Unavailable -> StatusLine(Icons.Filled.Warning, Warning, stringResource(R.string.diag_rate_unavailable))
                is RateState.Ready -> RateLine(rate.counts)
            }
        }
    }
}

@Composable
private fun RateLine(counts: CannotTellCounts) {
    val percent = counts.percent
    if (percent == null) {
        StatusLine(Icons.Filled.Info, LevelColors.NotScored, stringResource(R.string.diag_rate_empty))
    } else {
        val text = stringResource(R.string.diag_rate_value, percent, counts.cannotTell, counts.total)
        StatusLine(Icons.Filled.Info, LevelColors.CannotTell, text)
    }
}

/** Status by word + icon + colour (never colour alone, never red). */
@Composable
private fun ProxyStatus(check: ProxyCheck, onOpenSettings: () -> Unit) {
    if (check !is ProxyCheck.Done) {
        StatusLine(Icons.Filled.Info, LevelColors.NotScored, stringResource(R.string.diag_proxy_checking))
        return
    }
    val (title, hint) =
        when (check.health) {
            ProxyHealth.OK -> stringResource(R.string.diag_proxy_ok, check.roundTripMs) to null
            ProxyHealth.REJECTED_SECRET -> stringResource(R.string.diag_proxy_rejected) to null
            ProxyHealth.NOT_CONFIGURED -> stringResource(R.string.diag_proxy_not_set) to stringResource(R.string.diag_proxy_not_set_hint)
            ProxyHealth.SETTINGS_UNREADABLE ->
                stringResource(R.string.diag_proxy_settings_unreadable) to stringResource(R.string.diag_proxy_settings_unreadable_hint)
            ProxyHealth.OFFLINE -> stringResource(R.string.diag_proxy_offline) to stringResource(R.string.diag_proxy_offline_hint)
            ProxyHealth.TIMEOUT -> stringResource(R.string.diag_proxy_timeout) to stringResource(R.string.diag_proxy_timeout_hint)
            ProxyHealth.UNEXPECTED -> stringResource(R.string.diag_proxy_unexpected) to stringResource(R.string.diag_proxy_unexpected_hint)
        }
    val ok = check.health == ProxyHealth.OK
    StatusLine(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, if (ok) LevelColors.Level3 else Warning, title)
    hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (check.health in SETTINGS_FIXES) TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.diag_go_settings)) }
}

private val SETTINGS_FIXES =
    setOf(ProxyHealth.NOT_CONFIGURED, ProxyHealth.SETTINGS_UNREADABLE, ProxyHealth.REJECTED_SECRET, ProxyHealth.UNEXPECTED)

@Composable
private fun StatusLine(icon: ImageVector, color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Message(text: String, actionRes: Int, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        StatusLine(Icons.Filled.Info, Warning, text)
        Button(onClick = onAction) { Text(stringResource(actionRes)) }
    }
}

@Composable
private fun CameraContent(state: DiagnosticsUiState.Content, cameraPreview: @Composable (Modifier) -> Unit) {
    val preview: @Composable (Modifier) -> Unit = { m ->
        Box(modifier = m.aspectRatio(state.frameAspect).clip(RoundedCornerShape(Radius.Sm))) {
            cameraPreview(Modifier.fillMaxSize())
            LandmarkOverlay(state.posePoints, state.handPoints, Modifier.fillMaxSize())
        }
    }
    val metrics: @Composable (Modifier) -> Unit = { m ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S3), modifier = m) {
            Metric(stringResource(R.string.diag_camera_fps), stringResource(R.string.diag_fps_value, fps(state.combinedFps)))
            LandmarkGroup.entries.forEach { group -> GroupBar(group, state.groups[group] ?: 0) }
        }
    }
    if (isPortrait()) {
        // Portrait: preview full width (capped) on top, the visibility bars below it.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S4), modifier = Modifier.fillMaxWidth()) {
            preview(Modifier.widthIn(max = PREVIEW_WIDTH_PORTRAIT).fillMaxWidth())
            metrics(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S4), modifier = Modifier.fillMaxWidth()) {
            preview(Modifier.width(PREVIEW_WIDTH))
            metrics(Modifier.weight(1f))
        }
    }
}

@Composable
private fun GroupBar(group: LandmarkGroup, percent: Int) {
    val (word, color) =
        when (GroupVisibility.level(percent)) {
            VisibilityLevel.CLEAR -> R.string.diag_level_clear to LevelColors.Level3
            VisibilityLevel.PARTLY -> R.string.diag_level_partly to LevelColors.Level2
            VisibilityLevel.NOT_SEEN -> R.string.diag_level_not_seen to LevelColors.NotScored
        }
    val name =
        when (group) {
            LandmarkGroup.HEAD -> R.string.diag_group_head
            LandmarkGroup.SHOULDERS -> R.string.diag_group_shoulders
            LandmarkGroup.ARMS -> R.string.diag_group_arms
            LandmarkGroup.HIPS -> R.string.diag_group_hips
            LandmarkGroup.HANDS -> R.string.diag_group_hands
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(name), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.diag_group_value, stringResource(word), percent),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        val track = MaterialTheme.colorScheme.surfaceVariant
        Box(Modifier.fillMaxWidth().height(BAR_HEIGHT).clip(RoundedCornerShape(Spacing.S1)).background(track)) {
            Box(Modifier.fillMaxWidth(percent.coerceIn(0, PERCENT) / PERCENT.toFloat()).height(BAR_HEIGHT).background(color))
        }
    }
}

/** Debug-only spike tools (Epic 1): delegate, per-stream fps, timed run and the try-step attempt. */
@Composable
private fun DeveloperTools(state: DiagnosticsUiState.Content, onEvent: (DiagnosticsEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        Metric(stringResource(R.string.diagnostics_delegate), state.delegate.name)
        Metric(stringResource(R.string.diagnostics_pose_fps), fps(state.poseFps))
        Metric(stringResource(R.string.diagnostics_hand_fps), fps(state.handFps))
        Metric(stringResource(R.string.diagnostics_combined_fps), fps(state.combinedFps))
        Metric(stringResource(R.string.diagnostics_visible, RETENTION_SEC), "${state.visiblePercent}%")
        if (state.spikeSteps.size > 1 && state.attempt !is AttemptState.Recording) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                state.spikeSteps.forEach { id ->
                    FilterChip(
                        selected = state.attempt?.stepId == id,
                        onClick = { onEvent(DiagnosticsEvent.SelectStep(id)) },
                        label = { Text(id) }
                    )
                }
            }
        }
        state.attempt?.let { Attempt(it, onEvent) }
        when (val run = state.run) {
            RunState.Idle ->
                Button(onClick = { onEvent(DiagnosticsEvent.StartRun) }) { Text(stringResource(R.string.diagnostics_run, RUN_SEC)) }
            is RunState.Running ->
                Text(stringResource(R.string.diagnostics_running, run.elapsedSec, run.totalSec))
            is RunState.Done -> {
                Text(stringResource(R.string.diagnostics_run_result, RUN_SEC), fontWeight = FontWeight.Bold)
                Metric(stringResource(R.string.diagnostics_pose_fps), fps(run.result.pose))
                Metric(stringResource(R.string.diagnostics_hand_fps), fps(run.result.hand))
                Metric(stringResource(R.string.diagnostics_combined_fps), fps(run.result.combined))
                OutlinedButton(onClick = { onEvent(DiagnosticsEvent.StartRun) }) {
                    Text(stringResource(R.string.diagnostics_run, RUN_SEC))
                }
            }
        }
    }
}

/** Debug-only spike attempt: start button, countdown, then outcome, reason and latency. */
@Composable
private fun Attempt(attempt: AttemptState, onEvent: (DiagnosticsEvent) -> Unit) {
    when (attempt) {
        is AttemptState.Recording ->
            Text(stringResource(R.string.diagnostics_attempt_recording, attempt.stepId, attempt.remainingSec))
        is AttemptState.Idle, is AttemptState.Done -> {
            if (attempt is AttemptState.Done) {
                Text(
                    stringResource(
                        R.string.diagnostics_attempt_result,
                        attempt.stepId,
                        attempt.outcome.name.lowercase(Locale.ROOT),
                        attempt.reason,
                        attempt.pairingWaitMs,
                        attempt.evaluationMs,
                        attempt.pairingWaitMs + attempt.evaluationMs
                    ),
                    fontWeight = FontWeight.Bold
                )
                attempt.modelId?.let { model ->
                    Text(stringResource(R.string.diagnostics_attempt_model, model, attempt.promptVersion.orEmpty()))
                }
            }
            OutlinedButton(onClick = { onEvent(DiagnosticsEvent.TryStep) }) {
                Text(stringResource(R.string.diagnostics_try_step, attempt.stepId))
            }
        }
    }
}

/**
 * Draws landmarks over the (mirrored) preview. The overlay shares one box with the PreviewView whose aspect
 * ratio is the upright analysis frame's width / height, and the PreviewView uses FIT_CENTER, so the image
 * fills the box exactly (no crop, no letterbox). Mapping: screenX = (1 - x) * boxWidth (the single mirror,
 * because the preview is mirrored), screenY = y * boxHeight. Inference itself is never flipped. Wrists are coloured by side: RIGHT_WRIST in red, LEFT_WRIST in blue.
 */
@Composable
private fun LandmarkOverlay(pose: List<LandmarkPoint>, hands: List<List<LandmarkPoint>>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        pose.forEachIndexed { index, point ->
            val color =
                when (index) {
                    PoseLandmarkIndex.RIGHT_WRIST -> Color.Red
                    PoseLandmarkIndex.LEFT_WRIST -> Color.Blue
                    else -> Color.Green
                }
            val radius = if (index == PoseLandmarkIndex.RIGHT_WRIST || index == PoseLandmarkIndex.LEFT_WRIST) 14f else 6f
            drawPoint(point, color, radius)
        }
        hands.flatten().forEach { drawPoint(it, Color.Yellow, 4f) }
    }
}

private fun DrawScope.drawPoint(point: LandmarkPoint, color: Color, radius: Float) {
    // MediaPipe can estimate landmarks outside the frame (x or y outside 0..1); never draw outside the preview.
    if (point.x !in 0f..1f || point.y !in 0f..1f) return
    drawCircle(color, radius, Offset(Mirror.flipX(point.x) * size.width, point.y * size.height))
}

@Composable
private fun Metric(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

// Western digits always (therapist data), whatever the UI locale.
private fun fps(value: Double) = String.format(Locale.ROOT, "%.1f", value)

private const val PERCENT = 100
private val PREVIEW_WIDTH = 420.dp
private val PREVIEW_WIDTH_PORTRAIT = 560.dp
private val BAR_HEIGHT = 8.dp
private const val RUN_SEC = (FpsMeter.RUN_DURATION_MS / 1_000L).toInt()
private const val RETENTION_SEC = (FrameBuffer.DEFAULT_RETENTION_MS / 1_000L).toInt()

private val previewContent =
    DiagnosticsUiState.Content(
        delegate = InferenceDelegate.GPU,
        poseFps = 24.0,
        handFps = 18.0,
        combinedFps = 17.0,
        visiblePercent = 92,
        groups = mapOf(LandmarkGroup.HEAD to 100, LandmarkGroup.SHOULDERS to 95, LandmarkGroup.ARMS to 60, LandmarkGroup.HANDS to 20),
        frameAspect = 4f / 3f,
        posePoints = List(33) { LandmarkPoint(0.3f + it * 0.01f, 0.2f + it * 0.02f) },
        handPoints = emptyList(),
        run = RunState.Idle
    )

private val previewHealthy = HealthState(ProxyCheck.Done(ProxyHealth.OK, 420), RateState.Ready(CannotTellCounts(40, 5)))

@Composable
private fun Preview(state: DiagnosticsUiState, health: HealthState, developer: Boolean = false) {
    DlsTheme { DiagnosticsScreen(state, health, {}, {}, {}, {}, {}, developer = developer) }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsContentPreview() = Preview(previewContent, previewHealthy)

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsDeveloperPreview() = Preview(
    previewContent.copy(
        run = RunState.Done(FpsRunResult(24.1, 17.9, 16.8, 60_000)),
        attempt = AttemptState.Done("step.preview", Outcome.DONE, "check_passed", 101, 3)
    ),
    HealthState(ProxyCheck.Done(ProxyHealth.REJECTED_SECRET, 120), RateState.Ready(CannotTellCounts(0, 0))),
    developer = true
)

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsPermissionDeniedPreview() =
    Preview(DiagnosticsUiState.PermissionDenied, HealthState(ProxyCheck.Done(ProxyHealth.NOT_CONFIGURED, 0)))

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsLoadingPreview() = Preview(DiagnosticsUiState.Loading, HealthState())

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsErrorPreview() =
    Preview(DiagnosticsUiState.Error("RuntimeException"), HealthState(ProxyCheck.Done(ProxyHealth.OFFLINE, 0)))
