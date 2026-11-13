package com.aitherapist.dls.ui.child

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Child-mode host bound to one assessment run for [childId] on [taskId], or the continuation of saved session
 * [sessionId]. Screen pinning and hardware keys are handled by the activity while child mode is on. The adult corner
 * opens the pause sheet; its Exit pauses the session and calls [onExit] (the PIN). The ViewModel is scoped to this
 * nav entry (AppNavigation's ViewModelStore decorator), so every run gets a fresh one. When [hasNext] (jacket "both"),
 * [onNext] fires a moment after this run's last step has saved the session `done`.
 */
@Composable
fun ChildModeHost(
    childId: String,
    taskId: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
    hasNext: Boolean = false,
    onNext: () -> Unit = {},
    viewModel: StepViewModel = hiltViewModel()
) {
    LaunchedEffect(childId, taskId, sessionId) { viewModel.start(childId, taskId, sessionId) }
    // Leaving composition (PIN screen on top) or the app stopping pauses audio and ticks; return restarts the prompt.
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheetOpen by viewModel.adultSheet.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    LaunchedEffect(completed, hasNext) {
        if (completed && hasNext) {
            delay(NEXT_TASK_DELAY_MS) // the calm end shows briefly before the next task's start screen
            onNext()
        }
    }
    StepScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onAdultCorner = viewModel::openAdultSheet,
        modifier = modifier,
        onStart = viewModel::beginTask
    )
    if (sheetOpen) {
        AdultPauseSheet(
            paused = state.isPaused,
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
