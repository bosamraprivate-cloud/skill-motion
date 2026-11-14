package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Activity
import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.data.SessionResult
import com.aitherapist.dls.scoring.PlanLevel
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.scoring.StepLevel

/** One activity row; every text is the catalogue text verbatim. [cameraCounted] is true when it has a `counter`. */
data class PlanActivity(
    val id: String,
    val textAr: String,
    val title: String,
    val tools: String,
    val howToPerform: String,
    val goal: String,
    val criterion: String,
    val cameraCounted: Boolean
)

/** A task that fed a plan section, with that task's domain average (null = no scored steps, shown as a dash). */
data class PlanSource(val taskId: String, val taskName: String, val average: Double?, val planLevel: PlanLevel?)

/** One plan section: a weak [domain] at the weakest [planLevel] seen across [sources], and its activities. */
data class PlanSectionUi(val domain: String, val planLevel: PlanLevel, val sources: List<PlanSource>, val activities: List<PlanActivity>)

sealed interface PlanUiState {
    data object Loading : PlanUiState

    /** No finished assessment yet. */
    data object Empty : PlanUiState

    /** Finished assessments exist but no domain is weak: no plan needed. */
    data object AllStrengths : PlanUiState

    /** [reloadError] is true when a reload failed: the old content stays, with an inline retry. */
    data class Content(val sections: List<PlanSectionUi>, val selected: Set<String>, val reloadError: Boolean = false) : PlanUiState {
        /** Selected ids in display order, each once. */
        val selectedInOrder: List<String> get() = sections.flatMap { s -> s.activities.map { it.id } }.distinct().filter { it in selected }
    }

    data class Error(val reason: String) : PlanUiState
}

fun Activity.toPlanActivity() = PlanActivity(id, textAr, title, tools, howToPerform, goal, criterion, counter != null)

/**
 * The plan of the latest finished assessment per task (coordinator decision, Story 4.3 review): one section per
 * domain across all tasks, at the weakest plan level that domain reached in any task; each task that covers the
 * domain is listed as a source with its own average. Order and activities come from [RulesEngine.activitiesFor].
 */
fun buildPlan(results: List<SessionResult>, catalog: Catalog): List<PlanSectionUi> {
    val taskOrder = catalog.tasks.mapIndexed { i, t -> t.id to i }.toMap()
    val tags = catalog.steps.associate { it.id to it.tags }
    val sources = LinkedHashMap<String, MutableList<PlanSource>>()
    results.sortedBy { taskOrder[it.session.taskId] ?: Int.MAX_VALUE }.forEach { result ->
        val task = catalog.tasks.firstOrNull { it.id == result.session.taskId }
        val covered = task?.steps.orEmpty().flatMap { RulesEngine.domainsOf(it.tags) }.toSet()
        val averages = RulesEngine.domainAverages(LinkedHashMap<String, StepLevel?>(result.levels), tags).averages
        averages.forEach { (domain, avg) ->
            if (avg != null || domain in covered) {
                sources.getOrPut(domain) { mutableListOf() } +=
                    PlanSource(result.session.taskId, task?.nameAr ?: result.session.taskId, avg, avg?.let(RulesEngine::planLevel))
            }
        }
    }
    val levels =
        sources.mapNotNull { (domain, list) -> list.mapNotNull { it.planLevel }.maxByOrNull { it.ordinal }?.let { domain to it } }.toMap()
    return RulesEngine.activitiesFor(levels, catalog.activities).map { section ->
        PlanSectionUi(section.domain, section.planLevel, sources.getValue(section.domain), section.activities.map { it.toPlanActivity() })
    }
}
