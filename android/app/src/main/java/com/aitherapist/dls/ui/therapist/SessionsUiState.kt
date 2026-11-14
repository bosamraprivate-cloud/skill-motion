package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.CorrectionEntity
import com.aitherapist.dls.data.RewardEntity
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.data.TrainingResultEntity
import com.aitherapist.dls.data.sessionLevels
import com.aitherapist.dls.rewards.RewardRecorder.Companion.TASK_RUN_PREFIX
import com.aitherapist.dls.rewards.RewardRecorder.Companion.TRAINING_PREFIX
import com.aitherapist.dls.scoring.StepLevel
import com.aitherapist.dls.training.TrainingResultIds

/** What a history row is: an assessment session or one training run. */
enum class HistoryKind { ASSESSMENT, TRAINING }

/**
 * Display state of a history row (running and paused both read as paused: they can be continued). [NOT_FINISHED] is a
 * training run without its finished-run row (still running, or left before its end).
 */
enum class HistoryState { DONE, PAUSED, ABANDONED, NOT_FINISHED }

/** One row of the Sessions tab, read-only. [title] is the task name, or the run's activity names. */
data class SessionListItem(
    val id: String,
    val kind: HistoryKind,
    val title: String,
    val startedAt: Long,
    val state: HistoryState,
    val stars: Int,
    /** Number of activities in a training run (0 for an assessment). */
    val activities: Int = 0
) {
    /** Only a finished assessment opens the session summary. */
    val opensSummary: Boolean get() = kind == HistoryKind.ASSESSMENT && state == HistoryState.DONE
}

/** Overview quick stats; [lastAssessmentAt] is null before the first finished assessment. */
data class OverviewStats(val lastAssessmentAt: Long?, val finishedTasks: Int, val stickers: Int)

sealed interface SessionsUiState {
    data object Loading : SessionsUiState

    data class Content(val items: List<SessionListItem>, val stats: OverviewStats) : SessionsUiState

    data class Error(val reason: String) : SessionsUiState
}

fun historyState(state: SessionState): HistoryState = when (state) {
    SessionState.DONE -> HistoryState.DONE
    SessionState.RUNNING, SessionState.PAUSED -> HistoryState.PAUSED
    SessionState.ABANDONED -> HistoryState.ABANDONED
}

/**
 * The child's history newest first: assessment sessions (stars from their step rewards) and training runs grouped
 * from their saved activities (done when the run's task row exists, otherwise not finished).
 */
fun buildSessionList(
    sessions: List<SessionEntity>,
    training: List<TrainingResultEntity>,
    rewards: List<RewardEntity>,
    catalog: Catalog?
): List<SessionListItem> {
    val taskNames = catalog?.tasks?.associate { it.id to it.nameAr }.orEmpty()
    val activityNames = catalog?.activities?.associate { it.id to it.textAr }.orEmpty()
    val starsBySession = rewards.filter { it.sessionId != null }.groupBy { it.sessionId }.mapValues { (_, r) -> r.sumOf { it.stars } }
    val assessments =
        sessions.map { s ->
            SessionListItem(
                id = s.id,
                kind = HistoryKind.ASSESSMENT,
                title = taskNames[s.taskId] ?: s.taskId,
                startedAt = s.startedAt,
                state = historyState(s.state),
                stars = starsBySession[s.id] ?: 0
            )
        }
    val finishedRuns = rewards.map { it.id }.filter { it.startsWith(TASK_RUN_PREFIX) }.map { it.removePrefix(TASK_RUN_PREFIX) }.toSet()
    val runs =
        training.groupBy { TrainingResultIds.runIdOf(it.id) }.map { (runId, rows) ->
            val ordered = rows.sortedBy { it.createdAt }
            SessionListItem(
                id = runId,
                kind = HistoryKind.TRAINING,
                title = ordered.map { activityNames[it.activityId] ?: it.activityId }.distinct().joinToString(" · "),
                startedAt = ordered.first().createdAt,
                state = if (runId in finishedRuns) HistoryState.DONE else HistoryState.NOT_FINISHED,
                stars = rewards.filter { it.id.startsWith(TRAINING_PREFIX + TrainingResultIds.runPrefix(runId)) }.sumOf { it.stars },
                activities = ordered.size
            )
        }
    return (assessments + runs).sortedByDescending { it.startedAt }
}

