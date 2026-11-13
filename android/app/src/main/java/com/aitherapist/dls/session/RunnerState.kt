package com.aitherapist.dls.session

/** Assessment runner states (design "Session runner"): SHOW -> LISTEN -> ATTEMPT -> JUDGE per attempt. */
sealed interface RunnerState {
    /** Before [AssessmentRunner.start]. */
    data object Idle : RunnerState

    /** A state on one step: its index, id and prompt (level, help, attempt number). */
    sealed interface OnStep : RunnerState {
        val stepIndex: Int
        val stepId: String
        val prompt: PromptState
    }

    data class Show(override val stepIndex: Int, override val stepId: String, override val prompt: PromptState) : OnStep

    /** Instruction audio plays; the UI sends AudioEnded. */
    data class Listen(override val stepIndex: Int, override val stepId: String, override val prompt: PromptState) : OnStep

    /** Attempt window open since [openedAtMs]. */
    data class Attempt(override val stepIndex: Int, override val stepId: String, override val prompt: PromptState, val openedAtMs: Long) :
        OnStep

    /** Window closed; the verifier runs. */
    data class Judge(override val stepIndex: Int, override val stepId: String, override val prompt: PromptState) : OnStep

    /** Adult pause: no window, nothing judged; [AssessmentRunner.resume] restarts this attempt from LISTEN. */
    data class Paused(override val stepIndex: Int, override val stepId: String, override val prompt: PromptState) : OnStep

    /** Every step recorded; the session stays resumable until [AssessmentRunner.complete] runs. */
    data object Finished : RunnerState

    /** After the last step: the session is `done`. */
    data object Completed : RunnerState

    /** A save failed; nothing advanced. [Event.Retry] repeats the failed save from [previous]. */
    data class Error(val previous: RunnerState) : RunnerState
}
