package com.aitherapist.dls.ui.therapist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Devices
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
import com.aitherapist.dls.data.Zone
import com.aitherapist.dls.ui.LocalExternalHop
import com.aitherapist.dls.ui.components.ErrorState
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.ui.theme.Warning
import com.aitherapist.dls.ui.theme.ZoneColors
import kotlin.math.abs

private enum class CameraAccess { CHECKING, GRANTED, DENIED }

/**
 * Stateful zone editor: camera permission and preview around [ZoneEditorScreen]. [onDone] leaves the editor and
 * [doneLabel] names that action (for example "Skip" during setup). Zones are optional.
 */
@Composable
fun ZoneEditorRoute(onDone: () -> Unit, doneLabel: String, viewModel: ZoneEditorViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val externalHop = LocalExternalHop.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Saveable so a rotation never prompts again.
    var access by rememberSaveable { mutableStateOf(CameraAccess.CHECKING) }
    var cameraError by rememberSaveable { mutableStateOf<String?>(null) }
    val granted = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            access = if (ok) CameraAccess.GRANTED else CameraAccess.DENIED
        }
    LaunchedEffect(Unit) {
        when {
            granted() -> access = CameraAccess.GRANTED
            access == CameraAccess.CHECKING -> launcher.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Re-check after returning from system settings; never re-prompts.
        if (access != CameraAccess.GRANTED && granted()) access = CameraAccess.GRANTED
    }
    ZoneEditorScreen(
        state = state,
        cameraGranted = access == CameraAccess.GRANTED,
        cameraDenied = access == CameraAccess.DENIED,
        cameraError = cameraError,
        onEvent = viewModel::onEvent,
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            externalHop()
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        },
        onDone = onDone,
        doneLabel = doneLabel,
        cameraPreview = { modifier, onAspect -> ZoneCameraPreview(onAspect, { cameraError = it }, modifier) }
    )
}

@Composable
private fun ZoneCameraPreview(onAspect: (Float) -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val aspectCallback by rememberUpdatedState(onAspect)
    val errorCallback by rememberUpdatedState(onError)
    DisposableEffect(owner) {
        // Preview only: no frame analysis; the aspect comes once from the preview resolution, on the main thread.
        controller.bindPreview(owner, previewView.surfaceProvider, { aspectCallback(it) }, { errorCallback(it) })
        onDispose { controller.unbind() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
fun ZoneEditorScreen(
    state: ZoneEditorUiState,
    cameraGranted: Boolean,
    cameraDenied: Boolean,
    onEvent: (ZoneEditorEvent) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit,
    doneLabel: String,
    modifier: Modifier = Modifier,
    cameraError: String? = null,
    cameraPreview: @Composable (Modifier, (Float) -> Unit) -> Unit = { _, _ -> }
) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Spacing.S4),
        verticalArrangement = Arrangement.spacedBy(Spacing.S3)
    ) {
        PageHeader(stringResource(R.string.zones_title)) {
            Button(onClick = onDone, shape = RoundedCornerShape(Radius.Sm), modifier = Modifier.heightIn(min = 48.dp)) { Text(doneLabel) }
        }
        when (state) {
            ZoneEditorUiState.Loading -> Skeleton(rows = 3)
            is ZoneEditorUiState.Error ->
                ErrorState(stringResource(R.string.zones_error, state.reason), onRetry = { onEvent(ZoneEditorEvent.Retry) })
            is ZoneEditorUiState.Content -> {
                val camera: @Composable (Modifier) -> Unit = { m ->
                    when {
                        !cameraGranted -> PermissionPanel(cameraDenied, onRequestPermission, onOpenSettings, m)
                        cameraError != null ->
                            Text(
                                stringResource(R.string.zones_camera_error, cameraError),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = m
                            )
                        else -> PreviewArea(state, onEvent, cameraPreview, m)
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (maxWidth < COMPACT_WIDTH) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S3), modifier = Modifier.fillMaxSize()) {
                            camera(Modifier.weight(1f).fillMaxWidth())
                            ZoneList(state, onEvent, Modifier.fillMaxWidth().weight(1f))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S4), modifier = Modifier.fillMaxSize()) {
                            camera(Modifier.weight(1f))
                            ZoneList(state, onEvent, Modifier.width(320.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPanel(cameraDenied: Boolean, onRequestPermission: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S3), modifier = modifier.widthIn(max = 480.dp)) {
        Text(stringResource(R.string.zones_permission_needed), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            Button(onClick = onRequestPermission) { Text(stringResource(R.string.diagnostics_grant)) }
            if (cameraDenied) {
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.diagnostics_open_settings)) }
            }
        }
    }
}

@Composable
private fun PreviewArea(
    state: ZoneEditorUiState.Content,
    onEvent: (ZoneEditorEvent) -> Unit,
    cameraPreview: @Composable (Modifier, (Float) -> Unit) -> Unit,
    modifier: Modifier
) {
    var aspect by rememberSaveable { mutableFloatStateOf(DEFAULT_ASPECT) }
    var downAt by remember { mutableStateOf<Offset?>(null) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val drawingColor = ZoneColors.of(state.selected)
    val measurer = rememberTextMeasurer()
    val labels = ZONE_NAMES.associateWith { zoneLabel(it) }
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.aspectRatio(aspect).clip(RoundedCornerShape(Radius.Md))) {
            cameraPreview(Modifier.fillMaxSize()) { a -> if (a > 0f && a != aspect) aspect = a }
            Canvas(
                modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // The rectangle starts at the touch-down point, not where the drag slop was passed.
                        awaitEachGesture { downAt = awaitFirstDown(requireUnconsumed = false).position }
                    }.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                dragStart = downAt ?: it
                                dragEnd = it
                            },
                            onDrag = { change, _ -> dragEnd = change.position },
                            onDragEnd = {
                                val s = dragStart
                                val e = dragEnd
                                if (s != null && e != null) {
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    onEvent(ZoneEditorEvent.Drawn(s.x / w, s.y / h, e.x / w, e.y / h))
                                }
                                dragStart = null
                                dragEnd = null
                            },
                            onDragCancel = {
                                dragStart = null
                                dragEnd = null
                            }
                        )
                    }
            ) {
                // The selected zone is drawn last so its handles sit on top.
                state.zones.entries.sortedBy { it.key == state.selected }.forEach { (name, zone) ->
                    drawZone(zone, ZoneColors.of(name), name == state.selected, measurer.measure(labels[name] ?: name, labelStyle))
                }
                val s = dragStart
                val e = dragEnd
                if (s != null && e != null) {
                    drawRect(
                        drawingColor,
                        Offset(minOf(s.x, e.x), minOf(s.y, e.y)),
                        Size(abs(e.x - s.x), abs(e.y - s.y)),
                        style = Stroke(width = SELECTED_STROKE_DP.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
                    )
                }
            }
        }
    }
}

