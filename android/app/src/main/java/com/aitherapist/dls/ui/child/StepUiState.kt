package com.aitherapist.dls.ui.child

/**
 * The step visual, shown only from level 2 (level 1 has no card): the demonstration clip segment, looping once the
 * card is up ([animate]; [large] at level 3), or the still enlarged when the clip is missing.
 */
data class StepVisual(val clip: String?, val still: String, val animate: Boolean, val large: Boolean)

/**
 * What the step's main area stages (owner design, EXPERIENCE "Prompt staging"):
 * level 1 = [CHARACTER_HERO] (the side character large and centred, no card); level 2-3 = a short [HANDOFF]
 * (the character points toward where the card appears, then fades away) and then [CLIP] (the demonstration card).
 */
enum class StepStage { CHARACTER_HERO, HANDOFF, CLIP }

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
        /** Story 6.2: the calm time ring; null when the child's ring is off or the step has no time (older catalogue). */
        val ring: TimeRing? = null,
        /** Which visual the main area stages; the ring frames the character (hero) or the card (handoff, clip). */
        val stage: StepStage = StepStage.CHARACTER_HERO,
        /** Calm effect level: stage changes are crossfades only, nothing moves. */
        val calm: Boolean = false
    ) : StepUiState

    /**
     * New session (Story 2.16): the character large (the same guide the child meets on every step), the task name
     * and a big Start; the first step waits for the tap.
     */
    data class Start(val taskName: String?, val reactionClip: String?, val paused: Boolean = false) : StepUiState

    /** Adult pause dim is on (step or start screen). */
    val isPaused: Boolean
        get() = (this as? Step)?.paused == true || (this as? Start)?.paused == true

    /** Every step finished and the session saved `done`: calm end visual until the adult leaves through the PIN. */
    data class Finished(val reactionClip: String?) : StepUiState
}
