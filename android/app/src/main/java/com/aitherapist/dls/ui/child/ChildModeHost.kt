package com.aitherapist.dls.ui.child

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Child-mode host bound to one assessment run for [childId] on [taskId]. Screen pinning and hardware keys are
 * handled by the activity while child mode is on; [onAdultCorner] opens the PIN to leave. The ViewModel is scoped to
 * this nav entry (AppNavigation's ViewModelStore decorator), so every run gets a fresh one and a new session.
 */
@Composable
fun ChildModeHost(
    childId: String,
    taskId: String,
    onAdultCorner: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StepViewModel = hiltViewModel()
) {
    LaunchedEffect(childId, taskId) { viewModel.start(childId, taskId) }
    // Leaving composition (PIN screen on top) or the app stopping pauses audio and ticks; return restarts the prompt.
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StepScreen(state = state, onEvent = viewModel::onEvent, onAdultCorner = onAdultCorner, modifier = modifier)
}
