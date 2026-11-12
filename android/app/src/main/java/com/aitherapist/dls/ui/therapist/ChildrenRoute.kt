package com.aitherapist.dls.ui.therapist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.EmptyState
import kotlinx.coroutines.launch

/** Detail-pane key for the add form; real child ids are UUIDs, so this never collides. */
private const val NEW_CHILD = "new"

/**
 * Children destination: list-detail on a tablet (adaptive), list then detail on a phone.
 * The detail pane shows the add/edit profile form, or a hint when nothing is selected.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ChildrenRoute(modifier: Modifier = Modifier) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val detailKey = navigator.currentDestination?.contentKey

    BackHandler(enabled = navigator.canNavigateBack()) { scope.launch { navigator.navigateBack() } }

    fun open(key: String) {
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                ChildListRoute(selectedId = detailKey, onSelect = ::open, onAdd = { open(NEW_CHILD) })
            }
        },
        detailPane = {
            AnimatedPane {
                when (val key = detailKey) {
                    null ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(icon = Icons.Filled.Face, message = stringResource(R.string.children_select))
                        }
                    else ->
                        ChildProfileRoute(childId = key.takeUnless { it == NEW_CHILD }, onResult = { result ->
                            when (result) {
                                // First save of a new child: replace the "new" entry so Back never reopens the blank form.
                                is ProfileResult.Saved -> if (key != result.childId) {
                                    scope.launch {
                                        navigator.navigateBack()
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, result.childId)
                                    }
                                }
                                ProfileResult.Deleted -> scope.launch { navigator.navigateBack() }
                            }
                        })
                }
            }
        }
    )
}
