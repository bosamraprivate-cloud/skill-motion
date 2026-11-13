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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.aitherapist.dls.R
import com.aitherapist.dls.camera.CameraController
import com.aitherapist.dls.camera.FrameBuffer
import com.aitherapist.dls.camera.Mirror
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.vision.FpsMeter
import com.aitherapist.dls.vision.FpsRunResult
import com.aitherapist.dls.vision.InferenceDelegate
import com.aitherapist.dls.vision.LandmarkPoint
import com.aitherapist.dls.vision.Outcome
import com.aitherapist.dls.vision.PoseLandmarkIndex
import java.util.Locale

/** Stateful entry: permission, camera binding and lifecycle around the stateless screen. */
@Composable
fun DiagnosticsRoute(
    onOpenSample: () -> Unit,
    onOpenCharacterPreview: () -> Unit,
    onOpenChildMode: (childId: String, taskId: String) -> Unit = { _, _ -> },
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showTryStep by rememberSaveable { mutableStateOf(false) }
    if (showTryStep) {
        TryStepScreenDialog(
            onStart = { childId, taskId ->
                showTryStep = false
                onOpenChildMode(childId, taskId)
            },
            onDismiss = { showTryStep = false }
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    // Content appears once permission is granted; start the engine then.
    LaunchedEffect(state is DiagnosticsUiState.Loading) { viewModel.start() }

    DiagnosticsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        },
        onOpenSample = onOpenSample,
        onOpenCharacterPreview = onOpenCharacterPreview,
        onTryStepScreen = { showTryStep = true },
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
    onEvent: (DiagnosticsEvent) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSample: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCharacterPreview: () -> Unit = {},
    onTryStepScreen: () -> Unit = {},
    cameraPreview: @Composable (Modifier) -> Unit = {}
) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = onOpenSample) { Text(stringResource(R.string.diagnostics_open_sample)) }
                OutlinedButton(onClick = onOpenCharacterPreview) {
                    Text(stringResource(R.string.diagnostics_open_character_preview))
                }
                OutlinedButton(onClick = onTryStepScreen) { Text(stringResource(R.string.diagnostics_try_step_screen)) }
            }
            when (state) {
                DiagnosticsUiState.Loading -> Skeleton(rows = 3)
                DiagnosticsUiState.PermissionRequired ->
                    PermissionMessage(R.string.diagnostics_permission_needed, R.string.diagnostics_grant, onRequestPermission)
                DiagnosticsUiState.PermissionDenied ->
                    PermissionMessage(R.string.diagnostics_permission_denied, R.string.diagnostics_open_settings, onOpenSettings)
                is DiagnosticsUiState.Error ->
                    ErrorState(
                        message = stringResource(R.string.diagnostics_error, state.reason),
                        onRetry = { onEvent(DiagnosticsEvent.Retry) }
                    )
                is DiagnosticsUiState.Content -> Content(state, onEvent, cameraPreview)
            }
        }
    }
}

@Composable
private fun PermissionMessage(messageRes: Int, actionRes: Int, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(messageRes), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAction) { Text(stringResource(actionRes)) }
    }
}

@Composable
private fun Content(state: DiagnosticsUiState.Content, onEvent: (DiagnosticsEvent) -> Unit, cameraPreview: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxHeight().aspectRatio(state.frameAspect)) {
            cameraPreview(Modifier.fillMaxSize())
            LandmarkOverlay(state.posePoints, state.handPoints, Modifier.fillMaxSize())
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())
        ) {
            Metric(stringResource(R.string.diagnostics_delegate), state.delegate.name)
            Metric(stringResource(R.string.diagnostics_pose_fps), fps(state.poseFps))
            Metric(stringResource(R.string.diagnostics_hand_fps), fps(state.handFps))
            Metric(stringResource(R.string.diagnostics_combined_fps), fps(state.combinedFps))
            Metric(stringResource(R.string.diagnostics_visible, RETENTION_SEC), "${state.visiblePercent}%")
            if (state.spikeSteps.isNotEmpty()) ProbeRow(state.probe, onEvent)
            if (state.spikeSteps.size > 1 && state.attempt !is AttemptState.Recording) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** Debug-only proxy reachability row (FR-1). */
@Composable
private fun ProbeRow(probe: ProbeState?, onEvent: (DiagnosticsEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onEvent(DiagnosticsEvent.Probe) }, enabled = probe !is ProbeState.Running) {
            Text(stringResource(R.string.diagnostics_probe))
        }
        when (probe) {
            null -> Unit
            ProbeState.Running -> Text(stringResource(R.string.diagnostics_probe_running))
            is ProbeState.Done -> {
                val res = if (probe.reachable) R.string.diagnostics_probe_reachable else R.string.diagnostics_probe_unreachable
                Text(stringResource(res, probe.detail, probe.roundTripMs), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
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

private fun fps(value: Double) = String.format(Locale.ROOT, "%.1f", value)

private const val RUN_SEC = (FpsMeter.RUN_DURATION_MS / 1_000L).toInt()
private const val RETENTION_SEC = (FrameBuffer.DEFAULT_RETENTION_MS / 1_000L).toInt()

private val previewContent =
    DiagnosticsUiState.Content(
        delegate = InferenceDelegate.GPU,
        poseFps = 24.0,
        handFps = 18.0,
        combinedFps = 17.0,
        visiblePercent = 92,
        frameAspect = 4f / 3f,
        posePoints = List(33) { LandmarkPoint(0.3f + it * 0.01f, 0.2f + it * 0.02f) },
        handPoints = emptyList(),
        run = RunState.Idle
    )

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsContentPreview() {
    DlsTheme { DiagnosticsScreen(previewContent, {}, {}, {}, {}) }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsDonePreview() {
    DlsTheme {
        DiagnosticsScreen(
            previewContent.copy(
                run = RunState.Done(FpsRunResult(24.1, 17.9, 16.8, 60_000)),
                attempt = AttemptState.Done("step.preview", Outcome.DONE, "check_passed", 101, 3)
            ),
            {},
            {},
            {},
            {}
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsPermissionDeniedPreview() {
    DlsTheme { DiagnosticsScreen(DiagnosticsUiState.PermissionDenied, {}, {}, {}, {}) }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsPermissionRequiredPreview() {
    DlsTheme { DiagnosticsScreen(DiagnosticsUiState.PermissionRequired, {}, {}, {}, {}) }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsLoadingPreview() {
    DlsTheme { DiagnosticsScreen(DiagnosticsUiState.Loading, {}, {}, {}, {}) }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun DiagnosticsErrorPreview() {
    DlsTheme { DiagnosticsScreen(DiagnosticsUiState.Error("RuntimeException"), {}, {}, {}, {}) }
}
