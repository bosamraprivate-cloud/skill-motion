package com.aitherapist.dls.session

import com.aitherapist.dls.data.AttemptVerdict

/** How a finished step ended, for the child's step-end effect (Story 4.8). Never feeds scoring. */
data class StepEnd(val stepIndex: Int, val outcome: Outcome) {
    enum class Outcome {
        /** Done on some attempt: celebration. */
        DONE,

        /** Not done after the last attempt: encouragement, nothing negative. */
        NOT_DONE,

        /** Ended in doubt (not scored): the child never notices doubt, so it is celebrated like taking part. */
        TOOK_PART
    }

    companion object {
        fun of(stepIndex: Int, verdicts: List<AttemptVerdict>): StepEnd = StepEnd(
            stepIndex,
            when {
                verdicts.any { it == AttemptVerdict.DONE } -> Outcome.DONE
                verdicts.lastOrNull() == AttemptVerdict.NOT_DONE -> Outcome.NOT_DONE
                else -> Outcome.TOOK_PART
            }
        )
    }
}
