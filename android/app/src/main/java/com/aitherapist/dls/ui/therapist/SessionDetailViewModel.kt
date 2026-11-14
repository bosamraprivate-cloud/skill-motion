package com.aitherapist.dls.ui.therapist

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.data.CorrectionEntity
import com.aitherapist.dls.data.CorrectionStore
import com.aitherapist.dls.data.EvidenceFrames
import com.aitherapist.dls.data.SessionHistory
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.scoring.StepLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Session detail (Stories 5.1, 5.2): steps, attempts and evidence of one assessment session, and the only caller of
 * [CorrectionStore] (AD-9). Levels are recomputed from the facts after every write; nothing but the correction is stored.
 */
@HiltViewModel
class SessionDetailViewModel
internal constructor(
    private val history: SessionHistory,
    private val catalog: CatalogRepository,
    private val corrections: CorrectionStore,
    private val frames: EvidenceFrames,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long,
    private val newId: () -> String
) : ViewModel() {
    @Inject constructor(
        history: SessionHistory,
        catalog: CatalogRepository,
        corrections: CorrectionStore,
        frames: EvidenceFrames
    ) : this(history, catalog, corrections, frames, Dispatchers.IO, System::currentTimeMillis, { UUID.randomUUID().toString() })

    private val state = MutableStateFlow<SessionDetailUiState>(SessionDetailUiState.Loading)
    val uiState: StateFlow<SessionDetailUiState> = state.asStateFlow()

    private var childId: String? = null
    private var sessionId: String? = null

    fun load(childId: String, sessionId: String) {
        this.childId = childId
        this.sessionId = sessionId
        viewModelScope.launch { state.value = read(childId, sessionId) }
    }

    fun retry() {
        val c = childId ?: return
        val s = sessionId ?: return
        state.value = SessionDetailUiState.Loading
        viewModelScope.launch { state.value = read(c, s) }
    }

    /** Saves (upserts) the step's correction; [level] [StepLevel.NOT_SCORED] stores null. */
    fun correct(stepId: String, level: StepLevel) = write(stepId) { session ->
        val value = RulesEngine.score(level)
        corrections.addCorrection(CorrectionEntity(id = newId(), sessionId = session, stepId = stepId, level = value, createdAt = clock()))
    }

    /** Deletes the step's correction: the automatic level is back. */
    fun removeCorrection(stepId: String) = write(stepId) { session -> corrections.clearCorrection(session, stepId) }

    /** Recently decoded thumbnails by path (small LRU, in memory only; never written). */
    private val thumbs =
        object : LinkedHashMap<String, ImageBitmap>(THUMB_CACHE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > THUMB_CACHE
        }

    /** One evidence frame decrypted and decoded to at most [maxPx]; thumbnails come from the LRU when cached. */
    suspend fun image(relativePath: String, maxPx: Int): ImageBitmap? {
        val cacheable = maxPx == EVIDENCE_THUMB_PX
        if (cacheable) synchronized(thumbs) { thumbs[relativePath] }?.let { return it }
        val bytes = frame(relativePath) ?: return null
        val image = decodeFrameBytes(bytes, maxPx) ?: return null
        if (cacheable) synchronized(thumbs) { thumbs[relativePath] = image }
        return image
    }

    /** Decrypted JPEG bytes of one evidence frame, in memory only; null when missing or unreadable. */
    suspend fun frame(relativePath: String): ByteArray? = withContext(io) {
        try {
            frames.read(relativePath)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            null
        }
    }

    private fun write(stepId: String, action: suspend (sessionId: String) -> Unit) {
        val current = state.value as? SessionDetailUiState.Content ?: return
        val step = current.detail.steps.firstOrNull { it.stepId == stepId } ?: return
        if (!step.canCorrect || current.saving) return
        val c = childId ?: return
        val s = sessionId ?: return
        state.value = current.copy(saving = true, writeFailed = false)
        viewModelScope.launch {
            val ok =
                try {
                    withContext(io) { action(s) }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    false
                }
            state.value =
                when (val fresh = read(c, s)) {
                    is SessionDetailUiState.Content -> fresh.copy(writeFailed = !ok)
                    else -> fresh
                }
        }
    }

    private suspend fun read(childId: String, sessionId: String): SessionDetailUiState = try {
        withContext(io) {
            val session = history.session(sessionId)?.takeIf { it.childId == childId }
            if (session == null) {
                SessionDetailUiState.Empty
            } else {
                // Names only: a missing catalogue still shows the saved steps with raw ids.
                val cat = catalog.load().getOrNull()
                SessionDetailUiState.Content(
                    buildSessionDetail(session, history.attempts(session.id), history.corrections(session.id), cat)
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        SessionDetailUiState.Error(e::class.simpleName ?: "unknown") // type only, never child data
    }
}

private const val THUMB_CACHE = 48
