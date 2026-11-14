package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.camera.CameraController
import com.aitherapist.dls.catalog.Criterion
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.training.TrainingState
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import java.text.NumberFormat
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Child-mode training run (Story 4.6) for [childId] over [activityIds] in order. Counted activities run the background
 * camera (no preview) into the counter; guided ones show the activity, a timer and Done. The adult corner opens the
 * pause sheet; Exit interrupts (nothing saved for the current activity) and calls [onExit] (the PIN). The calm end
 * screen hands back to the therapist through [onExit] too.
 */
@Composable
fun TrainingRoute(
    childId: String,
    activityIds: List<String>,
    runId: String,
    onExit: () -> Unit,
    viewModel: TrainingViewModel = hiltViewModel()
) {
    LaunchedEffect(childId, activityIds, runId) { viewModel.start(childId, activityIds, runId) }
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraWanted by viewModel.cameraWanted.collectAsStateWithLifecycle()
    val sheetOpen by viewModel.adultSheet.collectAsStateWithLifecycle()
    val cameraUnavailable by viewModel.cameraUnavailable.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (cameraWanted) TrainingCamera(viewModel::onFrame, viewModel::onCameraError)
    TrainingScreen(state = state, onDone = viewModel::done, onAdultCorner = viewModel::openAdultSheet, onFinish = onExit)
    if (sheetOpen) {
        val paused = (state as? TrainingUiState.Content)?.training?.paused == true
        AdultPauseSheet(
            paused = paused,
            cameraUnavailable = cameraUnavailable,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onExit = {
                scope.launch {
                    viewModel.exit()
                    onExit()
                }
            },
            onDismiss = viewModel::dismissAdultSheet
        )
    }
}

@Composable
fun TrainingScreen(
    state: TrainingUiState,
    onDone: () -> Unit,
    onAdultCorner: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(ChildColors.Canvas).padding(Spacing.S4)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val content = state as? TrainingUiState.Content
            if (content != null && !content.training.ended) {
                Text(
                    stringResource(R.string.training_progress, digits(content.training.index + 1), digits(content.training.total)),
                    style = ChildType.Button,
                    color = ChildColors.InkSoft
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { TrainingAdultCorner(onAdultCorner) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (state) {
                TrainingUiState.Loading -> Unit
                TrainingUiState.Empty -> EndPanel(stringResource(R.string.training_empty), onFinish)
                is TrainingUiState.Content ->
                    if (state.training.ended) {
                        EndPanel(stringResource(R.string.training_end), onFinish)
                    } else {
                        ActivityPanel(state, onDone)
                    }
            }
        }
    }
}

@Composable
private fun ActivityPanel(state: TrainingUiState.Content, onDone: () -> Unit) {
    val t = state.training
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.S5), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.S4)
        ) {
            Text(t.activity?.textAr.orEmpty(), style = ChildType.Instruction, color = ChildColors.Ink, textAlign = TextAlign.Center)
            val criterion = t.criterion
            if (criterion == null) GuidedTimer(t) else Counter(t, criterion)
        }
        DoneButton(enabled = state.doneReady && !t.paused, onClick = onDone)
    }
}

@Composable
private fun GuidedTimer(t: TrainingState) {
    val total = (t.elapsedMs / MS_PER_SEC).toInt()
    Box(Modifier.size(RING_SIZE).background(ChildColors.Surface, CircleShape), contentAlignment = Alignment.Center) {
        Text("${digits(total / SECONDS_PER_MIN)}:${digits(total % SECONDS_PER_MIN, 2)}", style = ChildType.Count, color = ChildColors.Ink)
    }
}

