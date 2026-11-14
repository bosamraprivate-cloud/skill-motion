package com.aitherapist.dls.ui.child

/**
 * Pure flash timing for reward effects (Story 4.8, WCAG 2.3.1): never more than [MAX_PER_SECOND] flashes in any
 * one-second window. A "flash" is one star twinkle pulse.
 */
object FlashSchedule {
    const val MAX_PER_SECOND = 3
    private const val WINDOW_MS = 1_000L

    /** Smallest gap that keeps any 1 s window at [MAX_PER_SECOND] flashes or fewer. */
    const val MIN_GAP_MS = WINDOW_MS / MAX_PER_SECOND + 1

    /**
     * Start times (ms from effect start) for up to [count] flashes inside [durationMs], evenly spaced but never
     * closer than [MIN_GAP_MS]; flashes that do not fit are dropped.
     */
    fun times(count: Int, durationMs: Long): List<Long> {
        if (count <= 0 || durationMs <= 0) return emptyList()
        val gap = (durationMs / count).coerceAtLeast(MIN_GAP_MS)
        return (0 until count).map { it * gap }.filter { it < durationMs }
    }

    /** True when no one-second window holds more than [MAX_PER_SECOND] of [times]. */
    fun isSafe(times: List<Long>): Boolean {
        val sorted = times.sorted()
        return sorted.indices.all { i -> sorted.count { it >= sorted[i] && it < sorted[i] + WINDOW_MS } <= MAX_PER_SECOND }
    }
}
