package com.aitherapist.dls.rewards

import com.aitherapist.dls.data.RewardEntity
import com.aitherapist.dls.data.RewardStore
import com.aitherapist.dls.training.TrainingResultIds

/**
 * The only writer of `reward` rows (AD-9, Story 4.7). Called after a step's attempt facts are saved (by
 * `AssessmentRunner`) or after a training result is saved (Story 4.6). Writes are idempotent: the row id is derived from
 * (session, step) or the training result, so a retry or restore never double-counts. The eval child earns nothing;
 * replay sessions never call it. Rewards never feed scoring.
 *
 * Depends on `data.RewardStore`/`RewardEntity`: the spine's package diagram allows `rewards --> data`.
 */
class RewardRecorder(private val store: RewardStore, private val wallClock: () -> Long = System::currentTimeMillis) {
    /** Records the stars for step [stepId] of assessment session [sessionId]; true when a new row was written. */
    suspend fun recordStep(childId: String, sessionId: String, stepId: String, finish: Finish): Boolean =
        write(stepKey(sessionId, stepId), childId, sessionId, finish)

    /** Records the stars for one completed training set with the same rules; [resultId] keeps it idempotent. */
    suspend fun recordTrainingSet(childId: String, outcome: Finish, resultId: String): Boolean =
        write("$TRAINING_PREFIX$resultId", childId, null, outcome)

    suspend fun starsForSession(sessionId: String): Int = store.starsForSession(sessionId)

    /** Finished assessment tasks plus finished training tasks (sticker progress, Stories 4.8/4.9). */
    suspend fun totalFinishedTasks(childId: String): Int = store.finishedTasks(childId)

    /** Stickers [childId] has unlocked, in unlock order. */
    suspend fun stickerIds(childId: String): List<String> = store.stickerIds(childId)

    /** Task end of assessment session [sessionId]: its stars, the finished-task count and any sticker it unlocks. */
    suspend fun finishAssessment(childId: String, sessionId: String): TaskReward? =
        finishTask(childId, sessionId, taskRowId = "task:session:$sessionId", starPrefix = "step:$sessionId:")

    /** End of training run [runId] (result ids `"$runId:<index>"`): the run counts as one finished task. */
    suspend fun finishTrainingRun(childId: String, runId: String): TaskReward? =
        finishTask(childId, null, taskRowId = "$TASK_RUN_PREFIX$runId", starPrefix = TRAINING_PREFIX + TrainingResultIds.runPrefix(runId))

    /**
     * Writes the task's own zero-star row (deterministic id; it also marks a finished training run) and, when the
     * finished-task count owes a sticker, stores at most one on that row. Read, compute and write run in one
     * transaction, so retries, restores or concurrent calls never duplicate a sticker or put it on a later task.
     * Null for the eval child (no reward screen).
     */
    private suspend fun finishTask(childId: String, sessionId: String?, taskRowId: String, starPrefix: String): TaskReward? {
        if (store.isEvalOrMissing(childId)) return null
        return store.inTransaction {
            store.addIfAbsent(RewardEntity(id = taskRowId, childId = childId, sessionId = sessionId, stars = 0, createdAt = wallClock()))
            val finished = store.finishedTasks(childId)
            val stored = store.stickerIds(childId).size
            if (RewardRules.stickersUnlocked(finished) > stored && store.stickerOf(taskRowId) == null) {
                store.setStickerIfAbsent(taskRowId, Stickers.idFor(stored))
            }
            TaskReward(store.starsForPrefix(starPrefix), finished, store.stickerOf(taskRowId))
        }
    }

    private suspend fun write(id: String, childId: String, sessionId: String?, finish: Finish): Boolean {
        if (store.isEvalOrMissing(childId)) return false
        return store.addIfAbsent(
            RewardEntity(id = id, childId = childId, sessionId = sessionId, stars = RewardRules.stars(finish), createdAt = wallClock())
        )
    }

    companion object {
        /** Id prefix of a training set's reward row (`training:<resultId>`). */
        const val TRAINING_PREFIX = "training:"

        /** Id prefix of a finished training run's task row (`task:run:<runId>`). */
        const val TASK_RUN_PREFIX = "task:run:"

        fun stepKey(sessionId: String, stepId: String): String = "step:$sessionId:$stepId"
    }
}
