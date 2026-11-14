package com.aitherapist.dls.scoring

/** Rule 1 step level (design "Rules engine"). Levels are computed, never stored. */
enum class StepLevel { THREE, TWO, ONE, NOT_SCORED }

/** Rule 1 outcome of one saved attempt row. Doubt is never failure. */
enum class RuleOutcome { DONE, NOT_DONE, CANNOT_TELL }

/** The facts of one saved attempt row that Rule 1 reads, in saved order. */
data class RuleAttempt(val number: Int, val promptLevel: Int, val outcome: RuleOutcome, val verifierNone: Boolean = false)

/** Rule 2 result of a domain average (`scoring-rules.md`); [STRENGTH] has no plan. */
enum class PlanLevel { STRENGTH, MILD, MODERATE, SEVERE }

/**
 * Rule 2 per-domain output: [averages] maps every domain named by the tags to its average score, or null when the
 * domain has no scored steps; [scoredCounts] is the number of scored steps behind each average; [untaggedScored]
 * counts scored steps that carry no domain tag (they feed no domain).
 */
data class DomainAverages(
    val averages: Map<String, Double?>,
    val scoredCounts: Map<String, Int>,
    val untaggedScored: Int,
    /** Scored step ids missing from [RulesEngine.domainAverages]'s tag map (not in the catalogue); they feed no domain. */
    val unknownSteps: Set<String> = emptySet()
)

/** A need: a level-2/1 step and the domains it "may be related to" (empty for an untagged or unknown step). */
data class Need(val stepId: String, val level: StepLevel, val domains: Set<String>)

/**
 * Stage 7 strengths and needs of one assessment: [strengths] are level-3 step ids, [needs] level-2/1 steps with
 * their domains, [notScored] steps that ended not scored (listed apart, no blame). Open steps appear nowhere.
 */
data class StrengthsAndNeeds(val strengths: List<String>, val needs: List<Need>, val notScored: List<String>)

/** Pure scoring rules (no Android, Room or coroutines). Bump [RULES_VERSION] on any rule change. */
object RulesEngine {
    const val RULES_VERSION = "3"

    private const val NOT_DONE_LIMIT = 3

    /** `scoring-rules.md` Rule 2: "Visual-motor coordination" counts as Motor coordination (once per step). */
    const val MOTOR_COORDINATION = "Motor coordination"
    const val VISUAL_MOTOR_COORDINATION = "Visual-motor coordination"

    /** Rule 2 thresholds as written in `scoring-rules.md`. */
    private const val STRENGTH_AVERAGE = 3.0
    private const val MILD_FROM = 2.5
    private const val MODERATE_FROM = 1.5

    private const val SCORE_THREE = 3
    private const val SCORE_TWO = 2
    private const val SCORE_ONE = 1

    /** Numeric score of a scored level (Rule 1 table); null for not scored. */
    fun score(level: StepLevel): Int? = when (level) {
        StepLevel.THREE -> SCORE_THREE
        StepLevel.TWO -> SCORE_TWO
        StepLevel.ONE -> SCORE_ONE
        StepLevel.NOT_SCORED -> null
    }

    /** True when [level] is a storable correction: null (not scored) or a Rule 1 score 1..3. */
    fun isValidCorrection(level: Int?): Boolean = level == null || level in SCORE_ONE..SCORE_THREE

    /**
     * A stored therapist correction level as a [StepLevel]: 3/2/1 are the Rule 1 scores, null is "not scored".
     * `scoring-rules.md` defines no score 0, so any other value is rejected.
     */
    fun correctionLevel(level: Int?): StepLevel = when (level) {
        null -> StepLevel.NOT_SCORED
        SCORE_THREE -> StepLevel.THREE
        SCORE_TWO -> StepLevel.TWO
        SCORE_ONE -> StepLevel.ONE
        else -> throw IllegalArgumentException("correction level $level is not a Rule 1 score")
    }

    /**
     * The level reports use: when [hasCorrection], the correction ([correction], null = not scored) wins over the
     * automatic Rule 1 level of [rows]; otherwise Rule 1 (null while the step is still open).
     */
    fun effectiveStepLevel(rows: List<RuleAttempt>, hasCorrection: Boolean, correction: Int?): StepLevel? =
        if (hasCorrection) correctionLevel(correction) else stepLevel(rows)

