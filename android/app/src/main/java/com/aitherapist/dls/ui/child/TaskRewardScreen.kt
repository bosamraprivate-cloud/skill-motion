package com.aitherapist.dls.ui.child

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.R
import com.aitherapist.dls.data.ChildProfiles
import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.rewards.RewardRecorder
import com.aitherapist.dls.rewards.RewardRules
import com.aitherapist.dls.rewards.TaskReward
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.ChildType
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.ui.therapist.CharacterIndexSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter

/** Which finished task the reward screen is for. */
sealed interface RewardTask {
    data class Assessment(val sessionId: String) : RewardTask

    data class Training(val runId: String) : RewardTask
}

/** Task reward state. [Skip]: no reward screen (eval child, or nothing could load: never blocks the flow). */
sealed interface TaskRewardUiState {
    data object Loading : TaskRewardUiState

    data object Skip : TaskRewardUiState

    data class Content(val stars: Int, val finishedTasks: Int, val newSticker: String?, val effectLevel: EffectLevel, val clip: String?) :
        TaskRewardUiState
}

/** Builds the task reward (Story 4.9): unlocks owed stickers once through [RewardRecorder], then shows stars and path. */
@HiltViewModel
class TaskRewardViewModel
@Inject
constructor(
    private val rewards: RewardRecorder,
    private val children: ChildProfiles,
    private val characters: CharacterIndexSource
) : ViewModel() {
    private val state = MutableStateFlow<TaskRewardUiState>(TaskRewardUiState.Loading)
    val uiState: StateFlow<TaskRewardUiState> = state.asStateFlow()
    private var started = false

    fun load(childId: String, task: RewardTask) {
        if (started) return
        started = true
        viewModelScope.launch { state.value = build(childId, task) }
    }

    private suspend fun build(childId: String, task: RewardTask): TaskRewardUiState = try {
        val reward: TaskReward? =
            when (task) {
                is RewardTask.Assessment -> rewards.finishAssessment(childId, task.sessionId)
                is RewardTask.Training -> rewards.finishTrainingRun(childId, task.runId)
            }
        val child = children.get(childId)
        if (reward == null || child == null) {
            TaskRewardUiState.Skip
        } else {
            val variantId = child.characterVariant.name.lowercase(Locale.ROOT)
            val clip =
                runCatching {
                    characters.load().variants.firstOrNull { it.id == variantId }?.reactions?.let { list ->
                        (list.firstOrNull { it.state == CELEBRATE_BIG } ?: list.firstOrNull())?.file
                    }
                }.getOrNull()
            TaskRewardUiState.Content(reward.stars, reward.finishedTasks, reward.newSticker, child.effectLevel, clip)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        TaskRewardUiState.Skip
    }

    private companion object {
        const val CELEBRATE_BIG = "celebrateBig"
    }
}

/**
 * Task reward for [childId] after [task] finished (Story 4.9). Continues through [onNext] when the child taps the big
 * Next button or after a calm delay (paused while the sticker book or adult sheet is open, or the screen is stopped); never blocks: a skipped reward continues
 * at once.
 */
@Composable
fun TaskRewardRoute(
    childId: String,
    task: RewardTask,
    onNext: () -> Unit,
    paused: Boolean = false,
    onAdultCorner: () -> Unit = {},
    viewModel: TaskRewardViewModel = hiltViewModel(key = "reward-$task")
) {
    LaunchedEffect(childId, task) { viewModel.load(childId, task) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var bookOpen by rememberSaveable { mutableStateOf(false) }
    val next by rememberUpdatedState(onNext)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    // The calm auto-continue waits while the sticker book or adult sheet is open or the screen is not started.
    LaunchedEffect(state, bookOpen, paused, visible) {
        when (state) {
            TaskRewardUiState.Skip -> next()
            is TaskRewardUiState.Content -> if (!bookOpen && !paused && visible) {
                delay(AUTO_CONTINUE_MS)
                next()
            }
            TaskRewardUiState.Loading -> Unit
        }
    }
    val content = state as? TaskRewardUiState.Content
    when {
        content == null -> Box(Modifier.fillMaxSize().background(ChildColors.Canvas))
        bookOpen -> StickerBookRoute(childId, onBack = { bookOpen = false })
        else -> TaskRewardScreen(content, onNext = onNext, onStickerBook = { bookOpen = true }, onAdultCorner = onAdultCorner)
    }
}

/**
 * Stars of the task, the character's `celebrateBig` and the 3-slot sticker path. Full: a single gentle Konfetti burst
 * (no flashing); calm (default, or system animations removed): the clip only, fading in slowly.
 */
@Composable
fun TaskRewardScreen(
    state: TaskRewardUiState.Content,
    onNext: () -> Unit,
    onStickerBook: () -> Unit,
    modifier: Modifier = Modifier,
    onAdultCorner: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val reducedMotion =
        remember(configuration) {
            runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
                .getOrDefault(false)
        }
    val full = state.effectLevel == EffectLevel.FULL && !reducedMotion
    val fade = remember { Animatable(if (full) 1f else 0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(CALM_FADE_MS, easing = LinearEasing)) }
    Box(modifier.fillMaxSize().background(ChildColors.Canvas)) {
        // Landscape balance: the celebrating character on the start half, stars, sticker path and buttons on the end half.
        Row(
            Modifier.fillMaxSize().padding(Spacing.S5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S5)
        ) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                CharacterView(clip = state.clip, loop = true, modifier = Modifier.fillMaxSize().alpha(fade.value))
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.S5)
            ) {
                val starsLabel = stringResource(R.string.reward_stars, childDigits(state.stars))
                Row(
                    Modifier
                        .semantics { contentDescription = starsLabel }
                        .background(ChildColors.Surface, RoundedCornerShape(percent = 50))
                        .padding(horizontal = Spacing.S6, vertical = Spacing.S2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = ChildColors.Star, modifier = Modifier.size(96.dp))
                    Text(childDigits(state.stars), style = ChildType.Count, color = ChildColors.Ink)
                }
                Row(
                    Modifier.background(ChildColors.Surface, RoundedCornerShape(Radius.Lg)).padding(Spacing.S4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S4)
                ) {
                    StickerPath(RewardRules.pathFilled(state.finishedTasks))
                    state.newSticker?.let { sticker ->
                        val label = stringResource(R.string.reward_sticker_new)
                        StickerArt.Sticker(
                            sticker,
                            earned = true,
                            modifier = Modifier.size(112.dp).semantics {
                                contentDescription = label
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
                    ChildBigButton(
                        stringResource(R.string.reward_sticker_book),
                        ChildColors.Repeat,
                        onStickerBook,
                        icon = Icons.Filled.Star
                    )
                    ChildBigButton(
                        stringResource(R.string.reward_next),
                        ChildColors.Done,
                        onNext,
                        icon = Icons.AutoMirrored.Filled.ArrowForward
                    )
                }
            }
        }
        if (full) {
            val parties =
                remember {
                    listOf(
                        Party(
                            speed = 0f,
                            maxSpeed = CONFETTI_SPEED,
                            damping = CONFETTI_DAMPING,
                            spread = FULL_CIRCLE,
                            colors = CONFETTI_COLORS,
                            position = Position.Relative(CENTER, CONFETTI_Y),
                            emitter = Emitter(duration = BURST_MS, TimeUnit.MILLISECONDS).max(PARTICLES)
                        )
                    )
                }
            KonfettiView(modifier = Modifier.fillMaxSize(), parties = parties)
        }
        // The adult corner (2 s press) stays reachable on the reward screen.
        Box(Modifier.align(Alignment.TopEnd).padding(Spacing.S3)) { TrainingAdultCorner(onAdultCorner) }
    }
}

/** Calm delay before the reward continues on its own. */
const val AUTO_CONTINUE_MS = 10_000L
private const val CALM_FADE_MS = 1_500
private const val BURST_MS = 400L
private const val PARTICLES = 80
private const val CONFETTI_SPEED = 20f
private const val CONFETTI_DAMPING = 0.9f
private const val FULL_CIRCLE = 360
private const val CENTER = 0.5
private const val CONFETTI_Y = 0.4

// Calm palette only: gold, green, blue, lilac, mint (no red/orange).
private val CONFETTI_COLORS = listOf(0xE8C26A, 0x5E9C8A, 0x7FA7C9, 0xB9A6D3, 0xA9CFC2)