/** One saved zone: tinted fill, outline, a name tag, and corner handles when [selected]. */
private fun DrawScope.drawZone(zone: Zone, color: Color, selected: Boolean, label: TextLayoutResult) {
    val r = ZoneMath.toDisplay(zone)
    val topLeft = Offset(r.left * size.width, r.top * size.height)
    val rectSize = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height)
    val stroke = (if (selected) SELECTED_STROKE_DP else STROKE_DP).dp.toPx()
    drawRect(color.copy(alpha = if (selected) SELECTED_FILL_ALPHA else FILL_ALPHA), topLeft, rectSize)
    drawRect(color, topLeft, rectSize, style = Stroke(width = stroke))
    val pad = 6.dp.toPx()
    val tagSize = Size(label.size.width + 2 * pad, label.size.height + pad)
    drawRoundRect(color, topLeft, tagSize, CornerRadius(4.dp.toPx()))
    drawText(label, topLeft = Offset(topLeft.x + pad, topLeft.y + pad / 2))
    if (selected) {
        val handle = HANDLE_DP.dp.toPx()
        listOf(
            topLeft,
            Offset(topLeft.x + rectSize.width, topLeft.y),
            Offset(topLeft.x, topLeft.y + rectSize.height),
            Offset(topLeft.x + rectSize.width, topLeft.y + rectSize.height)
        ).forEach { corner ->
            drawCircle(Color.White, handle, corner)
            drawCircle(color, handle, corner, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

@Composable
private fun ZoneList(state: ZoneEditorUiState.Content, onEvent: (ZoneEditorEvent) -> Unit, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.S2),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.zones_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ZONE_NAMES.forEach { name -> ZoneRow(name, state, onEvent) }
        if (state.tooSmall) Text(stringResource(R.string.zones_too_small), color = Warning, style = MaterialTheme.typography.bodyMedium)
        if (state.saveFailed) {
            Text(stringResource(R.string.zones_save_failed), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { onEvent(ZoneEditorEvent.RetrySave) }, enabled = !state.busy) {
                Text(stringResource(R.string.zones_try_again))
            }
        }
        if (state.saved) {
            Text(
                stringResource(R.string.settings_saved),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** One zone as a selectable card: colour swatch, name, and either its Clear action or a calm empty state. */
@Composable
private fun ZoneRow(name: String, state: ZoneEditorUiState.Content, onEvent: (ZoneEditorEvent) -> Unit) {
    val selected = name == state.selected
    val drawn = name in state.zones
    val color = ZoneColors.of(name)
    Surface(
        shape = RoundedCornerShape(Radius.Md),
        color = if (selected) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
        modifier =
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton) { onEvent(ZoneEditorEvent.Select(name)) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S2).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
        ) {
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(if (drawn) color else color.copy(alpha = 0.25f)))
            Column(Modifier.weight(1f)) {
                Text(zoneLabel(name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(
                        when {
                            drawn -> R.string.zones_drawn
                            selected -> R.string.zones_empty_selected
                            else -> R.string.zones_empty
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (drawn) {
                TextButton(onClick = { onEvent(ZoneEditorEvent.Clear(name)) }, enabled = !state.busy) {
                    Text(stringResource(R.string.zones_clear))
                }
            }
        }
    }
}

@Composable
internal fun zoneLabel(name: String): String = when (name) {
    "mat" -> stringResource(R.string.zones_mat)
    "sink" -> stringResource(R.string.zones_sink)
    "place" -> stringResource(R.string.zones_place)
    else -> name
}

private const val DEFAULT_ASPECT = 4f / 3f
private const val STROKE_DP = 2
private const val SELECTED_STROKE_DP = 3
private const val HANDLE_DP = 7
private const val FILL_ALPHA = 0.14f
private const val SELECTED_FILL_ALPHA = 0.24f
private val COMPACT_WIDTH = 600.dp

@Preview(device = Devices.TABLET)
@Composable
private fun ZoneEditorPreview() {
    DlsTheme {
        ZoneEditorScreen(
            ZoneEditorUiState.Content(zones = mapOf("mat" to Zone(0.2f, 0.6f, 0.7f, 0.9f)), tooSmall = true),
            cameraGranted = true,
            cameraDenied = false,
            onEvent = {},
            onRequestPermission = {},
            onOpenSettings = {},
            onDone = {},
            doneLabel = "Done"
        )
    }
}
