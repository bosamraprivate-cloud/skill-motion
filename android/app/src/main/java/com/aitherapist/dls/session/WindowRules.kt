package com.aitherapist.dls.session

/**
 * Attempt-window and movement rules (FR-13). Rules, not clinical content; live and replay scoring
 * both read these so they time windows identically. Bump [RULES_VERSION] on any change.
 */
object WindowRules {
    const val RULES_VERSION = 1

    /** After this much elapsed time, a still child closes the window. */
    const val STILL_AFTER_MS = 20_000L

    /** No movement for this long counts as still. */
    const val STILL_FOR_MS = 2_000L

    /** Hard cap: the window always closes at this elapsed time. */
    const val TIMEOUT_MS = 45_000L

    /** Movement is measured over this trailing span. */
    const val MOVEMENT_SPAN_MS = 500L

    /** Movement threshold as a fraction of shoulder width (strictly greater counts). */
    const val MOVEMENT_SHOULDER_FRACTION = 0.03
}
