package com.aitherapist.dls.ui.child

/** The step visual: a still (levels 1-2, scaled at level 2) or the demonstration clip segment (level 3). */
data class StepVisual(val clip: String?, val still: String, val animate: Boolean, val scale: Float, val large: Boolean)

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
        /** Reaction clip asset path; null hides the reaction character. */
        val reactionClip: String?,
        val doneEnabled: Boolean,
        val promptLevel: Int,
        val stars: Int = 0,
        /** Adult pause: the screen dims and the character idles; Done/Help/Repeat do nothing. */
        val paused: Boolean = false
    ) : StepUiState

    /** New session (Story 2.16): task name, task picture and a big Start; the first step waits for the tap. */
    data class Start(val taskName: String?, val picture: String?, val reactionClip: String?, val paused: Boolean = false) : StepUiState

    /**
     * Closing character walkthrough: each step's demonstration clip (or still) with its audio, back to back.
     * [visual] null (no media for the child's character) shows the reaction character instead. No buttons.
     */
    data class Walkthrough(
        val stepIndex: Int,
        val stepCount: Int,
        val sentence: String,
        val visual: StepVisual?,
        val reactionClip: String?,
        val paused: Boolean = false
    ) : StepUiState

    /** Adult pause dim is on (step, start screen or walkthrough). */
    val isPaused: Boolean
        get() = (this as? Step)?.paused == true || (this as? Start)?.paused == true || (this as? Walkthrough)?.paused == true

    /** Every step finished (after the walkthrough): calm end visual until the adult leaves through the PIN. */
    data class Finished(val reactionClip: String?) : StepUiState
}
