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

    /** Every step finished: calm end visual until the adult leaves through the PIN. */
    data class Finished(val reactionClip: String?) : StepUiState
}
