package com.aitherapist.dls.ui.therapist

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.ui.child.CharacterView
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.ui.theme.calmSlide
import com.aitherapist.dls.ui.theme.isForwardTab
import com.aitherapist.dls.ui.theme.isPortrait
import com.aitherapist.dls.ui.theme.isRtl
import com.aitherapist.dls.ui.theme.rememberReducedMotion
import java.util.Locale

/**
 * New session for one child: domain tabs across the top, an adaptive grid of task cards below
 * (jacket: on, off or both), Start/Cancel pinned at the bottom; [onStart] opens child mode.
 * Each card shows the first step's still for [character]'s variant, or the character's idle still when it is missing.
 */
@Composable
fun NewSessionDialog(
    onStart: (taskId: String, thenTaskId: String?) -> Unit,
    onDismiss: () -> Unit,
    character: ChildCharacter? = null,
    viewModel: NewSessionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = state as? NewSessionUiState.Content
    val variant = character?.name?.lowercase(Locale.ROOT)
    val canStart = content?.selected?.choiceReady(content.choice, variant) == true
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val expanded = maxWidth >= EXPANDED_MIN_WIDTH
            val sizeModifier = if (expanded) {
                Modifier.fillMaxWidth(EXPANDED_FRACTION).fillMaxHeight(EXPANDED_FRACTION).widthIn(max = DIALOG_MAX_WIDTH)
            } else {
                Modifier.fillMaxSize().padding(Spacing.S3)
            }
            Surface(
                shape = RoundedCornerShape(Radius.Lg),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp, // DESIGN.md: dialogs use Material 3 level 3
                modifier = sizeModifier
            ) {
                Column(Modifier.padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                    Text(stringResource(R.string.new_session_title), style = MaterialTheme.typography.headlineMedium)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (val s = state) {
                            NewSessionUiState.Loading -> Skeleton(rows = 4)
                            is NewSessionUiState.Error -> Text(stringResource(R.string.new_session_error, s.reason))
                            is NewSessionUiState.Content -> TaskPicker(s, character, viewModel::onEvent)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Start / Cancel stay pinned below the scrolling grid.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S2, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss, modifier = Modifier.height(BUTTON_HEIGHT)) {
                            Text(stringResource(R.string.new_session_cancel))
                        }
                        Button(
                            enabled = canStart,
                            shape = RoundedCornerShape(Radius.Sm),
                            modifier = Modifier.height(BUTTON_HEIGHT).widthIn(min = START_MIN_WIDTH),
                            onClick = { viewModel.plan()?.let { (first, then) -> onStart(first, then) } }
                        ) { Text(stringResource(R.string.new_session_start)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskPicker(s: NewSessionUiState.Content, character: ChildCharacter?, onEvent: (NewSessionEvent) -> Unit) {
    val variant = character?.name?.lowercase(Locale.ROOT)
    // Ready tasks first; areas with nothing ready yet still show, last, so the therapist sees what is coming.
    val groups = s.byAreaReadyFirst(variant)
    if (groups.isEmpty()) return
    // Open on the tab holding the current selection, else the first domain.
    val initialTab = groups.indexOfFirst { (_, options) -> options.any { it.id == s.selectedId } }.coerceAtLeast(0)
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val current = tab.coerceIn(0, groups.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        val tabs = @Composable {
            groups.forEachIndexed { index, (area, _) ->
                Tab(
                    selected = index == current,
                    onClick = { tab = index },
                    modifier = Modifier.height(BUTTON_HEIGHT),
                    text = { Text(s.areaNames[area] ?: area, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
                )
            }
        }
        // Portrait: fixed equal-width domain tabs when they fit; otherwise (and in landscape) the scrolling strip.
        if (isPortrait() && groups.size <= MAX_FIXED_TABS) {
            PrimaryTabRow(selectedTabIndex = current) { tabs() }
        } else {
            PrimaryScrollableTabRow(selectedTabIndex = current, edgePadding = 0.dp) { tabs() }
        }
        val rtl = isRtl()
        val reduced = rememberReducedMotion()
        AnimatedContent(
            targetState = current,
            transitionSpec = { calmSlide(isForwardTab(initialState, targetState), rtl, reduced) },
            contentAlignment = Alignment.TopCenter,
            label = "domainTab"
        ) { shown ->
            TaskGrid(groups[shown].second, s, character, onEvent)
        }
    }
}

/** Domain tabs above this count fall back to a scrolling strip even in portrait. */
private const val MAX_FIXED_TABS = 4

@Composable
private fun TaskGrid(
    options: List<TaskOption>,
    s: NewSessionUiState.Content,
    character: ChildCharacter?,
    onEvent: (NewSessionEvent) -> Unit
) {
    val selected = s.selected
    val variant = character?.name?.lowercase(Locale.ROOT)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TILE_MIN_WIDTH),
        contentPadding = PaddingValues(vertical = Spacing.S3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4), // DESIGN.md: 24dp gutters
        verticalArrangement = Arrangement.spacedBy(Spacing.S4),
        modifier = Modifier.fillMaxSize()
    ) {
        items(options, key = { it.id }) { option ->
            val ready = option.isReady(variant)
            TaskTile(option, character, option.id == s.selectedId, ready) { onEvent(NewSessionEvent.Select(option.id, variant)) }
        }
        if (selected != null && selected.isPair && options.any { it.id == selected.id }) {
            item(span = { GridItemSpan(maxLineSpan) }) { PairChoiceRow(selected, s.choice, variant, onEvent) }
        }
    }
}

@Composable
private fun TaskTile(option: TaskOption, character: ChildCharacter?, selected: Boolean, ready: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val variant = character?.name?.lowercase(Locale.ROOT)
    val picture = variant?.let { option.pictures[it] }
    val name = if (option.isPair) "${option.nameAr} / ${option.offNameAr}" else option.nameAr
    val soon = stringResource(R.string.new_session_coming_soon)
    val label = when {
        !ready -> "$name, $soon"
        selected -> stringResource(R.string.new_session_task_selected, name)
        else -> name
    }
    // Not ready (step clips missing for this character): greyed, "Coming soon" badge, cannot be selected.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (ready) 1f else NOT_READY_ALPHA)
            .selectable(selected = selected, enabled = ready, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        shape = RoundedCornerShape(Radius.Md),
        // Selection = thicker primary border + check icon, not colour alone. No shadow (therapist card).
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.primaryContainer else colors.surface)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TILE_PICTURE_HEIGHT)
                .padding(Spacing.S2)
                .background(ChildColors.ClipBackdrop, RoundedCornerShape(Radius.Sm))
        ) {
            CharacterView(
                clip = if (picture == null) character?.let(::characterStillPath) else null,
                still = picture,
                loop = false,
                stillMode = true,
                modifier = Modifier.fillMaxSize().padding(Spacing.S1)
            )
            if (!ready) {
                Surface(
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                    shape = RoundedCornerShape(Radius.Sm),
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.S2)
                ) {
                    Text(
                        soon,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1)
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.align(
                        Alignment.TopStart
                    ).padding(Spacing.S2).background(colors.surface, CircleShape).size(Spacing.S5)
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.S3, end = Spacing.S3, bottom = Spacing.S3, top = Spacing.S1)
        )
    }
}

@Composable
private fun PairChoiceRow(option: TaskOption, choice: PairChoice, variant: String?, onEvent: (NewSessionEvent) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.S2)) {
        PairChoice.entries.forEachIndexed { index, c ->
            SegmentedButton(
                selected = choice == c,
                onClick = { onEvent(NewSessionEvent.Choose(c)) },
                // A half whose step clips are missing cannot be started (coming soon).
                enabled = option.choiceReady(c, variant),
                shape = SegmentedButtonDefaults.itemShape(index, PairChoice.entries.size)
            ) {
                Text(
                    when (c) {
                        PairChoice.ON -> option.nameAr
                        PairChoice.OFF -> option.offNameAr.orEmpty()
                        PairChoice.BOTH -> stringResource(R.string.new_session_both)
                    }
                )
            }
        }
    }
}

private val DIALOG_MAX_WIDTH = 2200.dp
private val EXPANDED_MIN_WIDTH = 840.dp
private const val EXPANDED_FRACTION = 0.9f
private val BUTTON_HEIGHT = 48.dp
private val START_MIN_WIDTH = 160.dp
private val TILE_MIN_WIDTH = 208.dp
private val TILE_PICTURE_HEIGHT = 160.dp
private const val NOT_READY_ALPHA = 0.45f
