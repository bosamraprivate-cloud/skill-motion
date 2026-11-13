package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.ChildCharacter

/** One list row; the character thumbnail comes from [character]. */
data class ChildRow(val id: String, val name: String, val ageYears: Int, val character: ChildCharacter) {
    override fun toString(): String = "ChildRow(id=$id)"
}

/** An interrupted (running or paused) session: child name and task only, never levels or results. */
data class ContinueRow(
    val sessionId: String,
    val childId: String,
    val childName: String,
    val taskId: String,
    /** Arabic task name from the catalogue (the id when it cannot load). */
    val taskName: String = taskId
) {
    override fun toString(): String = "ContinueRow(sessionId=$sessionId)"
}

sealed interface ChildListUiState {
    data object Loading : ChildListUiState

    /** No visible children at all; the eval child never counts. */
    data object Empty : ChildListUiState

    /** [children] is already filtered by [query]; it may be empty when nothing matches. */
    data class Content(val query: String, val children: List<ChildRow>, val continueRows: List<ContinueRow> = emptyList()) :
        ChildListUiState

    data class Error(val reason: String) : ChildListUiState
}

/** Case-insensitive match on name; a blank query keeps everything. */
fun filterChildren(rows: List<ChildRow>, query: String): List<ChildRow> {
    val q = query.trim()
    if (q.isEmpty()) return rows
    return rows.filter { it.name.contains(q, ignoreCase = true) }
}
