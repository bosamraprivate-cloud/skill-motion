package com.aitherapist.dls.vision

/** Measured landmark streams. COMBINED counts frames for which both pose and hand results arrived. */
enum class FpsStream { POSE, HAND, COMBINED }

/** Average fps per stream over a finished measurement run. */
data class FpsRunResult(val pose: Double, val hand: Double, val combined: Double, val durationMs: Long)

/**
 * Pure frame-rate meter (NFR-2): rolling [windowMs] fps per stream plus run averages.
 * Results are reported per frame timestamp; a frame counts for COMBINED once both a pose
 * and a hand result arrived for it. Thread-safe.
 */
class FpsMeter(private val clock: () -> Long, private val windowMs: Long = DEFAULT_WINDOW_MS) {
    private val events = FpsStream.entries.associateWith { ArrayDeque<Long>() }
    private val runCounts = FpsStream.entries.associateWith { 0 }.toMutableMap()
    private val pendingPose = LinkedHashSet<Long>()
    private val pendingHand = LinkedHashSet<Long>()
    private var runStartMs: Long? = null

    /** Records a result of [stream] (POSE or HAND) for the frame captured at [frameTimestampMs]. */
    @Synchronized
    fun onResult(stream: FpsStream, frameTimestampMs: Long) {
        require(stream != FpsStream.COMBINED) { "COMBINED is derived" }
        record(stream)
        val own = if (stream == FpsStream.POSE) pendingPose else pendingHand
        val other = if (stream == FpsStream.POSE) pendingHand else pendingPose
        if (other.remove(frameTimestampMs)) {
            record(FpsStream.COMBINED)
        } else {
            own.add(frameTimestampMs)
            while (own.size > MAX_PENDING) own.remove(own.first())
        }
    }

    /** Rolling fps of [stream] over the last window; 0 when empty. */
    @Synchronized
    fun fps(stream: FpsStream): Double {
        val queue = events.getValue(stream)
        trim(queue)
        return queue.size * MS_PER_SECOND / windowMs
    }

    @Synchronized
    fun startRun() {
        runStartMs = clock()
        clearPending()
        FpsStream.entries.forEach { runCounts[it] = 0 }
    }

    /** Drops half-paired frames, e.g. when the engine stops; rolling windows age out on their own. */
    @Synchronized
    fun clearPending() {
        pendingPose.clear()
        pendingHand.clear()
    }

    /** Elapsed ms of the current run, or null when no run is active. */
    @Synchronized
    fun runElapsedMs(): Long? = runStartMs?.let { clock() - it }

    /** Ends the run and returns the average fps of each stream over it, or null without a run. */
    @Synchronized
    fun finishRun(): FpsRunResult? {
        val start = runStartMs ?: return null
        val duration = clock() - start
        runStartMs = null
        if (duration <= 0) return FpsRunResult(0.0, 0.0, 0.0, 0)
        fun avg(stream: FpsStream) = runCounts.getValue(stream) * MS_PER_SECOND / duration
        return FpsRunResult(avg(FpsStream.POSE), avg(FpsStream.HAND), avg(FpsStream.COMBINED), duration)
    }

    private fun record(stream: FpsStream) {
        val queue = events.getValue(stream)
        queue.addLast(clock())
        trim(queue)
        if (runStartMs != null) runCounts[stream] = runCounts.getValue(stream) + 1
    }

    private fun trim(queue: ArrayDeque<Long>) {
        val cutoff = clock() - windowMs
        while (queue.isNotEmpty() && queue.first() <= cutoff) queue.removeFirst()
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 1_000L
        const val RUN_DURATION_MS = 60_000L
        private const val MS_PER_SECOND = 1_000.0
        private const val MAX_PENDING = 64
    }
}