@Composable
private fun Counter(t: TrainingState, criterion: Criterion) {
    val label = stringResource(R.string.training_counter_label)
    when (criterion) {
        is Criterion.Reps ->
            if (criterion.perSide) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
                    listOf(Side.RIGHT to R.string.training_side_right, Side.LEFT to R.string.training_side_left).forEach { (side, name) ->
                        val value = t.counts[side] ?: 0
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(name), style = ChildType.Button, color = ChildColors.InkSoft)
                            Ring(
                                value.toFloat() / criterion.target,
                                stringResource(R.string.training_of, digits(value), digits(criterion.target)),
                                label,
                                RING_SIZE_SMALL
                            )
                        }
                    }
                }
            } else {
                Ring(
                    t.count.toFloat() / criterion.target,
                    stringResource(R.string.training_of, digits(t.count), digits(criterion.target)),
                    label,
                    RING_SIZE
                )
            }
        is Criterion.Hold -> {
            val target = criterion.target.roundToInt().coerceAtLeast(1)
            val held = if (t.targetReached) target else t.holdSec.toInt().coerceAtMost(target)
            Ring(
                if (t.targetReached) 1f else (t.holdSec / criterion.target).toFloat(),
                stringResource(R.string.training_of, digits(held), digits(target)),
                label,
                RING_SIZE
            )
        }
    }
}

@Composable
private fun Ring(progress: Float, text: String, label: String, size: Dp) {
    Box(Modifier.size(size).semantics { contentDescription = "$label $text" }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = ChildColors.Done,
            trackColor = ChildColors.StarEmpty,
            strokeWidth = 18.dp
        )
        Text(text, style = if (size == RING_SIZE) ChildType.Title else ChildType.Button, color = ChildColors.Ink)
    }
}

@Composable
private fun DoneButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.Lg),
        colors = ButtonDefaults.buttonColors(containerColor = ChildColors.Done, disabledContainerColor = ChildColors.ScheduleDone),
        modifier = Modifier.sizeIn(minWidth = 220.dp, minHeight = 160.dp)
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.child_done), style = ChildType.Button, modifier = Modifier.padding(start = Spacing.S2))
    }
}

@Composable
private fun EndPanel(message: String, onFinish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S5)) {
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = ChildColors.Done, modifier = Modifier.size(120.dp))
        Text(message, style = ChildType.Title, color = ChildColors.Ink, textAlign = TextAlign.Center)
        Button(
            onClick = onFinish,
            shape = RoundedCornerShape(Radius.Lg),
            colors = ButtonDefaults.buttonColors(containerColor = ChildColors.Help),
            modifier = Modifier.sizeIn(minWidth = 220.dp, minHeight = 120.dp)
        ) { Text(stringResource(R.string.training_back_to_adult), style = ChildType.Button) }
    }
}

/** Arabic-Indic digits in child mode (app locale ar-EG). */
@Composable
private fun digits(value: Int, minDigits: Int = 1): String {
    val locale = LocalConfiguration.current.locales[0]
    return NumberFormat.getIntegerInstance(locale).apply {
        minimumIntegerDigits = minDigits
        isGroupingUsed = false
    }.format(value)
}

/** Small low-contrast target; only a 2 s press opens the adult pause sheet (same gesture as the step screen). */
@Composable
private fun TrainingAdultCorner(onLongPress: () -> Unit) {
    val label = stringResource(R.string.child_adult_corner)
    Box(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val ended =
                        withTimeoutOrNull(ADULT_HOLD_MS) {
                            waitForUpOrCancellation()
                            true
                        }
                    if (ended == null) onLongPress()
                }
            },
        contentAlignment = Alignment.Center
    ) { Box(Modifier.size(16.dp).background(ChildColors.StarEmpty, CircleShape)) }
}

/** The front camera's analysis stream while a counted activity runs; released when it leaves. No preview. */
@Composable
private fun TrainingCamera(onFrame: (Bitmap, Long) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }
    DisposableEffect(owner) {
        controller.bindAnalysis(owner, onFrame, onError)
        onDispose { controller.unbind() }
    }
}

private val RING_SIZE = 320.dp
private val RING_SIZE_SMALL = 220.dp
private const val ADULT_HOLD_MS = 2_000L
private const val MS_PER_SEC = 1_000L
private const val SECONDS_PER_MIN = 60
