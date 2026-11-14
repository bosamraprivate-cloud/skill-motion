package com.aitherapist.dls.ui.child

/**
 * The step visual: the still at level 1; at levels 2-3 the demonstration clip segment (plays once, holds its last
 * frame; [large] at level 3), or the still enlarged when the clip is missing.
 */
data class StepVisual(val clip: String?, val still: String, val animate: Boolean, val large: Boolean)

/** What the child sees. No scores, levels, errors or network state ever reach the child. */
sealed interface StepUiState {
    /** Loading, a failed save being retried, or content that could not load: a calm "wait" visual. */
    data object Waiting : StepUiState

    data class Step(
        val stepIndex: Int,
        val stepCount: Int,
        val sentence: String,
        /** Null when the step has no media for the child's character (dev catalogue): reaction character only. */
        val visual: StepVisual?,
        /** Side character clip asset path; null hides the character. */
        val reactionClip: String?,
        val doneEnabled: Boolean,
        val promptLevel: Int,
        val stars: Int = 0,
        /** Adult pause: the screen dims and the character idles; Done/Help/Repeat do nothing. */
        val paused: Boolean = false,
        /** Portrait layout clip: the character stands above the main area, so `point` (sideways) becomes `idle`. */
        val portraitClip: String? = reactionClip
    ) : StepUiState

    /** New session (Story 2.16): task name, task picture and a big Start; the first step waits for the tap. */
    data class Start(
        val taskName: String?,
        val picture: String?,
        val reactionClip: String?,
        val paused: Boolean = false,
        val portraitClip: String? = reactionClip
    ) : StepUiState

    /** Adult pause dim is on (step or start screen). */
    val isPaused: Boolean
        get() = (this as? Step)?.paused == true || (this as? Start)?.paused == true

    /** Every step finished and the session saved `done`: calm end visual until the adult leaves through the PIN. */
    data class Finished(val reactionClip: String?) : StepUiState
}
