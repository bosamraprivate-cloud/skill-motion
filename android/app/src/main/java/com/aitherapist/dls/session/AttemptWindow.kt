package com.aitherapist.dls.session

/** Injected time source so live and replay scoring time windows identically. */
fun interface Clock {
    fun nowMs(): Long
}

/** Why an attempt window closed (FR-13). [storageName] is the lower_snake value to persist. */
enum class CloseReason(val storageName: String) { DONE("done"), STILL("still"), TIMEOUT("timeout") }

/**
 * One attempt window, opened when instruction audio ends ([openedAtMs], passed by the caller).
 * Closes on Done; or elapsed >= 20 s with no movement in the last 2 s; or elapsed >= 45 s.
 */
class AttemptWindow(val openedAtMs: Long) {
    private val samples = ArrayDeque<PoseSample>()
    private var lastMovementMs: Long = openedAtMs
    private var doneAtMs: Long? = null
    private var closed: CloseReason? = null

    /** Feed one pose sample. Samples before the window opened, out of order, or after close are dropped. */
    fun onSample(sample: PoseSample) {
        if (closed != null || sample.tMs < openedAtMs) return
        if (samples.isNotEmpty() && sample.tMs < samples.last().tMs) return
        samples.addLast(sample)
        while (samples.first().tMs < sample.tMs - WindowRules.MOVEMENT_SPAN_MS) samples.removeFirst()
        // The still clock runs only on samples with a pose: a sample without one cannot show stillness.
        if (!sample.hasPose || Movement.moved(samples, sample.tMs)) lastMovementMs = sample.tMs
    }

    /** The child (or HID key) pressed Done; counts only if the window had not already closed at [nowMs]. */
    fun onDone(nowMs: Long) {
        if (closed != null || doneAtMs != null) return
        if (check(nowMs) == null) doneAtMs = nowMs
    }

    /** The close reason at [nowMs], or null while open. The first non-null reason is latched. */
    fun check(nowMs: Long): CloseReason? {
        closed?.let { return it }
        val elapsed = nowMs - openedAtMs
        val reason =
            when {
                doneAtMs?.let { nowMs >= it } == true -> CloseReason.DONE
                elapsed >= WindowRules.TIMEOUT_MS -> CloseReason.TIMEOUT
                elapsed >= WindowRules.STILL_AFTER_MS && nowMs - lastMovementMs >= WindowRules.STILL_FOR_MS -> CloseReason.STILL
                else -> null
            }
        closed = reason
        return reason
    }

    /** [check] at the [clock]'s current time. */
    fun check(clock: Clock): CloseReason? = check(clock.nowMs())
}
