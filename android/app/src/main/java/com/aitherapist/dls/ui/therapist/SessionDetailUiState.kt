package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.CorrectionEntity
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.data.latestCorrection
import com.aitherapist.dls.data.toRuleAttempt
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.scoring.StepLevel

/**
 * One step of the Session detail (Stories 5.1, 5.2), computed from the facts on every load and never stored.
 * [automatic] is the Rule 1 level (null = still open); [corrected] is true when a therapist correction exists, and
 * [correctedLevel] is its level ([StepLevel.NOT_SCORED] for a "not scored" correction).
 */
data class StepDetail(
    val stepId: String,
    /** Catalogue step number; null for a saved step no longer in the catalogue. */
    val number: Int?,
    val text: String,
    val automatic: StepLevel?,
    val corrected: Boolean,
    val correctedLevel: StepLevel?,
    /** Saved attempts in saved order. */
    val attempts: List<AttemptEntity>,
    /**
     * A finished session allows correcting every step (even one never tried); a paused or abandoned one only steps
     * with an automatic level or an existing correction (open steps stay read-only).
     */
    val canCorrect: Boolean
) {
    /** The level reports and plans use: the correction when present, else the automatic level. */
    val effective: StepLevel? get() = if (corrected) correctedLevel else automatic
}

data class SessionDetail(
    val sessionId: String,
    val childId: String,
    val taskName: String,
    val startedAt: Long,
    val state: HistoryState,
    val steps: List<StepDetail>
)

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState

    /** No such assessment session (deleted, replay, eval child, or another child). */
    data object Empty : SessionDetailUiState

    /** [saving] while a correction is written; [writeFailed] after a failed write (the shown levels stay the saved ones). */
    data class Content(val detail: SessionDetail, val saving: Boolean = false, val writeFailed: Boolean = false) : SessionDetailUiState

    data class Error(val reason: String) : SessionDetailUiState
}

/**
 * Detail of one assessment session: every catalogue step of its task in catalogue order (plus saved steps missing
 * from the catalogue, at the end), each with its attempts, automatic level and correction. Invalid correction rows
 * are ignored (read path never throws); if several rows exist, the latest wins as in [com.aitherapist.dls.data.sessionLevels].
 */
fun buildSessionDetail(
    session: SessionEntity,
    attempts: List<AttemptEntity>,
    corrections: List<CorrectionEntity>,
    catalog: Catalog?
): SessionDetail {
    val task = catalog?.tasks?.firstOrNull { it.id == session.taskId }
    val byStep = attempts.groupBy { it.stepId }
    val done = session.state == SessionState.DONE
    val latest =
        corrections
            .filter { RulesEngine.isValidCorrection(it.level) }
            .groupBy { it.stepId }
            .mapValues { (_, rows) -> latestCorrection(rows) }
    val catalogSteps = task?.steps.orEmpty()
    val known = catalogSteps.map { it.id }.toSet()
    val extra = (byStep.keys + latest.keys).filter { it !in known }.distinct()
    fun detail(stepId: String, number: Int?, text: String): StepDetail {
        val rows = byStep[stepId].orEmpty()
        val correction = latest[stepId]
        val automatic = RulesEngine.stepLevel(rows.map { it.toRuleAttempt() })
        return StepDetail(
            stepId = stepId,
            number = number,
            text = text,
            automatic = automatic,
            corrected = correction != null,
            correctedLevel = correction?.let { RulesEngine.correctionLevel(it.level) },
            attempts = rows,
            canCorrect = done || automatic != null || correction != null
        )
    }
    return SessionDetail(
        sessionId = session.id,
        childId = session.childId,
        taskName = task?.nameAr ?: session.taskId,
        startedAt = session.startedAt,
        state = historyState(session.state),
        steps = catalogSteps.map { detail(it.id, it.number, it.textAr) } + extra.map { detail(it, null, it) }
    )
}
