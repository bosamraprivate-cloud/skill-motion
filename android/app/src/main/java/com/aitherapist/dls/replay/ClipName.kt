package com.aitherapist.dls.replay

import com.aitherapist.dls.session.PromptState

/** The scripted outcome token in a clip name. */
enum class ScriptedOutcome(val token: String) {
    DONE("done"),
    NOT_DONE("not_done");

    companion object {
        fun fromToken(token: String): ScriptedOutcome? = entries.firstOrNull { it.token == token }
    }
}

/** Why a clip was skipped (shown to the therapist with the file name). */
enum class ClipProblem {
    MALFORMED_NAME,
    BAD_ATTEMPT,
    BAD_OUTCOME,
    UNKNOWN_STEP,
    MISSING_FILE,
    COPY_FAILED,
    SAVE_FAILED,
    DUPLICATE,
    TOO_LONG
}

/**
 * A replay clip name `<stepId>__<attemptNumber>__<outcome>__<volunteer>.mp4` (design "Replay"), the join key with
 * `eval/clips.csv`. The prompt level is the one the live ladder gives that attempt number without Help (capped at 3).
 */
data class ClipName(val fileName: String, val stepId: String, val attemptNumber: Int, val outcome: ScriptedOutcome, val volunteer: String) {
    val promptLevel: Int get() = attemptNumber.coerceIn(PromptState.MIN_LEVEL, PromptState.MAX_LEVEL)

    /** A parse result: the name, or the problem that made it unusable. */
    sealed interface Parsed {
        data class Ok(val clip: ClipName) : Parsed

        data class Invalid(val fileName: String, val problem: ClipProblem) : Parsed
    }

    companion object {
        private const val EXTENSION = ".mp4"
        private const val SEPARATOR = "__"
        private const val PARTS = 4
        private val PART = Regex("[A-Za-z0-9._-]+")

        fun parse(fileName: String): Parsed {
            fun invalid(problem: ClipProblem) = Parsed.Invalid(fileName, problem)
            if (!fileName.endsWith(EXTENSION, ignoreCase = true)) return invalid(ClipProblem.MALFORMED_NAME)
            val parts = fileName.dropLast(EXTENSION.length).split(SEPARATOR)
            if (parts.size != PARTS || parts.any { !PART.matches(it) || it.startsWith('_') || it.endsWith('_') }) {
                return invalid(ClipProblem.MALFORMED_NAME)
            }
            val (stepId, attempt, outcome, volunteer) = parts
            val number = attempt.toIntOrNull()?.takeIf { it >= 1 } ?: return invalid(ClipProblem.BAD_ATTEMPT)
            val scripted = ScriptedOutcome.fromToken(outcome) ?: return invalid(ClipProblem.BAD_OUTCOME)
            return Parsed.Ok(ClipName(fileName, stepId, number, scripted, volunteer))
        }
    }
}
