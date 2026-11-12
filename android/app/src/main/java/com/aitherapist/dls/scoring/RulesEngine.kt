package com.aitherapist.dls.scoring

/** Rule 1 step level (design "Rules engine"). Levels are computed, never stored. */
enum class StepLevel { THREE, TWO, ONE, NOT_SCORED }

/** Rule 1 outcome of one saved attempt row. Doubt is never failure. */
enum class RuleOutcome { DONE, NOT_DONE, CANNOT_TELL }

/** The facts of one saved attempt row that Rule 1 reads, in saved order. */
data class RuleAttempt(val number: Int, val promptLevel: Int, val outcome: RuleOutcome, val verifierNone: Boolean = false)

/** Pure scoring rules (no Android, Room or coroutines). Bump [RULES_VERSION] on any rule change. */
object RulesEngine {
    const val RULES_VERSION = "1"

    private const val NOT_DONE_LIMIT = 3

    /**
     * Rule 1 on one step's [rows] in saved order. A [correction] wins. Walking the rows: a `none`
     * verifier row -> not scored; the first `done` -> THREE on number 1 at prompt level 1, else TWO;
     * two consecutive `cannot_tell` on one number -> not scored; three `not_done` numbers -> ONE.
     * Returns null while the step is still open (more attempts are due).
     */
    fun stepLevel(rows: List<RuleAttempt>, correction: StepLevel? = null): StepLevel? {
        if (correction != null) return correction
        val notDoneNumbers = mutableSetOf<Int>()
        var previous: RuleAttempt? = null
        for (row in rows) {
            if (row.verifierNone) return StepLevel.NOT_SCORED
            when (row.outcome) {
                RuleOutcome.DONE -> return if (row.number == 1 && row.promptLevel == 1) StepLevel.THREE else StepLevel.TWO
                RuleOutcome.CANNOT_TELL ->
                    if (previous?.outcome == RuleOutcome.CANNOT_TELL && previous.number == row.number) return StepLevel.NOT_SCORED
                RuleOutcome.NOT_DONE -> {
                    notDoneNumbers += row.number
                    if (notDoneNumbers.size >= NOT_DONE_LIMIT) return StepLevel.ONE
                }
            }
            previous = row
        }
        return null
    }
}
