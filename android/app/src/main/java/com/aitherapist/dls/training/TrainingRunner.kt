package com.aitherapist.dls.training

import com.aitherapist.dls.catalog.Activity
import com.aitherapist.dls.catalog.Criterion
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.data.TrainingResultEntity
import com.aitherapist.dls.data.TrainingResultStore
import com.aitherapist.dls.rewards.Finish
import com.aitherapist.dls.rewards.RewardRecorder
import com.aitherapist.dls.vision.LandmarkFrame
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the training screen shows for the current activity (Story 4.6). */
data class TrainingState(
    val index: Int = 0,
    val total: Int = 0,
    val activity: Activity? = null,
    /** Non-null for a camera-counted activity. */
    val criterion: Criterion? = null,
    val count: Int = 0,
    val counts: Map<Side?, Int> = emptyMap(),
    val holdSec: Double = 0.0,
    val targetReached: Boolean = false,
    val elapsedMs: Long = 0,
    val paused: Boolean = false,
    val ended: Boolean = false
) {
    val counted: Boolean get() = criterion != null
}

/**
 * Runs the picked activities one at a time in the picked order (Story 4.6). The only writer of `training_result`
 * (AD-9): a finished activity saves exactly one row with the deterministic id `"$runId:$index"`, then its stars go
 * through [RewardRecorder.recordTrainingSet] (idempotent on that id). A failed write leaves the activity current so
 * Done retries; a retry after the row exists only re-records the stars. [start] skips activities whose row already
 * exists (a run restored after process death) and re-records their stars. An interrupted activity writes nothing. Pause freezes the timer and ignores frames. Not thread-safe: call from one
 * thread (the ViewModel's main scope).
 */
class TrainingRunner(
    private val childId: String,
    private val activities: List<Activity>,
    private val store: TrainingResultStore,
    private val rewards: RewardRecorder,
    private val clock: () -> Long,
    private val runId: String,
    private val wallClock: () -> Long = System::currentTimeMillis
) {
    private val state = MutableStateFlow(TrainingState(total = activities.size, ended = activities.isEmpty()))
    val current: StateFlow<TrainingState> = state.asStateFlow()

    private var counter: RepCounter? = null
    private var accumulatedMs = 0L
    private var runningSince: Long? = null
    private var savedIndex = -1
    private var started = false

    /** Starts the first activity without a saved row (once); with every row saved the run is ended. */
    suspend fun start() {
        if (started || activities.isEmpty()) return
        started = true
        var index = 0
        while (index < activities.size) {
            val saved = store.find(resultId(index)) ?: break
            rewards.recordTrainingSet(childId, finishOf(saved.criterionMet), saved.id)
            index++
        }
        if (index < activities.size) begin(index) else state.value = state.value.copy(ended = true)
    }

    fun resultId(index: Int): String = "$runId:$index"

    private fun begin(index: Int) {
        val activity = activities[index]
        counter = activity.counter?.let { c -> runCatching { RepCounter(c) }.getOrNull() }
        accumulatedMs = 0
        runningSince = clock()
        state.value =
            TrainingState(index = index, total = activities.size, activity = activity, criterion = counter?.let { activity.counter })
    }

    private fun elapsed(): Long = accumulatedMs + (runningSince?.let { clock() - it } ?: 0L)

    /** Refreshes the running timer. */
    fun tick() {
        val s = state.value
        if (!started || s.ended || s.paused) return
        state.value = s.copy(elapsedMs = elapsed())
    }

    /** One landmark frame for a counted activity; ignored while paused, ended or guided. */
    fun feed(frame: LandmarkFrame) {
        val s = state.value
        val c = counter ?: return
        if (s.ended || s.paused) return
        c.feed(frame)
        val hold = c.holdSec.values.maxOrNull() ?: 0.0
        state.value = s.copy(count = c.count, counts = c.counts, holdSec = hold, targetReached = c.targetReached, elapsedMs = elapsed())
    }

    fun pause() {
        val s = state.value
        if (!started || s.ended || s.paused) return
        accumulatedMs = elapsed()
        runningSince = null
        state.value = s.copy(paused = true, elapsedMs = accumulatedMs)
    }

    fun resume() {
        val s = state.value
        if (!s.paused) return
        runningSince = clock()
        state.value = s.copy(paused = false)
    }

    /**
     * Ends the current activity (criterion reached or Done): saves one row, then records stars, then moves on.
     * Returns false when nothing was saved (already saved, not started, or ended).
     */
    suspend fun finish(): Boolean {
        val s = state.value
        val activity = s.activity ?: return false
        if (!started || s.ended || savedIndex == s.index) return false
        val c = counter
        val criterionMet = c?.targetReached ?: false
        val count =
            when (s.criterion) {
                null -> null
                is Criterion.Reps -> c?.count ?: 0
                is Criterion.Hold -> (c?.bestHoldSec?.values?.maxOrNull() ?: 0.0).toInt()
            }
        val result =
            TrainingResultEntity(
                id = resultId(s.index),
                childId = childId,
                activityId = activity.id,
                count = count,
                durationSec = (elapsed() / MS_PER_SEC).roundToInt(),
                criterionMet = criterionMet,
                createdAt = wallClock()
            )
        val saved = store.find(result.id) ?: result.also { store.add(it) }
        rewards.recordTrainingSet(childId, finishOf(saved.criterionMet), saved.id)
        savedIndex = s.index
        val next = s.index + 1
        if (next < activities.size) {
            begin(next)
        } else {
            counter = null
            runningSince = null
            state.value = state.value.copy(ended = true, paused = false)
        }
        return true
    }

    /** Exit mid-activity: nothing is written for the current activity. */
    fun interrupt() {
        counter = null
        runningSince = null
        state.value = state.value.copy(ended = true, paused = false)
    }

    /** Criterion met by the camera = done on the first try (2 stars); anything else = finished (1 star). */
    private fun finishOf(criterionMet: Boolean): Finish = if (criterionMet) Finish.Done(firstTry = true) else Finish.Finished

    private companion object {
        const val MS_PER_SEC = 1_000.0
    }
}
