package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.camera.CameraController
import kotlinx.coroutines.launch

/**
 * Child-mode host bound to one assessment run for [childId] on [taskId], or the continuation of saved session
 * [sessionId]. Screen pinning and hardware keys are handled by the activity while child mode is on. The adult corner
 * opens the pause sheet; its Exit pauses the session and calls [onExit] (the PIN). The ViewModel is scoped to this
 * nav entry (AppNavigation's ViewModelStore decorator), so every run gets a fresh one. When a next task exists (jacket
 * "both": [thenTaskId] for a new run, the session's saved `nextTaskId` for a continued one), [onNext] fires when the
 * task reward screen (Story 4.9) continues after this run's last step has saved the session `done`.
 */
@Composable
fun ChildModeHost(
    childId: String,
    taskId: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
    thenTaskId: String? = null,
    onNext: (nextTaskId: String) -> Unit = {},
    onSession: (sessionId: String) -> Unit = {},
    viewModel: StepViewModel = hiltViewModel()
) {
    LaunchedEffect(childId, taskId, sessionId) { viewModel.start(childId, taskId, sessionId, thenTaskId) }
    // Leaving composition (PIN screen on top) or the app stopping pauses audio and ticks; return restarts the prompt.
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Report this run's session (new or continued) so the post-PIN summary shows exactly this visit.
    LaunchedEffect(state) { viewModel.sessionId?.let(onSession) }
    val effect by viewModel.effect.collectAsStateWithLifecycle()
    var starTarget by remember { mutableStateOf<Offset?>(null) }
    var characterCentre by remember { mutableStateOf<Offset?>(null) }
    val scope = rememberCoroutineScope()
    val sheetOpen by viewModel.adultSheet.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val cameraWanted by viewModel.cameraWanted.collectAsStateWithLifecycle()
    val cameraUnavailable by viewModel.cameraUnavailable.collectAsStateWithLifecycle()
    if (cameraWanted) BackgroundCamera(viewModel::onFrame, viewModel::onCameraError)
    val nextTaskId by viewModel.nextTaskId.collectAsStateWithLifecycle()
    // Story 4.9: a finished task shows the task reward first, then continues (next chained task, or the calm end).
    var rewardShown by rememberSaveable(childId, taskId, sessionId) { mutableStateOf(false) }
    val finishedSession = viewModel.sessionId.takeIf { completed }
    Box(modifier) {
        if (finishedSession != null && !rewardShown) {
            // Story 4.9: the task reward replaces the step screen (no layered touches).
            TaskRewardRoute(
                childId,
                RewardTask.Assessment(finishedSession),
                onNext = {
                    rewardShown = true
                    nextTaskId?.let(onNext)
                },
                paused = sheetOpen,
                onAdultCorner = viewModel::openAdultSheet
            )
        } else {
            StepScreen(
                state = state,
                onEvent = viewModel::onEvent,
                onAdultCorner = viewModel::openAdultSheet,
                onStart = viewModel::beginTask,
                onStarBarPositioned = { starTarget = it },
                onCharacterCentre = { characterCentre = it }
            )
            RewardEffects(effect, Modifier.fillMaxSize(), starTarget, characterCentre)
        }
        DebugResultStrip()
    }
    if (sheetOpen) {
        AdultPauseSheet(
            paused = state.isPaused,
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

/** Story 3.5: the front camera's analysis stream while a camera-scored step runs; no preview is shown to the child. */
@Composable
private fun BackgroundCamera(onFrame: (Bitmap, Long) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }
    DisposableEffect(owner) {
        controller.bindAnalysis(owner, onFrame, onError)
        onDispose { controller.unbind() }
    }
}
