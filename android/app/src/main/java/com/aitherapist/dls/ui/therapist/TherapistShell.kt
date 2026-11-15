package com.aitherapist.dls.ui.therapist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.SummaryRequest
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Therapist-mode destinations on the start-side rail (agreed IA): Children and Settings only. Diagnostics, Replay &
 * evaluation, Try step screen and Character preview sit under Settings -> Advanced tools (debug builds only).
 */
enum class ShellDestination(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    CHILDREN(R.string.shell_children, Icons.Outlined.Face, Icons.Filled.Face),
    SETTINGS(R.string.shell_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

/**
 * Therapist shell. The rail is the first child of a Row, so it sits on the start side (right in RTL).
 * [destinationContent] renders the stateful destinations; [TherapistShellLayout] stays stateless for previews.
 */
@Composable
fun TherapistShell(
    onOpenSample: () -> Unit,
    modifier: Modifier = Modifier,
    onContinueSession: (childId: String, taskId: String, sessionId: String) -> Unit = { _, _, _ -> },
    onNewSession: (childId: String, taskId: String, thenTaskId: String?) -> Unit = { _, _, _ -> },
    onStartTraining: (childId: String, activityIds: List<String>) -> Boolean = { _, _ -> false },
    pendingSummary: SummaryRequest? = null,
    onSummaryShown: () -> Unit = {},
    networkViewModel: NetworkBannerViewModel = hiltViewModel()
) {
    var selected by rememberSaveable { mutableStateOf(ShellDestination.CHILDREN) }
    val banner by networkViewModel.banner.collectAsStateWithLifecycle()
    // Return from child mode: the session summary first, then the child's profile (Overview, or Report on request).
    var summary by remember { mutableStateOf<SummaryRequest?>(null) }
    var tabRequest by remember { mutableStateOf<ProfileTabRequest?>(null) }
    LaunchedEffect(pendingSummary) {
        val request = pendingSummary ?: return@LaunchedEffect
        selected = ShellDestination.CHILDREN
        // No session of this visit (left before the first step): straight back to the child's Overview.
        if (request.sessionIds.isEmpty()) tabRequest = ProfileTabRequest(request.childId, ProfileTab.OVERVIEW) else summary = request
        onSummaryShown()
    }
    TherapistShellLayout(selected = selected, onSelect = { selected = it }, modifier = modifier, banner = banner) { destination ->
        when (destination) {
            ShellDestination.CHILDREN -> ChildrenRoute(
                onContinue = onContinueSession,
                onNewSession = onNewSession,
                onStartTraining = onStartTraining,
                tabRequest = tabRequest
            )
            ShellDestination.SETTINGS -> SettingsRoute(onOpenSample = onOpenSample)
        }
    }
    summary?.let { request ->
        val child = request.childId
        SessionSummaryDialog(
            childId = child,
            sessionIds = request.sessionIds,
            onOpenReport = {
                summary = null
                tabRequest = ProfileTabRequest(child, ProfileTab.REPORT)
            },
            onDismiss = {
                summary = null
                tabRequest = ProfileTabRequest(child, ProfileTab.OVERVIEW)
            }
        )
    }
}

@Composable
fun TherapistShellLayout(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    modifier: Modifier = Modifier,
    banner: NetworkBanner = NetworkBanner.NONE,
    destinationContent: @Composable (ShellDestination) -> Unit
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight().width(RAIL_WIDTH),
                containerColor = MaterialTheme.colorScheme.surface,
                header = { RailBrand() }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                    ShellDestination.entries.forEach { destination ->
                        RailItem(destination, destination == selected) { onSelect(destination) }
                    }
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NetworkBannerBar(banner)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { destinationContent(selected) }
            }
        }
    }
}

@Composable
private fun RailItem(destination: ShellDestination, selected: Boolean, onClick: () -> Unit) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null) },
        label = {
            Text(
                stringResource(destination.label),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        alwaysShowLabel = true,
        colors =
        NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * Calm, slim network notice for the therapist (never shown in child mode; therapist tokens, not error red). No dismiss
 * button: it slides away by itself when the network is back, and a failed cloud check fades after [PROXY_NOTICE_MS].
 */
@Composable
private fun NetworkBannerBar(banner: NetworkBanner) {
    var shown by remember { mutableStateOf(banner) }
    var visible by remember { mutableStateOf(banner != NetworkBanner.NONE) }
    LaunchedEffect(banner) {
        if (banner == NetworkBanner.NONE) {
            visible = false
            return@LaunchedEffect
        }
        shown = banner
        visible = true
        if (banner == NetworkBanner.PROXY_FAILED) {
            delay(PROXY_NOTICE_MS)
            visible = false
        }
    }
    AnimatedVisibility(visible = visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val offline = shown == NetworkBanner.OFFLINE
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.S4, vertical = Spacing.S2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S2)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    stringResource(if (offline) R.string.network_banner_offline else R.string.network_banner_proxy_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private const val PROXY_NOTICE_MS = 8_000L

/** Skillmotion mark and wordmark at the top of the rail. */
@Composable
private fun RailBrand() {
    Column(
        modifier = Modifier.padding(top = Spacing.S3, bottom = Spacing.S4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_skillmotion_mark),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(Spacing.S1))
        Text(
            stringResource(R.string.shell_brand),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

private val RAIL_WIDTH = 96.dp

@Preview(device = Devices.TABLET)
@Composable
private fun TherapistShellPreview() {
    DlsTheme {
        TherapistShellLayout(selected = ShellDestination.CHILDREN, onSelect = {}, banner = NetworkBanner.OFFLINE) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.Filled.Face, message = stringResource(R.string.children_empty))
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 1280)
@Composable
private fun TherapistShellPortraitPreview() {
    TherapistShellPreview()
}
