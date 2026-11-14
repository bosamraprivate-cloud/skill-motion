package com.aitherapist.dls.ui.child

import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.session.CloseReason

/**
 * Story 6.2: the calm per-step time ring. It is [startFraction] full now and, while [running], fills linearly to full
 * over [remainingMs]. No digits, no ticking, no failure colour: running out of time is never a failure.
 */
data class TimeRing(
    val totalMs: Long,
    val startFraction: Float,
    val remainingMs: Long,
    val running: Boolean,
    val level: EffectLevel,
    val ending: Ending? = null,
    /** The attempt this ring belongs to (window open time); a new key restarts the fill animation. */
    val key: Long = 0L
) {
    enum class Ending { DONE_EARLY, TIME_UP }

    /** FULL only: a gentle pulse in the last [PULSE_LAST_MS] while the ring still fills. CALM stays static. */
    fun pulses(fraction: Float): Boolean = level == EffectLevel.FULL &&
        running &&
        ending == null &&
        totalMs > 0 &&
        fraction < 1f &&
        fraction >= 1f - PULSE_LAST_MS.toFloat() / totalMs

    companion object {
        const val PULSE_LAST_MS = 3_000L
    }
}

/**
 * Pure ring state machine (unit tested). Time passes only inside an open attempt window: while instruction audio
 * plays the ring waits empty; adult pause or an inactive screen freezes it where it was (the runner restarts the
 * attempt with a fresh window on resume, so the ring then starts again with it).
 */
class TimeRingTracker {
    private var openedAtMs: Long? = null
    private var totalMs = 0L
    private var fraction = 0f
    private var held = false
    private var closedFor: Long? = null

    /** Instruction audio plays / the window is not open yet: empty and still. */
    fun waiting(totalMs: Long, level: EffectLevel): TimeRing {
        openedAtMs = null
        held = false
        fraction = 0f
        this.totalMs = totalMs
        return TimeRing(totalMs, 0f, totalMs, running = false, level = level)
    }

    /** The window opened at [openedAtMs] is open at [nowMs]; [held] freezes the ring at the moment the hold began. */
    fun attempt(openedAtMs: Long, totalMs: Long, nowMs: Long, held: Boolean, level: EffectLevel): TimeRing {
        if (this.openedAtMs != openedAtMs) {
            this.openedAtMs = openedAtMs
            this.totalMs = totalMs
            this.held = false
        }
        val now = progress(openedAtMs, totalMs, nowMs)
        if (!held || !this.held) fraction = now
        this.held = held
        return if (held) {
            TimeRing(totalMs, fraction, 0L, running = false, level = level, key = openedAtMs)
        } else {
            val remaining = (totalMs - (nowMs - openedAtMs)).coerceIn(0L, totalMs)
            TimeRing(totalMs, fraction, remaining, running = remaining > 0, level = level, key = openedAtMs)
        }
    }

    /** Adult pause (the runner dropped the window): the ring stays as it was, still. */
    fun paused(level: EffectLevel): TimeRing = TimeRing(totalMs, fraction, 0L, running = false, level = level, key = openedAtMs ?: 0L)

    /**
     * The runner closed the window opened at [openedAtMs] for [reason] at [nowMs]. Returns the ending once per window
     * (Done or still before the end: [TimeRing.Ending.DONE_EARLY]; the step time ran out: [TimeRing.Ending.TIME_UP]),
     * or null for a window already handled.
     */
    fun closed(openedAtMs: Long, reason: CloseReason, nowMs: Long, level: EffectLevel): TimeRing? {
        if (closedFor == openedAtMs) return null
        closedFor = openedAtMs
        val total = if (this.openedAtMs == openedAtMs) totalMs else this.totalMs
        val ending = if (reason == CloseReason.TIMEOUT) TimeRing.Ending.TIME_UP else TimeRing.Ending.DONE_EARLY
        fraction = if (ending == TimeRing.Ending.TIME_UP) 1f else progress(openedAtMs, total, nowMs)
        return TimeRing(total, fraction, 0L, running = false, level = level, ending = ending, key = openedAtMs)
    }

    private fun progress(openedAtMs: Long, totalMs: Long, nowMs: Long): Float =
        if (totalMs <= 0) 1f else ((nowMs - openedAtMs).toFloat() / totalMs).coerceIn(0f, 1f)
}
