package com.aitherapist.dls.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.aitherapist.dls.ui.sample.SampleRoute
import com.aitherapist.dls.ui.therapist.DiagnosticsRoute
import kotlinx.serialization.Serializable

/** Typed navigation key for the therapist Diagnostics screen (start destination). */
@Serializable
data object DiagnosticsKey : NavKey

/** Typed navigation key for the reference sample screen (AD-13). */
@Serializable
data object SampleKey : NavKey

/** Navigation 3 shell: the back stack is plain state holding typed `@Serializable` keys. */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(DiagnosticsKey)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators =
        listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider =
        entryProvider {
            entry<DiagnosticsKey> { DiagnosticsRoute(onOpenSample = { backStack.add(SampleKey) }) }
            entry<SampleKey> { SampleRoute() }
        }
    )
}