/** Overview stats from the same facts as the reward rules (finished assessments plus finished runs; stored stickers). */
fun buildOverviewStats(sessions: List<SessionEntity>, rewards: List<RewardEntity>): OverviewStats {
    val done = sessions.filter { it.state == SessionState.DONE }
    return OverviewStats(
        lastAssessmentAt = done.maxOfOrNull { it.endedAt ?: it.startedAt },
        finishedTasks = done.size + rewards.count { it.id.startsWith(TASK_RUN_PREFIX) },
        stickers = rewards.count { it.stickerId != null }
    )
}

/** Read-only summary of one assessment session (computed, never stored). */
data class SessionSummary(
    val sessionId: String,
    val childId: String,
    val taskName: String,
    val startedAt: Long,
    /** Null while the session has no end time. */
    val durationMs: Long?,
    val levelThree: Int,
    val levelTwo: Int,
    val levelOne: Int,
    /** Steps tried whose effective level is not scored (or still open). */
    val notScored: Int,
    /** Steps of the task never tried in this session. */
    val notReached: Int,
    /** Not-scored steps with at least one cannot-tell attempt. */
    val cannotTell: Int,
    val stars: Int,
    val state: HistoryState
) {
    /** Every step of the task (plus any saved step no longer in the catalogue). */
    val steps: Int get() = levelThree + levelTwo + levelOne + notScored + notReached
}

/**
 * Counts per Rule 1 level (corrections included, as everywhere else) over the catalogue task's steps; a step with
 * no attempt and no correction is "not reached". Saved steps missing from the catalogue still count by level.
 */
fun buildSessionSummary(
    session: SessionEntity,
    attempts: List<AttemptEntity>,
    corrections: List<CorrectionEntity>,
    rewards: List<RewardEntity>,
    catalog: Catalog?
): SessionSummary {
    val task = catalog?.tasks?.firstOrNull { it.id == session.taskId }
    val levels = sessionLevels(attempts, corrections)
    val taskSteps = task?.steps?.map { it.id }.orEmpty()
    val tried = levels.values
    val notScoredIds = levels.filterValues { it == null || it == StepLevel.NOT_SCORED }.keys
    val cannotTellSteps = attempts.filter { it.verdict == AttemptVerdict.CANNOT_TELL }.map { it.stepId }.toSet()
    return SessionSummary(
        sessionId = session.id,
        childId = session.childId,
        taskName = task?.nameAr ?: session.taskId,
        startedAt = session.startedAt,
        durationMs = session.endedAt?.let { (it - session.startedAt).coerceAtLeast(0L) },
        levelThree = tried.count { it == StepLevel.THREE },
        levelTwo = tried.count { it == StepLevel.TWO },
        levelOne = tried.count { it == StepLevel.ONE },
        notScored = notScoredIds.size,
        notReached = taskSteps.count { it !in levels },
        cannotTell = notScoredIds.count { it in cannotTellSteps },
        stars = rewards.filter { it.sessionId == session.id }.sumOf { it.stars },
        state = historyState(session.state)
    )
}

sealed interface SessionSummaryUiState {
    data object Loading : SessionSummaryUiState

    /** No assessment session to summarise (e.g. the child left before the first step). */
    data object Empty : SessionSummaryUiState

    /** The visit's sessions exist but none is done (exited mid-way): a short note, never stale data. */
    data object NotFinished : SessionSummaryUiState

    /** One section per done session of the visit, in run order (a jacket chain gives two). */
    data class Content(val summaries: List<SessionSummary>) : SessionSummaryUiState

    data class Error(val reason: String) : SessionSummaryUiState
}
