package com.aitherapist.dls.ui.child

import com.aitherapist.dls.session.Clock

/** Accepts one Done per [windowMs] (a clicker or a double tap must not send two). */
class DoneDebouncer(private val clock: Clock, private val windowMs: Long = WINDOW_MS) {
    private var lastMs: Long? = null

    /** True when this press counts; false when it came within [windowMs] of the last accepted one. */
    fun accept(): Boolean {
        val now = clock.nowMs()
        val last = lastMs
        if (last != null && now - last < windowMs) return false
        lastMs = now
        return true
    }

    companion object {
        const val WINDOW_MS = 1_000L
    }
}
