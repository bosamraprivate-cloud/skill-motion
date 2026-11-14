package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.data.SessionResult
import com.aitherapist.dls.scoring.PlanLevel
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.scoring.StepLevel
import java.util.Locale
import kotlin.math.floor

/** A strength: a level-3 step with its catalogue number and Arabic text. */
data class StepLine(val stepId: String, val number: Int?, val text: String)

/** A need: a level-2/1 step that "may be related to" [domains] (empty for an untagged step). */
data class NeedLine(val stepId: String, val number: Int?, val text: String, val level: StepLevel, val domains: List<String>)

/**
 * One domain row; [average] and [planLevel] are null when the domain has no scored steps. [covered] is false when no
 * step of this task is tagged with the domain ("not covered by this task").
 */
data class DomainRow(val domain: String, val average: Double?, val planLevel: PlanLevel?, val scoredSteps: Int, val covered: Boolean = true)

/** The report of one task's latest finished assessment, recomputed on every open. */
data class TaskReport(
    val taskId: String,
    val taskName: String,
    val strengths: List<StepLine>,
    val needs: List<NeedLine>,
    val notScoredCount: Int,
    val domains: List<DomainRow>,
    /** Scored steps without a domain tag. */
    val untaggedCount: Int,
    /** Scored steps missing from the catalogue (feed no domain). */
    val unknownCount: Int,
    /** When the assessment finished (its start when no end is saved); 0 = unknown. */
    val assessedAt: Long = 0L
)

sealed interface ReportUiState {
    data object Loading : ReportUiState

    /** No finished assessment yet. */
    data object Empty : ReportUiState

    data class Content(val tasks: List<TaskReport>) : ReportUiState

    data class Error(val reason: String) : ReportUiState
}

/**
 * An average with one decimal and Western digits, rounded DOWN (2.95 -> "2.9") so the number never contradicts the
 * Rule 2 plan word (2.95 is mild, not "3.0").
 */
fun formatAverage(value: Double): String = String.format(Locale.US, "%.1f", floor(value * TENTHS + EPSILON) / TENTHS)

private const val TENTHS = 10.0

/** Guards against binary error such as 2.3 * 10 = 22.999...; far below one tenth. */
private const val EPSILON = 1e-9

/** Builds one section per result (latest per task), in catalogue task order; unknown tasks keep their order at the end. */
fun buildReport(results: List<SessionResult>, catalog: Catalog): List<TaskReport> {
    val taskOrder = catalog.tasks.mapIndexed { i, t -> t.id to i }.toMap()
    return results.sortedBy { taskOrder[it.session.taskId] ?: Int.MAX_VALUE }.map { taskReport(it, catalog) }
}

private fun taskReport(result: SessionResult, catalog: Catalog): TaskReport {
    val task = catalog.tasks.firstOrNull { it.id == result.session.taskId }
    val steps = task?.steps.orEmpty()
    val byId = catalog.steps.associateBy { it.id }
    val stepOrder = steps.mapIndexed { i, s -> s.id to i }.toMap()
    val ordered = LinkedHashMap<String, StepLevel?>()
    result.levels.entries.sortedBy { stepOrder[it.key] ?: Int.MAX_VALUE }.forEach { ordered[it.key] = it.value }
    // Whole-catalogue tags: a known step outside this task is never "unknown", and every scale domain gets a row.
    val tags = catalog.steps.associate { it.id to it.tags }
    val taskDomains = steps.flatMap { RulesEngine.domainsOf(it.tags) }.toSet()
    val split = RulesEngine.strengthsAndNeeds(ordered, tags)
    val averages = RulesEngine.domainAverages(ordered, tags)
    fun line(id: String) = byId[id]
    return TaskReport(
        taskId = result.session.taskId,
        taskName = task?.nameAr ?: result.session.taskId,
        strengths = split.strengths.map { StepLine(it, line(it)?.number, line(it)?.textAr ?: it) },
        needs = split.needs.map {
            NeedLine(it.stepId, line(it.stepId)?.number, line(it.stepId)?.textAr ?: it.stepId, it.level, it.domains.toList())
        },
        notScoredCount = split.notScored.size,
        domains =
        averages.averages.map { (domain, avg) ->
            DomainRow(domain, avg, avg?.let(RulesEngine::planLevel), averages.scoredCounts[domain] ?: 0, domain in taskDomains)
        },
        untaggedCount = averages.untaggedScored,
        unknownCount = averages.unknownSteps.size,
        assessedAt = result.session.endedAt ?: result.session.startedAt
    )
}
