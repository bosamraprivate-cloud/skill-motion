package com.aitherapist.dls.ui.child

import com.aitherapist.dls.data.EffectLevel

/** The step-end moment on screen (Story 4.8). [id] makes each moment distinct so it plays once. */
data class StepEffect(val id: Int, val kind: Kind, val level: EffectLevel) {
    enum class Kind { CELEBRATE, ENCOURAGE }

    /** Upper bound on screen; the Done/next flow is never held longer than this. */
    val durationMs: Long
        get() = when {
            kind == Kind.CELEBRATE && level == EffectLevel.FULL -> FULL_MS
            else -> CALM_MS
        }

    companion object {
        const val FULL_MS = 2_000L
        const val CALM_MS = 1_500L
    }
}
