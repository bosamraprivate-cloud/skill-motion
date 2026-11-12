package com.aitherapist.dls.session

/** Prompt state for one step (FR-12): [level] 1..3, whether Help was pressed, and the attempt number. */
data class PromptState(val level: Int = MIN_LEVEL, val helpPressed: Boolean = false, val attempt: Int = 1) {
    init {
        require(level in MIN_LEVEL..MAX_LEVEL) { "level must be 1..3" }
        require(attempt >= 1) { "attempt must be >= 1" }
    }

    companion object {
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 3
    }
}

/** Help/Repeat transitions (FR-12). Pure. */
object PromptLadder {
    /** Help raises the level (cap 3) and sets helpPressed; the attempt number is unchanged. */
    fun onHelp(state: PromptState): PromptState =
        state.copy(level = (state.level + 1).coerceAtMost(PromptState.MAX_LEVEL), helpPressed = true)

    /** Repeat replays the prompt and changes nothing. */
    fun onRepeat(state: PromptState): PromptState = state
}
