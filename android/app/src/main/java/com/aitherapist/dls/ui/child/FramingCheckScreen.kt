package com.aitherapist.dls.ui.child

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.camera.CameraController
import com.aitherapist.dls.ui.LocalExternalHop
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.vision.Framing
import com.aitherapist.dls.vision.FramingGuidance

private enum class CameraAccess { CHECKING, GRANTED, DENIED }

/**
 * Framing check (UX "Framing check", Story 3.4): adult-facing, before the start screen of a camera-scored task. The
 * mirrored preview carries a silhouette for the task's framing, grey until the needed landmarks stay visible ~1 s, then
 * sage. [onHandToChild] (sage, or Start anyway) enters child mode; content-only tasks call it at once. Back (system)
 * returns to the profile. The camera and landmarkers are released when the screen leaves.
 */
@Composable
fun FramingCheckRoute(taskId: String, onHandToChild: () -> Unit, viewModel: FramingCheckViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val externalHop = LocalExternalHop.current
    LaunchedEffect(taskId) { viewModel.load(taskId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val handOver by rememberUpdatedState(onHandToChild)
    LaunchedEffect(state) { if (state == FramingUiState.Skip) handOver() }
    var access by rememberSaveable { mutableStateOf(CameraAccess.CHECKING) }
    val granted = { ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            access = if (ok) CameraAccess.GRANTED else CameraAccess.DENIED
        }
    val needsCamera = state is FramingUiState.Check
    LaunchedEffect(needsCamera) {
        if (!needsCamera) return@LaunchedEffect
        when {
            granted() -> access = CameraAccess.GRANTED
            access == CameraAccess.CHECKING -> launcher.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (access != CameraAccess.GRANTED && granted()) access = CameraAccess.GRANTED
    }
    val cameraOn = needsCamera && access == CameraAccess.GRANTED
    if (cameraOn) {
        LifecycleStartEffect(viewModel) {
            viewModel.start()
            onStopOrDispose { viewModel.stop() }
        }
    }
    FramingCheckScreen(
        state = state,
        cameraGranted = access == CameraAccess.GRANTED,
        cameraDenied = access == CameraAccess.DENIED,
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            externalHop()
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
        onHandToChild = onHandToChild,
        cameraPreview = { m -> FramingCameraPreview(viewModel::onFrame, viewModel::onCameraError, m) }
    )
}

@Composable
private fun FramingCameraPreview(onFrame: (Bitmap, Long) -> Unit, onError: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }
    // The front preview is mirrored for display; analysis frames stay un-mirrored.
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    DisposableEffect(owner) {
        controller.bind(owner, previewView.surfaceProvider, onFrame, onError)
        onDispose { controller.unbind() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Stateless framing check. */
@Composable
fun FramingCheckScreen(
    state: FramingUiState,
    cameraGranted: Boolean,
    cameraDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onHandToChild: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable (Modifier) -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Spacing.S4),
        verticalArrangement = Arrangement.spacedBy(Spacing.S3)
    ) {
        val check = state as? FramingUiState.Check
        PageHeader(stringResource(R.string.framing_title)) {
            val override = check?.canOverride == true || state is FramingUiState.Unavailable || (check != null && !cameraGranted)
            if (override && check?.framed != true) {
                TextButton(onClick = onHandToChild) { Text(stringResource(R.string.framing_start_anyway)) }
            }
            Button(
                onClick = onHandToChild,
                enabled = check?.framed == true,
                colors = ButtonDefaults.buttonColors(containerColor = ChildColors.FramingOk)
            ) { Text(stringResource(R.string.framing_hand_to_child)) }
        }
        when (state) {
            FramingUiState.Loading, FramingUiState.Skip -> Skeleton(rows = 3)
            is FramingUiState.Unavailable ->
                Text(
                    stringResource(R.string.framing_unavailable, state.reason),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            is FramingUiState.Check ->
                when {
                    !cameraGranted -> PermissionPanel(cameraDenied, onRequestPermission, onOpenSettings)
                    state.engineError != null ->
                        Text(
                            stringResource(R.string.framing_engine_error, state.engineError),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    else -> {
                        Text(
                            stringResource(guidanceText(state.guidance, state.framed)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Box(Modifier.aspectRatio(PREVIEW_ASPECT)) {
                                cameraPreview(Modifier.fillMaxSize())
                                val color = if (state.framed) ChildColors.FramingOk else ChildColors.FramingWait
                                Canvas(Modifier.fillMaxSize()) { drawSilhouette(state.framing, color) }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun PermissionPanel(cameraDenied: Boolean, onRequestPermission: () -> Unit, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S3), modifier = Modifier.widthIn(max = 480.dp)) {
        Text(stringResource(R.string.framing_permission_needed), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            Button(onClick = onRequestPermission) { Text(stringResource(R.string.diagnostics_grant)) }
            if (cameraDenied) OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.diagnostics_open_settings)) }
        }
    }
}

private fun guidanceText(guidance: FramingGuidance?, framed: Boolean): Int = when {
    framed -> R.string.framing_ready
    guidance == null -> R.string.framing_looking
    else -> when (guidance) {
        FramingGuidance.OK -> R.string.framing_hold_still
        FramingGuidance.NO_PERSON -> R.string.framing_no_person
        FramingGuidance.MOVE_CLOSER -> R.string.framing_move_closer
        FramingGuidance.STEP_BACK -> R.string.framing_step_back
        FramingGuidance.MOVE_LEFT -> R.string.framing_move_left
        FramingGuidance.MOVE_RIGHT -> R.string.framing_move_right
        FramingGuidance.SHOW_HANDS -> R.string.framing_show_hands
    }
}

/** Simple body outline per framing, in normalised preview space (symmetric, so mirroring does not matter). */
private fun DrawScope.drawSilhouette(framing: Framing, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = 6.dp.toPx())
    fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 0.04f) = drawRoundRect(
        color,
        Offset(l * w, t * h),
        Size((r - l) * w, (b - t) * h),
        CornerRadius(radius * w, radius * w),
        style = stroke
    )
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), stroke.width)
    when (framing) {
        Framing.TABLE -> {
            drawCircle(color, 0.09f * h, Offset(0.5f * w, 0.22f * h), style = stroke)
            rect(0.34f, 0.34f, 0.66f, 0.78f)
            line(0.12f, 0.8f, 0.88f, 0.8f) // table edge
        }
        Framing.STANDING -> {
            drawCircle(color, 0.06f * h, Offset(0.5f * w, 0.12f * h), style = stroke)
            rect(0.42f, 0.2f, 0.58f, 0.55f)
            line(0.42f, 0.22f, 0.36f, 0.52f)
            line(0.58f, 0.22f, 0.64f, 0.52f)
            line(0.46f, 0.55f, 0.45f, 0.92f)
            line(0.54f, 0.55f, 0.55f, 0.92f)
        }
        Framing.SINK -> {
            drawCircle(color, 0.08f * h, Offset(0.5f * w, 0.2f * h), style = stroke)
            rect(0.36f, 0.3f, 0.64f, 0.7f)
            rect(0.25f, 0.66f, 0.75f, 0.82f, radius = 0.06f) // basin
        }
    }
}

private const val PREVIEW_ASPECT = 4f / 3f
