package com.aitherapist.dls.camera

import kotlin.math.abs

/** A frame paired with the nearest landmark result within the pairing window (AD-3). */
data class PairedFrame<F, R>(val timestampMs: Long, val frame: F, val result: R?) {
    val visible: Boolean get() = result != null
}

/**
 * Pure ring buffer pairing frames with landmark results by timestamp (AD-3).
 * Keeps the last [retentionMs] of frames; a frame is paired with the result whose timestamp is
 * nearest within [pairWindowMs]. A frame without such a result is `visible = false` once the
 * window has passed, and undecided (null) before that. Thread-safe.
 */
class FrameBuffer<F, R>(
    private val clock: () -> Long,
    private val retentionMs: Long = DEFAULT_RETENTION_MS,
    private val pairWindowMs: Long = DEFAULT_PAIR_WINDOW_MS
) {
    private val frames = ArrayDeque<Pair<Long, F>>()
    private val results = ArrayDeque<Pair<Long, R>>()

    @Synchronized
    fun addFrame(timestampMs: Long, frame: F) {
        frames.addLast(timestampMs to frame)
        evict()
    }

    @Synchronized
    fun addResult(timestampMs: Long, result: R) {
        results.addLast(timestampMs to result)
        evict()
    }

    /** The paired frame captured at [timestampMs], or null if unknown or still undecided. */
    @Synchronized
    fun paired(timestampMs: Long): PairedFrame<F, R>? {
        evict()
        val frame = frames.firstOrNull { it.first == timestampMs } ?: return null
        return resolve(frame)
    }

    /** All frames whose pairing is decided, oldest first. */
    @Synchronized
    fun resolved(): List<PairedFrame<F, R>> {
        evict()
        return frames.mapNotNull { resolve(it) }
    }

    @Synchronized
    fun clear() {
        frames.clear()
        results.clear()
    }

    @Synchronized
    fun frameCount(): Int {
        evict()
        return frames.size
    }

    private fun resolve(frame: Pair<Long, F>): PairedFrame<F, R>? {
        val (ts, value) = frame
        val nearest =
            results
                .filter { abs(it.first - ts) <= pairWindowMs }
                .minByOrNull { abs(it.first - ts) }
        return when {
            nearest != null -> PairedFrame(ts, value, nearest.second)
            clock() >= ts + pairWindowMs -> PairedFrame(ts, value, null)
            else -> null
        }
    }

    private fun evict() {
        val cutoff = clock() - retentionMs
        while (frames.isNotEmpty() && frames.first().first < cutoff) frames.removeFirst()
        while (results.isNotEmpty() && results.first().first < cutoff - pairWindowMs) results.removeFirst()
    }

    companion object {
        const val DEFAULT_RETENTION_MS = 3_000L
        const val DEFAULT_PAIR_WINDOW_MS = 100L
    }
}
