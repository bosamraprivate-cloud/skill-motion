package com.aitherapist.dls.ui.therapist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.DetailBarActions
import com.aitherapist.dls.ui.components.DetailTopBar
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.LocalDetailBarActions
import com.aitherapist.dls.ui.theme.calmSlide
import com.aitherapist.dls.ui.theme.isRtl
import com.aitherapist.dls.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/** Detail-pane key for the add form; real child ids are UUIDs, so this never collides. */
private const val NEW_CHILD = "new"

/**
 * Children destination: list-detail on a tablet (adaptive), list then detail on a phone.
 * The detail pane shows the add/edit profile form, or a hint when nothing is selected.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ChildrenRoute(
    modifier: Modifier = Modifier,
    onContinue: (childId: String, taskId: String, sessionId: String) -> Unit = { _, _, _ -> },
    onNewSession: (childId: String, taskId: String, thenTaskId: String?) -> Unit = { _, _, _ -> },
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean = { _, _ -> false },
    tabRequest: ProfileTabRequest? = null
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val detailKey = navigator.currentDestination?.contentKey

    BackHandler(enabled = navigator.canNavigateBack()) { scope.launch { navigator.navigateBack() } }

    fun open(key: String) {
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
    }

    // A request for a child's tab (after the session summary) opens that child in the detail pane.
    LaunchedEffect(tabRequest) {
        val request = tabRequest ?: return@LaunchedEffect
        if (detailKey != request.childId) navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, request.childId)
    }

    val listVm: ChildListViewModel = hiltViewModel()
    val listState by listVm.uiState.collectAsStateWithLifecycle()
    val detail: @Composable (String?) -> Unit = { key ->
        ChildDetail(key, onNewSession, onStartTraining, tabRequest, onContinue, onSaved = { savedId ->
            // First save of a new child: replace the "new" entry so Back never reopens the blank form.
            scope.launch {
                navigator.navigateBack()
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, savedId)
            }
        }, onDeleted = { scope.launch { navigator.navigateBack() } })
    }
    val list: @Composable () -> Unit = {
        ChildListRoute(selectedId = detailKey, onSelect = ::open, onAdd = { open(NEW_CHILD) }, onContinue = onContinue, viewModel = listVm)
    }

    // One pane at a time (portrait tablet, phone): our own calm list <-> detail motion and a real top bar on the detail.
    if (navigator.scaffoldDirective.maxHorizontalPartitions == 1) {
        val rtl = isRtl()
        val reduced = rememberReducedMotion()
        val newTitle = stringResource(R.string.child_new_title)
        val profileTitle = stringResource(R.string.child_edit_title)
        AnimatedContent(
            targetState = detailKey,
            transitionSpec = { calmSlide(forward = targetState != null, rtl = rtl, reduced = reduced) },
            contentAlignment = Alignment.TopCenter,
            label = "childrenPane",
            modifier = modifier.fillMaxSize()
        ) { key ->
            if (key == null) {
                list()
            } else {
                Column(Modifier.fillMaxSize()) {
                    val title =
                        if (key == NEW_CHILD) {
                            newTitle
                        } else {
                            (listState as? ChildListUiState.Content)?.children?.firstOrNull { it.id == key }?.name ?: profileTitle
                        }
                    val barActions = remember(key) { DetailBarActions() }
                    DetailTopBar(
                        title = title,
                        onBack = { scope.launch { navigator.navigateBack() } },
                        actions = { barActions.content?.invoke(this) }
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(LocalDetailBarActions provides barActions) { detail(key) }
                    }
                }
            }
        }
        return
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier,
        listPane = { AnimatedPane { list() } },
        detailPane = { AnimatedPane { detail(detailKey) } }
    )
}

/** The detail pane: a hint when nothing is selected, else the add/edit profile of [key]. */
@Composable
private fun ChildDetail(
    key: String?,
    onNewSession: (childId: String, taskId: String, thenTaskId: String?) -> Unit,
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean,
    tabRequest: ProfileTabRequest?,
    onContinue: (childId: String, taskId: String, sessionId: String) -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit
) {
    if (key == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Filled.Face, message = stringResource(R.string.children_select))
        }
        return
    }
    ChildProfileRoute(childId = key.takeUnless { it == NEW_CHILD }, onNewSession = { task, then ->
        if (key != NEW_CHILD) onNewSession(key, task, then)
    }, onStartTraining = onStartTraining, tabRequest = tabRequest, onContinue = { task, session ->
        if (key != NEW_CHILD) onContinue(key, task, session)
    }, onResult = { result ->
        when (result) {
            is ProfileResult.Saved -> if (key != result.childId) onSaved(result.childId)
            ProfileResult.Deleted -> onDeleted()
        }
    })
}
