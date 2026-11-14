package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.data.SessionResult
import com.aitherapist.dls.data.TrainingResultEntity
import com.aitherapist.dls.scoring.RulesEngine
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** A finished assessment offered in the compare pickers; [at] is its startedAt. */
data class CompareSession(val id: String, val taskId: String, val taskName: String, val at: Long)

/** Calm direction word of a difference (never red, never a verdict). */
enum class Trend { UP, SLIGHTLY_UP, DOWN, SLIGHTLY_DOWN, SAME }

/**
 * One compare row: averages as the report shows them (rounded down); [difference] is the real B - A rounded toward
 * zero to one decimal and signed, or null under 0.1 (the trend word alone then says "slightly").
 */
data class CompareRow(
    val domain: String,
    val averageA: String,
    val averageB: String,
    val difference: String?,
    val trend: Trend,
    val steps: Int
)

/** Compare, always older (A, [olderAt]) to newer (B, [newerAt]) by startedAt; [rows] empty = no common scored steps. */
data class CompareResult(val rows: List<CompareRow>, val commonSteps: Int, val olderAt: Long = 0L, val newerAt: Long = 0L)

sealed interface CompareUiState {
    data object Loading : CompareUiState

    /** No finished assessment has a second one of the same task: hint to run another assessment. */
    data object NotEnough : CompareUiState

    data class Content(
        val sessions: List<CompareSession>,
        val firstId: String?,
        val secondChoices: List<CompareSession>,
        val secondId: String?,
        val result: CompareResult?
    ) : CompareUiState

    data class Error(val reason: String) : CompareUiState
}

/** One saved training activity row. [count] null = guided (shown as "—"). */
data class TrainingRow(val id: String, val title: String, val count: Int?, val durationSec: Int, val criterionMet: Boolean, val at: Long)

sealed interface TrainingHistoryUiState {
    data object Loading : TrainingHistoryUiState

    data object Empty : TrainingHistoryUiState

    data class Content(val rows: List<TrainingRow>) : TrainingHistoryUiState

    data class Error(val reason: String) : TrainingHistoryUiState
}

private const val TENTHS = 10.0
private const val EPSILON = 1e-9

/** Real difference [diff] (B - A) rounded toward zero to one decimal, signed; null text below 0.1. Exactly 0 is SAME. */
fun formatDifference(diff: Double): Pair<String?, Trend> {
    if (abs(diff) < EPSILON) return "0.0" to Trend.SAME
    val tenths = floor(abs(diff) * TENTHS + EPSILON).toLong()
    val up = diff > 0
    if (tenths == 0L) return null to (if (up) Trend.SLIGHTLY_UP else Trend.SLIGHTLY_DOWN)
    val text = String.format(Locale.US, "%.1f", tenths / TENTHS)
    return (if (up) "+$text" else "-$text") to (if (up) Trend.UP else Trend.DOWN)
}

/** The second picker: other finished sessions of the same task as [firstId] (newest first). */
fun secondChoices(sessions: List<CompareSession>, firstId: String?): List<CompareSession> {
    val first = sessions.firstOrNull { it.id == firstId } ?: return emptyList()
    return sessions.filter { it.taskId == first.taskId && it.id != first.id }
}

/** Sessions that can be compared: only tasks with at least two finished assessments. */
fun comparableSessions(results: List<SessionResult>, catalog: Catalog): List<CompareSession> {
    val names = catalog.tasks.associate { it.id to it.nameAr }
    val perTask = results.groupingBy { it.session.taskId }.eachCount()
    return results.filter { (perTask[it.session.taskId] ?: 0) >= 2 }.map {
        CompareSession(
            it.session.id,
            it.session.taskId,
            names[it.session.taskId] ?: it.session.taskId,
            it.session.startedAt
        )
    }
}

/** Runs [RulesEngine.compare] on effective levels, always older (by startedAt) to newer, and formats the rows. */
fun compareResult(x: SessionResult, y: SessionResult, catalog: Catalog): CompareResult {
    val (first, second) = if (y.session.startedAt < x.session.startedAt) y to x else x to y
    val tags = catalog.steps.associate { it.id to it.tags }
    val comparison = RulesEngine.compare(first.levels, second.levels, tags)
    return CompareResult(
        comparison.domains.map {
            val (diff, trend) = formatDifference(it.difference)
            CompareRow(it.domain, formatAverage(it.averageA), formatAverage(it.averageB), diff, trend, it.steps)
        },
        comparison.commonSteps,
        first.session.startedAt,
        second.session.startedAt
    )
}

/** Training rows in saved order (newest first), titled with the activity's Arabic title (its id when unknown). */
fun trainingRows(results: List<TrainingResultEntity>, catalog: Catalog?): List<TrainingRow> {
    val titles = catalog?.activities?.associate { it.id to it.title }.orEmpty()
    return results.sortedWith(compareByDescending { it.createdAt }).map {
        TrainingRow(it.id, titles[it.activityId] ?: it.activityId, it.count, it.durationSec, it.criterionMet, it.createdAt)
    }
}
