package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    // Story 4.9: the end of the run shows the task reward first, then the calm end screen.
    var rewardShown by rememberSaveable(runId) { mutableStateOf(false) }
    if ((state as? TrainingUiState.Content)?.training?.ended == true && !rewardShown) {
        TaskRewardRoute(
            childId,
            RewardTask.Training(runId),
            onNext = { rewardShown = true },
            paused = sheetOpen,
            onAdultCorner = viewModel::openAdultSheet
        )
    } else {
        TrainingScreen(state = state, onDone = viewModel::done, onAdultCorner = viewModel::openAdultSheet, onFinish = onExit)
    }
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
    val content = state as? TrainingUiState.Content
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(ChildColors.Canvas).padding(Spacing.S5)) {
            // Top band (64dp) as on the step screen: equal side zones keep the activity schedule centred.
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f))
                if (content != null && !content.training.ended && content.training.total > 1) {
                    val label =
                        stringResource(R.string.training_progress, digits(content.training.index + 1), digits(content.training.total))
                    StepSchedule(
                        current = content.training.index,
                        count = content.training.total,
                        modifier = Modifier.semantics { contentDescription = label }
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
        // Adult pause: the same calm dim as the step screen; it takes no touches.
        if (content?.training?.paused == true) {
            Box(Modifier.fillMaxSize().background(ChildColors.Ink.copy(alpha = PAUSE_DIM)))
        }
    }
}

/**
 * One activity: the instruction card across the top, the counter ring (or timer) centred in the main area, and the
 * Done area in the bottom start corner, as on the step screen.
 */
@Composable
private fun ActivityPanel(state: TrainingUiState.Content, onDone: () -> Unit) {
    val t = state.training
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.S4)) {
        Box(
            Modifier.fillMaxWidth().background(ChildColors.Surface, RoundedCornerShape(Radius.Lg)).padding(
                horizontal = Spacing.S5,
                vertical = Spacing.S4
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                t.activity?.textAr.orEmpty(),
                style = ChildType.Instruction,
                color = ChildColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val criterion = t.criterion
            Box(Modifier.align(Alignment.Center)) { if (criterion == null) GuidedTimer(t) else Counter(t, criterion) }
            Box(Modifier.align(Alignment.BottomStart)) { DoneButton(enabled = state.doneReady && !t.paused, onClick = onDone) }
        }
    }
}

/** Guided activity: elapsed time in a calm circle (counts up, never down at the child). */
@Composable
private fun GuidedTimer(t: TrainingState) {
    val total = (t.elapsedMs / MS_PER_SEC).toInt()
    Box(
        Modifier
            .size(RING_SIZE)
            .background(ChildColors.Surface, CircleShape)
            .border(RING_STROKE, ChildColors.ScheduleDone, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${digits(total / SECONDS_PER_MIN)}:${digits(total % SECONDS_PER_MIN, 2)}",
            style = ChildType.Count,
            color = ChildColors.Ink
        )
    }
}

@Composable
private fun Counter(t: TrainingState, criterion: Criterion) {
    val label = stringResource(R.string.training_counter_label)
    when (criterion) {
        is Criterion.Reps ->
            if (criterion.perSide) {
                // Two equal columns, each with its side word above an identical ring: balanced around the centre.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S7), verticalAlignment = Alignment.CenterVertically) {
                    listOf(Side.RIGHT to R.string.training_side_right, Side.LEFT to R.string.training_side_left).forEach { (side, name) ->
                        val value = t.counts[side] ?: 0
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                            Text(stringResource(name), style = ChildType.Button, color = ChildColors.InkSoft)
                            Ring(value.toFloat() / criterion.target, value, criterion.target, label, RING_SIZE_SMALL)
                        }
                    }
                }
            } else {
                Ring(t.count.toFloat() / criterion.target, t.count, criterion.target, label, RING_SIZE)
            }
        is Criterion.Hold -> {
            val target = criterion.target.roundToInt().coerceAtLeast(1)
            val held = if (t.targetReached) target else t.holdSec.toInt().coerceAtMost(target)
            Ring(if (t.targetReached) 1f else (t.holdSec / criterion.target).toFloat(), held, target, label, RING_SIZE)
        }
    }
}

/** Sage progress ring; the count is large in the centre with "of target" under it ("٣ من ١٠" style). */
@Composable
private fun Ring(progress: Float, value: Int, target: Int, label: String, size: Dp) {
    val spoken = stringResource(R.string.training_of, digits(value), digits(target))
    Box(Modifier.size(size).semantics { contentDescription = "$label $spoken" }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = ChildColors.Done,
            trackColor = ChildColors.StarEmpty,
            strokeWidth = if (size == RING_SIZE) RING_STROKE else RING_STROKE_SMALL,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digits(value), style = ChildType.Count, color = ChildColors.Ink)
            Text(stringResource(R.string.training_of_target, digits(target)), style = ChildType.Button, color = ChildColors.InkSoft)
        }
    }
}

/** Done area as on the step screen: sage, check icon plus the word, at least 220 x 160 dp, soft 2dp shadow. */
@Composable
private fun DoneButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.Lg),
        colors =
        ButtonDefaults.buttonColors(
            containerColor = ChildColors.Done,
            contentColor = Color.White,
            disabledContainerColor = ChildColors.ScheduleDone,
            disabledContentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        modifier = Modifier.sizeIn(minWidth = 280.dp, minHeight = 160.dp)
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.child_done), style = ChildType.Button, modifier = Modifier.padding(start = Spacing.S2))
    }
}

/** The same calm end for every training run: a soft heart, one sentence, and the hand-back button. */
@Composable
private fun EndPanel(message: String, onFinish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S5)) {
        Box(Modifier.size(200.dp).background(ChildColors.Surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = ChildColors.Done, modifier = Modifier.size(112.dp))
        }
        Text(message, style = ChildType.Title, color = ChildColors.Ink, textAlign = TextAlign.Center)
        ChildBigButton(stringResource(R.string.training_back_to_adult), ChildColors.Help, onFinish, icon = Icons.Filled.Face)
    }
}

/** Arabic-Indic digits in child mode. */
private fun digits(value: Int, minDigits: Int = 1): String = childNumber(value, minDigits)

/** Small low-contrast target; only a 2 s press opens the adult pause sheet (same gesture as the step screen). */
@Composable
internal fun TrainingAdultCorner(onLongPress: () -> Unit) {
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

private val RING_SIZE = 360.dp
private val RING_SIZE_SMALL = 280.dp
private val RING_STROKE = 22.dp
private val RING_STROKE_SMALL = 18.dp
private const val PAUSE_DIM = 0.45f
private const val ADULT_HOLD_MS = 2_000L
private const val MS_PER_SEC = 1_000L
private const val SECONDS_PER_MIN = 60
