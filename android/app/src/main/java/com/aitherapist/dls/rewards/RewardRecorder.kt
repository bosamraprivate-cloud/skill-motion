package com.aitherapist.dls.rewards

import com.aitherapist.dls.data.RewardEntity
import com.aitherapist.dls.data.RewardStore

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

    private suspend fun write(id: String, childId: String, sessionId: String?, finish: Finish): Boolean {
        if (store.isEvalOrMissing(childId)) return false
        return store.addIfAbsent(
            RewardEntity(id = id, childId = childId, sessionId = sessionId, stars = RewardRules.stars(finish), createdAt = wallClock())
        )
    }

    companion object {
        private const val TRAINING_PREFIX = "training:"

        fun stepKey(sessionId: String, stepId: String): String = "step:$sessionId:$stepId"
    }
}
