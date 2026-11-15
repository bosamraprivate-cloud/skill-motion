package com.aitherapist.dls.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.R
import com.aitherapist.dls.rewards.RewardRecorder
import com.aitherapist.dls.rewards.RewardRules
import com.aitherapist.dls.rewards.Stickers
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Sticker book state: unlocked sticker ids and finished tasks (progress path). No spinner in child mode. */
sealed interface StickerBookUiState {
    data object Loading : StickerBookUiState

    data class Content(val unlocked: List<String>, val finishedTasks: Int) : StickerBookUiState
}

@HiltViewModel
class StickerBookViewModel
@Inject
constructor(private val rewards: RewardRecorder) : ViewModel() {
    private val state = MutableStateFlow<StickerBookUiState>(StickerBookUiState.Loading)
    val uiState: StateFlow<StickerBookUiState> = state.asStateFlow()

    fun load(childId: String) {
        viewModelScope.launch {
            state.value =
                try {
                    StickerBookUiState.Content(rewards.stickerIds(childId), rewards.totalFinishedTasks(childId))
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    StickerBookUiState.Content(emptyList(), 0) // a calm empty book, never an error
                }
        }
    }
}

/** Sticker book of [childId] (Story 4.9), opened from the task reward screen or the child profile. */
@Composable
fun StickerBookRoute(childId: String, onBack: () -> Unit, viewModel: StickerBookViewModel = hiltViewModel(key = "stickers-$childId")) {
    LaunchedEffect(childId) { viewModel.load(childId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StickerBookScreen(state, onBack)
}

/** Earned stickers in colour, locked ones as soft outlines, and the 3-slot path toward the next sticker. */
@Composable
fun StickerBookScreen(state: StickerBookUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(ChildColors.Canvas).padding(Spacing.S5),
        verticalArrangement = Arrangement.spacedBy(Spacing.S4)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.sticker_book_title),
                style = ChildType.Title,
                color = ChildColors.Ink,
                modifier = Modifier.weight(1f)
            )
            ChildBigButton(
                stringResource(R.string.sticker_book_back),
                ChildColors.Help,
                onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack
            )
        }
        val content = state as? StickerBookUiState.Content ?: return@Column
        Row(
            Modifier.fillMaxWidth().background(ChildColors.Surface, RoundedCornerShape(Radius.Lg)).padding(
                horizontal = Spacing.S5,
                vertical = Spacing.S3
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S5)
        ) {
            StickerPath(RewardRules.pathFilled(content.finishedTasks))
            Spacer(Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.sticker_book_count, content.unlocked.size, childDigits(content.unlocked.size)),
                style = ChildType.Button,
                color = ChildColors.InkSoft
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(STICKER_CELL),
            contentPadding = PaddingValues(vertical = Spacing.S3),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S5),
            verticalArrangement = Arrangement.spacedBy(Spacing.S5),
            modifier = Modifier.weight(1f)
        ) {
            items(List(maxOf(Stickers.BOOK_SLOTS, content.unlocked.size)) { Stickers.idFor(it) }) { id ->
                val earned = id in content.unlocked
                val label = stringResource(if (earned) R.string.sticker_earned else R.string.sticker_locked)
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .background(if (earned) ChildColors.Surface else Color.Transparent, RoundedCornerShape(Radius.Lg))
                        .padding(Spacing.S3)
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) { StickerArt.Sticker(id, earned, Modifier.fillMaxSize()) }
            }
        }
    }
}

/**
 * Three slots toward the next sticker joined by a soft path: [filled] slots hold a gold star and the path up to them
 * turns light sage; the rest stay sand outlines.
 */
@Composable
fun StickerPath(filled: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.reward_path, childDigits(filled), childDigits(RewardRules.TASKS_PER_STICKER))
    Row(modifier.semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
        repeat(RewardRules.TASKS_PER_STICKER) { slot ->
            if (slot > 0) {
                Box(
                    Modifier
                        .size(width = PATH_LINK, height = 8.dp)
                        .background(if (slot < filled) ChildColors.ScheduleDone else ChildColors.StarEmpty, RoundedCornerShape(50))
                )
            }
            val reached = slot < filled
            Box(
                Modifier
                    .size(PATH_SLOT)
                    .background(if (reached) ChildColors.Surface else ChildColors.Canvas, RoundedCornerShape(Radius.Md))
                    .border(3.dp, if (reached) ChildColors.Star else ChildColors.StarEmpty, RoundedCornerShape(Radius.Md)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (reached) ChildColors.Star else ChildColors.StarEmpty,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

private val STICKER_CELL = 160.dp
private val PATH_SLOT = 80.dp
private val PATH_LINK = 32.dp

/** A big (at least 220 x 120 dp) child button: optional icon plus the word, child ink on the calm colours. */
@Composable
fun ChildBigButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.Lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (color ==
                ChildColors.Done
            ) {
                Color.White
            } else {
                ChildColors.Ink
            }
        ),
        contentPadding = PaddingValues(horizontal = Spacing.S5, vertical = Spacing.S3),
        modifier = modifier.sizeIn(minWidth = 220.dp, minHeight = 120.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(Spacing.S3))
        }
        Text(text, style = ChildType.Button)
    }
}

/** Arabic-Indic digits in child mode (the app locale's digits). */
@Composable
fun childDigits(value: Int): String = childNumber(value)

/** Child mode always shows Arabic-Indic digits (nu-arab), whatever the app locale. */
val CHILD_DIGITS_LOCALE: Locale = Locale.forLanguageTag("ar-EG-u-nu-arab")

fun childNumber(value: Int, minDigits: Int = 1): String = NumberFormat.getIntegerInstance(CHILD_DIGITS_LOCALE).apply {
    minimumIntegerDigits = minDigits
    isGroupingUsed = false
}.format(value)

@Preview(widthDp = 800, heightDp = 1280)
@Composable
private fun StickerBookPortraitPreview() {
    DlsTheme { StickerBookScreen(StickerBookUiState.Content(unlocked = emptyList(), finishedTasks = 1), onBack = {}) }
}