    /** A step's Rule 2 domains: its tags with Visual-motor coordination folded into Motor coordination, each once. */
    fun domainsOf(tags: List<String>): Set<String> =
        tags.map { if (it == VISUAL_MOTOR_COORDINATION) MOTOR_COORDINATION else it }.toCollection(LinkedHashSet())

    /**
     * Rule 2 averages. [stepLevels] maps step id -> level (null or not scored = excluded); [tags] maps step id -> its
     * "Functional domain" tags (every catalogue step, untagged ones with an empty list). A step counts once in each of its domains; a domain with no scored step has a null average.
     */
    fun domainAverages(stepLevels: Map<String, StepLevel?>, tags: Map<String, List<String>>): DomainAverages {
        val sums = LinkedHashMap<String, Int>()
        val counts = LinkedHashMap<String, Int>()
        tags.values.flatMap { domainsOf(it) }.forEach {
            sums.putIfAbsent(it, 0)
            counts.putIfAbsent(it, 0)
        }
        var untagged = 0
        val unknown = LinkedHashSet<String>()
        for ((stepId, level) in stepLevels) {
            val value = level?.let(::score) ?: continue
            val stepTags = tags[stepId]
            if (stepTags == null) {
                unknown += stepId
                continue
            }
            val domains = domainsOf(stepTags)
            if (domains.isEmpty()) untagged++
            for (domain in domains) {
                sums[domain] = (sums[domain] ?: 0) + value
                counts[domain] = (counts[domain] ?: 0) + 1
            }
        }
        val averages = sums.mapValues { (domain, sum) -> counts.getValue(domain).takeIf { it > 0 }?.let { sum.toDouble() / it } }
        return DomainAverages(averages, counts, untagged, unknown)
    }

    /**
     * `project-details.md` Stage 7: level-3 steps are strengths, level-2/1 steps are needs, each with its Rule 2
     * domains ([domainsOf]; an id missing from [tags] has none). Order follows [stepLevels]; open (null) steps are skipped.
     */
    fun strengthsAndNeeds(stepLevels: Map<String, StepLevel?>, tags: Map<String, List<String>>): StrengthsAndNeeds {
        val strengths = mutableListOf<String>()
        val needs = mutableListOf<Need>()
        val notScored = mutableListOf<String>()
        for ((stepId, level) in stepLevels) {
            when (level) {
                null -> Unit
                StepLevel.THREE -> strengths += stepId
                StepLevel.TWO, StepLevel.ONE -> needs += Need(stepId, level, domainsOf(tags[stepId].orEmpty()))
                StepLevel.NOT_SCORED -> notScored += stepId
            }
        }
        return StrengthsAndNeeds(strengths, needs, notScored)
    }

    /** Rule 2: 3 = strength; 2.5 to <3 = mild; 1.5 to <2.5 = moderate; <1.5 = severe. */
    fun planLevel(average: Double): PlanLevel = when {
        average >= STRENGTH_AVERAGE -> PlanLevel.STRENGTH
        average >= MILD_FROM -> PlanLevel.MILD
        average >= MODERATE_FROM -> PlanLevel.MODERATE
        else -> PlanLevel.SEVERE
    }

    /**
     * Rule 1 on one step's [rows] in saved order. A [correction] wins. Walking the rows: a `none`
     * verifier row -> not scored; the first `done` -> THREE at prompt level 1 with no `not_done` before it
     * (a cannot-tell repeat never lowers a level), else TWO; two consecutive `cannot_tell` rows (the attempt and its
     * repeat, numbered n and n+1) -> not scored; three `not_done` numbers -> ONE.
     * Returns null while the step is still open (more attempts are due).
     */
    fun stepLevel(rows: List<RuleAttempt>, correction: StepLevel? = null): StepLevel? {
        if (correction != null) return correction
        val notDoneNumbers = mutableSetOf<Int>()
        var previous: RuleAttempt? = null
        for (row in rows) {
            if (row.verifierNone) return StepLevel.NOT_SCORED
            when (row.outcome) {
                RuleOutcome.DONE -> return if (notDoneNumbers.isEmpty() && row.promptLevel == 1) StepLevel.THREE else StepLevel.TWO
                RuleOutcome.CANNOT_TELL ->
                    if (previous?.outcome == RuleOutcome.CANNOT_TELL) return StepLevel.NOT_SCORED
                RuleOutcome.NOT_DONE -> {
                    notDoneNumbers += row.number
                    if (notDoneNumbers.size >= NOT_DONE_LIMIT) return StepLevel.ONE
                }
            }
            previous = row
        }
        return null
    }
}
