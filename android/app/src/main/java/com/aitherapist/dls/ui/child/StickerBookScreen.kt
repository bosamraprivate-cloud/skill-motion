package com.aitherapist.dls.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            ChildBigButton(stringResource(R.string.sticker_book_back), ChildColors.Help, onBack)
        }
        val content = state as? StickerBookUiState.Content ?: return@Column
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
            StickerPath(RewardRules.pathFilled(content.finishedTasks))
            Text(
                stringResource(R.string.sticker_book_count, childDigits(content.unlocked.size)),
                style = ChildType.Button,
                color = ChildColors.InkSoft
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(Spacing.S2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
            verticalArrangement = Arrangement.spacedBy(Spacing.S4),
            modifier = Modifier.weight(1f)
        ) {
            items(List(maxOf(Stickers.BOOK_SLOTS, content.unlocked.size)) { Stickers.idFor(it) }) { id ->
                val earned = id in content.unlocked
                val label = stringResource(if (earned) R.string.sticker_earned else R.string.sticker_locked)
                StickerArt.Sticker(id, earned, Modifier.aspectRatio(1f).semantics { contentDescription = label })
            }
        }
    }
}

/** Three slots toward the next sticker: [filled] slots hold a gold star, the rest are soft outlines. */
@Composable
fun StickerPath(filled: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.reward_path, childDigits(filled), childDigits(RewardRules.TASKS_PER_STICKER))
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        repeat(RewardRules.TASKS_PER_STICKER) { slot ->
            Box(
                Modifier.size(72.dp).background(ChildColors.Surface, RoundedCornerShape(Radius.Md)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (slot < filled) ChildColors.Star else ChildColors.StarEmpty,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

/** A big (at least 120 dp) child button. */
@Composable
fun ChildBigButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.Lg),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = modifier.sizeIn(minWidth = 220.dp, minHeight = 120.dp)
    ) { Text(text, style = ChildType.Button) }
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
