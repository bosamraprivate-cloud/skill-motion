package com.aitherapist.dls.rewards

import com.aitherapist.dls.data.RewardEntity
import com.aitherapist.dls.data.RewardStore

/** In-memory [RewardStore]; [evalChildren] earn nothing, [finishedAssessments] feeds [finishedTasks]. */
class FakeRewardStore(
    private val evalChildren: Set<String> = emptySet(),
    var finishedAssessments: Int = 0,
    /** Called before a row is inserted (records write order in runner tests). */
    private val onWrite: () -> Unit = {}
) : RewardStore {
    val rows = linkedMapOf<String, RewardEntity>()
    var fail = false

    override suspend fun addIfAbsent(reward: RewardEntity): Boolean {
        if (fail) error("disk full")
        if (reward.id in rows) return false
        onWrite()
        rows[reward.id] = reward
        return true
    }

    override suspend fun isEvalOrMissing(childId: String): Boolean = childId in evalChildren

    override suspend fun starsForSession(sessionId: String): Int = rows.values.filter { it.sessionId == sessionId }.sumOf { it.stars }

    override suspend fun finishedTasks(childId: String): Int =
        finishedAssessments + rows.values.count { it.childId == childId && it.sessionId == null }
}
