package com.aitherapist.dls.rewards

import com.aitherapist.dls.data.RewardEntity
import com.aitherapist.dls.data.RewardStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

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
        finishedAssessments + rows.values.count { it.childId == childId && it.id.startsWith("task:run:") }

    override suspend fun starsForPrefix(prefix: String): Int = rows.values.filter { it.id.startsWith(prefix) }.sumOf { it.stars }

    override suspend fun stickerOf(rowId: String): String? = rows[rowId]?.stickerId

    private val lock = Mutex()

    override suspend fun <T> inTransaction(block: suspend () -> T): T = lock.withLock { block() }

    /** Yields first, so concurrent callers interleave unless [inTransaction] serialises them. */
    override suspend fun stickerIds(childId: String): List<String> {
        yield()
        return rows.values
            .filter { it.childId == childId && it.stickerId != null }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
            .mapNotNull { it.stickerId }
    }

    override suspend fun setStickerIfAbsent(rowId: String, stickerId: String): Boolean {
        val row = rows[rowId] ?: return false
        if (row.stickerId != null) return false
        rows[rowId] = row.copy(stickerId = stickerId)
        return true
    }
}
