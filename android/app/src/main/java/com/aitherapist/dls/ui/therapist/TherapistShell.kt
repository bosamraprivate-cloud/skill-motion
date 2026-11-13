package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.theme.DlsTheme

/** Therapist-mode destinations on the start-side rail (UX: Children, Replay & evaluation, Diagnostics, Settings). */
enum class ShellDestination(val label: Int, val icon: ImageVector) {
    CHILDREN(R.string.shell_children, Icons.Filled.Face),
    REPLAY(R.string.shell_replay, Icons.Filled.PlayArrow),
    DIAGNOSTICS(R.string.shell_diagnostics, Icons.Filled.Build),
    SETTINGS(R.string.shell_settings, Icons.Filled.Settings)
}

/**
 * Therapist shell. The rail is the first child of a Row, so it sits on the start side (right in RTL).
 * [destinationContent] renders the stateful destinations; [TherapistShellLayout] stays stateless for previews.
 */
@Composable
fun TherapistShell(
    onOpenSample: () -> Unit,
    onOpenCharacterPreview: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenChildMode: (childId: String, taskId: String) -> Unit = { _, _ -> }
) {
    var selected by rememberSaveable { mutableStateOf(ShellDestination.CHILDREN) }
    TherapistShellLayout(selected = selected, onSelect = { selected = it }, modifier = modifier) { destination ->
        when (destination) {
            ShellDestination.CHILDREN -> ChildrenRoute()
            ShellDestination.REPLAY -> PlaceholderDestination(Icons.Filled.PlayArrow, stringResource(R.string.replay_empty))
            ShellDestination.DIAGNOSTICS -> DiagnosticsRoute(
                onOpenSample = onOpenSample,
                onOpenCharacterPreview = onOpenCharacterPreview,
                onOpenChildMode = onOpenChildMode
            )
            ShellDestination.SETTINGS -> SettingsRoute()
        }
    }
}

@Composable
fun TherapistShellLayout(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    modifier: Modifier = Modifier,
    destinationContent: @Composable (ShellDestination) -> Unit
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                ShellDestination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = destination == selected,
                        onClick = { onSelect(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) }
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { destinationContent(selected) }
        }
    }
}

@Composable
private fun PlaceholderDestination(icon: ImageVector, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState(icon = icon, message = message) }
}

@Preview(device = Devices.TABLET)
@Composable
private fun TherapistShellPreview() {
    DlsTheme {
        TherapistShellLayout(selected = ShellDestination.CHILDREN, onSelect = {}) {
            PlaceholderDestination(Icons.Filled.Face, stringResource(R.string.children_empty))
        }
    }
}
