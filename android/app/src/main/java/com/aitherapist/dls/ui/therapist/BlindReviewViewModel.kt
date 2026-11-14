package com.aitherapist.dls.ui.therapist

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.BlindItem
import com.aitherapist.dls.data.BlindRatingEntity
import com.aitherapist.dls.data.BlindReview
import com.aitherapist.dls.data.EvidenceFrames
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The three blind answers (Story 5.7). */
enum class BlindAnswer(val verdict: AttemptVerdict) {
    DONE(AttemptVerdict.DONE),
    NOT_DONE(AttemptVerdict.NOT_DONE),
    CANNOT_TELL(AttemptVerdict.CANNOT_TELL)
}

/**
 * What the blind screen shows of one attempt: the step sentence, the existing frames and the replay clip (never
 * verdict, level, child or any file name or path on screen).
 */
data class BlindAttempt(val attemptId: String, val stepText: String, val framePaths: List<String>, val clip: File? = null)

sealed interface BlindReviewUiState {
    data object Loading : BlindReviewUiState

    /** No attempt with frames exists yet. */
    data object Empty : BlindReviewUiState

    /** Every attempt is rated (calm end state). */
    data class AllRated(val total: Int) : BlindReviewUiState

    data class Content(
        val attempt: BlindAttempt,
        val rated: Int,
        val total: Int,
        val saving: Boolean = false,
        val writeFailed: Boolean = false
    ) : BlindReviewUiState

    data class Error(val reason: String) : BlindReviewUiState
}

/**
 * Order of one review run: a pure shuffle seeded by [seed]; then, where two attempts of the same step end up
 * adjacent, the second is swapped with the nearest later attempt of another step. How many attempts a step has never
 * decides the order.
 */
fun blindOrder(items: List<BlindItem>, seed: Long): List<BlindItem> {
    val out = items.shuffled(Random(seed)).toMutableList()
    for (i in 1 until out.size) {
        if (out[i].stepId != out[i - 1].stepId) continue
        val j = (i + 1 until out.size).firstOrNull { out[it].stepId != out[i - 1].stepId } ?: break
        out[i] = out[j].also { out[j] = out[i] }
    }
    return out
}

/**
 * Blind review (Story 5.7): one unrated attempt at a time, frames and step sentence only; the only writer of
 * `blind_rating` (AD-9). Ratings never change levels, reports or plans.
 */
@HiltViewModel
class BlindReviewViewModel
internal constructor(
    private val review: BlindReview,
    private val catalog: CatalogRepository,
    private val frames: EvidenceFrames,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long,
    private val newId: () -> String,
    private val seed: Long
) : ViewModel() {
    @Inject constructor(review: BlindReview, catalog: CatalogRepository, frames: EvidenceFrames) :
        this(review, catalog, frames, Dispatchers.IO, System::currentTimeMillis, { UUID.randomUUID().toString() }, System.nanoTime())

    private val state = MutableStateFlow<BlindReviewUiState>(BlindReviewUiState.Loading)
    val uiState: StateFlow<BlindReviewUiState> = state.asStateFlow()

    private var queue = ArrayDeque<BlindAttempt>()
    private var rated = 0
    private var total = 0

    init {
        load()
    }

    fun retry() {
        state.value = BlindReviewUiState.Loading
        load()
    }

    /** Saves the shown attempt's rating (upsert) and moves to the next one. */
    fun rate(answer: BlindAnswer) {
        val current = state.value as? BlindReviewUiState.Content ?: return
        if (current.saving) return
        state.value = current.copy(saving = true, writeFailed = false)
        viewModelScope.launch {
            val ok =
                try {
                    withContext(io) {
                        review.rate(BlindRatingEntity(newId(), current.attempt.attemptId, answer.verdict, clock()))
                    }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    false
                }
            when {
                ok -> {
                    queue.removeFirstOrNull()
                    rated++
                    show()
                }
                // The attempt was deleted meanwhile (session or child removed): drop it and recount.
                !stillExists(current.attempt.attemptId) -> {
                    queue.removeFirstOrNull()
                    total--
                    show()
                }
                else -> state.value = current.copy(saving = false, writeFailed = true)
            }
        }
    }

    /** Moves on without rating; the attempt stays unrated and comes back at the end of the queue. */
    fun skip() {
        val current = state.value as? BlindReviewUiState.Content ?: return
        if (current.saving) return
        queue.removeFirstOrNull()?.let { if (queue.isNotEmpty()) queue.addLast(it) else queue.addFirst(it) }
        show()
    }

    /** One frame decrypted (in memory only) and decoded to at most [maxPx]; null when missing or unreadable. */
    suspend fun image(relativePath: String, maxPx: Int): ImageBitmap? {
        val bytes =
            withContext(io) {
                try {
                    frames.read(relativePath)
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    null
                }
            } ?: return null
        return decodeFrameBytes(bytes, maxPx)
    }

    private suspend fun stillExists(attemptId: String): Boolean = try {
        withContext(io) { review.attemptExists(attemptId) }
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        true
    }

    private fun show() {
        val next = queue.firstOrNull()
        state.value =
            when {
                total == 0 -> BlindReviewUiState.Empty
                next == null -> BlindReviewUiState.AllRated(total)
                else -> BlindReviewUiState.Content(next, rated, total)
            }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val (items, ratedIds, texts) =
                    withContext(io) {
                        // Step sentences only; a missing catalogue shows the raw step id.
                        val steps = catalog.load().getOrNull()?.steps.orEmpty().associate { it.id to it.textAr }
                        Triple(review.candidates(), review.ratedAttemptIds(), steps)
                    }
                total = items.size
                rated = items.count { it.attemptId in ratedIds }
                queue =
                    ArrayDeque(
                        blindOrder(items.filter { it.attemptId !in ratedIds }, seed).map {
                            BlindAttempt(it.attemptId, texts[it.stepId] ?: it.stepId, it.framePaths, it.clip)
                        }
                    )
                show()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                state.value = BlindReviewUiState.Error(e::class.simpleName ?: "unknown") // type only
            }
        }
    }
}
