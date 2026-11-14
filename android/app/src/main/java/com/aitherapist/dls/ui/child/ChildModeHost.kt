package com.aitherapist.dls.ui.child

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.camera.CameraController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Child-mode host bound to one assessment run for [childId] on [taskId], or the continuation of saved session
 * [sessionId]. Screen pinning and hardware keys are handled by the activity while child mode is on. The adult corner
 * opens the pause sheet; its Exit pauses the session and calls [onExit] (the PIN). The ViewModel is scoped to this
 * nav entry (AppNavigation's ViewModelStore decorator), so every run gets a fresh one. When a next task exists (jacket
 * "both": [thenTaskId] for a new run, the session's saved `nextTaskId` for a continued one), [onNext] fires a moment after this run's last step has saved the session `done`.
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
    viewModel: StepViewModel = hiltViewModel()
) {
    LaunchedEffect(childId, taskId, sessionId) { viewModel.start(childId, taskId, sessionId, thenTaskId) }
    // Leaving composition (PIN screen on top) or the app stopping pauses audio and ticks; return restarts the prompt.
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheetOpen by viewModel.adultSheet.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val cameraWanted by viewModel.cameraWanted.collectAsStateWithLifecycle()
    val cameraUnavailable by viewModel.cameraUnavailable.collectAsStateWithLifecycle()
    if (cameraWanted) BackgroundCamera(viewModel::onFrame, viewModel::onCameraError)
    val nextTaskId by viewModel.nextTaskId.collectAsStateWithLifecycle()
    LaunchedEffect(completed, nextTaskId) {
        val next = nextTaskId
        if (completed && next != null) {
            delay(NEXT_TASK_DELAY_MS) // the calm end shows briefly before the next task's start screen
            onNext(next)
        }
    }
    Box(modifier) {
        StepScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onAdultCorner = viewModel::openAdultSheet,
            onStart = viewModel::beginTask
        )
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

private const val NEXT_TASK_DELAY_MS = 2_000L

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
