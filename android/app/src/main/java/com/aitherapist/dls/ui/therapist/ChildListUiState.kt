package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.ChildCharacter

/** One list row; the character thumbnail comes from [character]. */
data class ChildRow(val id: String, val name: String, val code: String, val ageYears: Int, val character: ChildCharacter) {
    override fun toString(): String = "ChildRow(id=$id)"
}

sealed interface ChildListUiState {
    data object Loading : ChildListUiState

    /** No visible children at all; the eval child never counts. */
    data object Empty : ChildListUiState

    /** [children] is already filtered by [query]; it may be empty when nothing matches. */
    data class Content(val query: String, val children: List<ChildRow>) : ChildListUiState

    data class Error(val reason: String) : ChildListUiState
}

/** Case-insensitive match on name or code; a blank query keeps everything. */
fun filterChildren(rows: List<ChildRow>, query: String): List<ChildRow> {
    val q = query.trim()
    if (q.isEmpty()) return rows
    return rows.filter { it.name.contains(q, ignoreCase = true) || it.code.contains(q, ignoreCase = true) }
}
