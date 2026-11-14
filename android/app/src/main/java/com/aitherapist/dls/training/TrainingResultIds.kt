package com.aitherapist.dls.training

/** The one format of training result ids: `"$runId:$index"` (one per finished activity of a run). */
object TrainingResultIds {
    fun make(runId: String, index: Int): String = "$runId:$index"

    /** Run id of [resultId] (everything before the last ':'). */
    fun runIdOf(resultId: String): String = resultId.substringBeforeLast(':')

    /** Prefix shared by every result id of [runId]. */
    fun runPrefix(runId: String): String = "$runId:"
}
