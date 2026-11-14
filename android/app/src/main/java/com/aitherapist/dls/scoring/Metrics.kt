package com.aitherapist.dls.scoring

/**
 * Goal 8 accuracy metrics (Story 5.8 part B): the Kotlin twin of `tools/eval/compute_metrics.py`, computed from the
 * same `results.csv` rows. Pure and read-only. Blind levels use [RulesEngine.stepLevel] (Rule 1, never copied). Doubt is
 * never disagreement: an attempt with cannot tell on either side is counted apart.
 */
object Metrics {
    const val AGREEMENT_TARGET = 0.80
    const val KAPPA_TARGET = 0.6
    const val CANNOT_TELL_MAX = 0.15

    const val DONE = "done"
    const val NOT_DONE = "not_done"
    const val CANNOT_TELL = "cannot_tell"
    const val NOT_SCORED = "not_scored"
    val VERDICTS = listOf(DONE, NOT_DONE, CANNOT_TELL)

    private val LEVELS = listOf(1, 2, 3)
    private const val FORMULA_STARTS = "=+-@"

    /** One `results.csv` row by column name (formula guard already undone). */
    data class Row(val cells: Map<String, String>) {
        operator fun get(column: String): String = cells[column].orEmpty()

        val correctionOnly: Boolean get() = get("attemptId").isBlank()
    }

    /** The numbers `compute_metrics.py` reports (names match its `as_dict`). */
    data class Result(
        val attempts: Int,
        val appCannotTell: Int,
        val rated: Int,
        val unrated: Int,
        val eitherCannotTell: Int,
        val decided: Int,
        val agree: Int,
        val steps: Int,
        val kappaPairs: List<Pair<Int, Int>>,
        val partialSteps: Int,
        val notScoredSteps: Int,
        val appOpenSteps: Int,
        val blindOpenSteps: Int,
        val correctedSteps: Int,
        val correctedChanged: Int,
        /** stepId -> (blind, app) -> count over rated attempts. */
        val confusion: Map<String, Map<Pair<String, String>, Int>>,
        val clipMatched: Int,
        val clipDecided: Int,
        val clipAgree: Int,
        val clipCannotTell: Int,
        val unmatched: List<String>
    ) {
        val agreement: Double? get() = if (decided > 0) agree.toDouble() / decided else null
        val cannotTellRate: Double? get() = if (attempts > 0) appCannotTell.toDouble() / attempts else null
        val kappa: Double? get() = quadraticKappa(kappaPairs)
        val clipAgreement: Double? get() = if (clipDecided > 0) clipAgree.toDouble() / clipDecided else null

        val agreementMet: Boolean? get() = agreement?.let { it >= AGREEMENT_TARGET }
        val kappaMet: Boolean? get() = kappa?.let { it >= KAPPA_TARGET }
        val cannotTellMet: Boolean? get() = cannotTellRate?.let { it <= CANNOT_TELL_MAX }
    }

    /** Quadratic-weighted Cohen's kappa on levels 1..3; null with no pairs or no expected disagreement. */
    fun quadraticKappa(pairs: List<Pair<Int, Int>>): Double? {
        val n = pairs.size
        if (n == 0) return null
        val k = LEVELS.size
        val rows = pairs.groupingBy { it.first }.eachCount()
        val cols = pairs.groupingBy { it.second }.eachCount()
        fun weight(a: Int, b: Int): Double {
            val d = (LEVELS.indexOf(a) - LEVELS.indexOf(b)).toDouble()
            return d * d / ((k - 1) * (k - 1))
        }
        val observed = pairs.sumOf { (a, b) -> weight(a, b) }
        var expected = 0.0
        for (a in LEVELS) for (b in LEVELS) expected += weight(a, b) * (rows[a] ?: 0) * (cols[b] ?: 0) / n
        return if (expected == 0.0) null else 1 - observed / expected
    }

    /** `level` / `correctedLevel` cell: 1..3, [NOT_SCORED], or null (empty). */
    internal fun parseLevel(value: String): Any? = when (val v = value.trim()) {
        "" -> null
        NOT_SCORED -> NOT_SCORED
        "1", "2", "3" -> v.toInt()
        else -> throw IllegalArgumentException("bad level '$v'")
    }

    private fun outcome(verdict: String): RuleOutcome = when (verdict) {
        DONE -> RuleOutcome.DONE
        NOT_DONE -> RuleOutcome.NOT_DONE
        CANNOT_TELL -> RuleOutcome.CANNOT_TELL
        else -> throw IllegalArgumentException("unknown outcome '$verdict'")
    }

    private fun levelValue(level: StepLevel?): Any? = when (level) {
        StepLevel.THREE -> 3
        StepLevel.TWO -> 2
        StepLevel.ONE -> 1
        StepLevel.NOT_SCORED -> NOT_SCORED
        null -> null
    }

    /** Same rules as `compute_metrics.compute`; [clips] maps a clip file to its scripted outcome (secondary table). */
    fun compute(rows: List<Row>, clips: Map<String, String> = emptyMap()): Result {
        validate(rows)
        var attempts = 0
        var appCannotTell = 0
        var rated = 0
        var unrated = 0
        var either = 0
        var decided = 0
        var agree = 0
        var clipMatched = 0
        var clipDecided = 0
        var clipAgree = 0
        var clipCannotTell = 0
        val unmatched = mutableListOf<String>()
        val confusion = sortedMapOf<String, MutableMap<Pair<String, String>, Int>>()
        val groups = LinkedHashMap<Pair<String, String>, MutableList<Row>>()
        val correctedOnly = LinkedHashSet<Pair<String, String>>()
        for (row in rows) {
            val key = row["sessionId"] to row["stepId"]
            if (row.correctionOnly) {
                correctedOnly += key
                continue
            }
            groups.getOrPut(key) { mutableListOf() } += row
            val app = row["verdict"]
            val blind = row["blindVerdict"]
            attempts++
            if (app == CANNOT_TELL) appCannotTell++
            if (blind.isNotEmpty()) {
                rated++
                val cell = confusion.getOrPut(row["stepId"]) { mutableMapOf() }
                cell[blind to app] = (cell[blind to app] ?: 0) + 1
                if (app == CANNOT_TELL || blind == CANNOT_TELL) {
                    either++
                } else {
                    decided++
                    if (app == blind) agree++
                }
            } else {
                unrated++
            }
            val clip = row["clipFile"].trim()
            if (clip.isNotEmpty()) {
                val scripted = clips[clip]
                if (scripted != null) {
                    clipMatched++
                    if (app == CANNOT_TELL) {
                        clipCannotTell++
                    } else if (scripted == DONE || scripted == NOT_DONE) {
                        clipDecided++
                        if (app == scripted) clipAgree++
                    }
                } else if (clip !in unmatched) {
                    unmatched += clip
                }
            }
        }
        var correctedSteps = 0
        var correctedChanged = 0
        correctedOnly.filter { it !in groups }.forEach { _ ->
            correctedSteps++
            correctedChanged++ // an open step (no automatic level) given a level
        }
        var partial = 0
        var notScored = 0
        var appOpen = 0
        var blindOpen = 0
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (stepRows in groups.values) {
            val appLevel = stepRows.firstOrNull { it["level"].isNotBlank() }?.let { parseLevel(it["level"]) }
            val correctedRow = stepRows.firstOrNull { it["correctedLevel"].isNotBlank() }
            if (correctedRow != null) {
                correctedSteps++
                if (parseLevel(correctedRow["correctedLevel"]) != appLevel) correctedChanged++
            }
            if (stepRows.any { it["blindVerdict"].isEmpty() }) {
                partial++
                continue
            }
            val blindLevel =
                levelValue(
                    RulesEngine.stepLevel(
                        stepRows.map {
                            RuleAttempt(it["attemptNumber"].trim().toInt(), it["promptLevel"].trim().toInt(), outcome(it["blindVerdict"]))
                        }
                    )
                )
            when {
                appLevel == NOT_SCORED || blindLevel == NOT_SCORED -> notScored++
                appLevel == null -> appOpen++
                blindLevel == null -> blindOpen++
                else -> pairs += (appLevel as Int) to (blindLevel as Int)
            }
        }
        return Result(
            attempts, appCannotTell, rated, unrated, either, decided, agree, groups.size, pairs, partial, notScored, appOpen,
            blindOpen, correctedSteps, correctedChanged, confusion, clipMatched, clipDecided, clipAgree, clipCannotTell, unmatched
        )
    }

    /** The input checks of `compute_metrics.read_results`; throws [IllegalArgumentException] on the first bad row. */
    fun validate(rows: List<Row>) {
        val lastNumber = mutableMapOf<Pair<String, String>, Int>()
        rows.forEachIndexed { index, row ->
            val line = index + 2
            if (row.correctionOnly) {
                require(row["level"].isBlank() && row["correctedLevel"].isNotBlank()) {
                    "line $line: correction-only row needs correctedLevel and no level"
                }
                parseLevel(row["correctedLevel"])
                return@forEachIndexed
            }
            require(row["verdict"] in VERDICTS) { "line $line: bad verdict '${row["verdict"]}'" }
            require(row["blindVerdict"].isEmpty() || row["blindVerdict"] in VERDICTS) {
                "line $line: bad blindVerdict '${row["blindVerdict"]}'"
            }
            for (column in listOf("attemptNumber", "promptLevel")) {
                val value = row[column].trim()
                require(
                    value.isNotEmpty() &&
                        value.all {
                            it.isDigit()
                        } &&
                        value.toInt() >= 1
                ) { "line $line: bad $column '${row[column]}'" }
            }
            parseLevel(row["level"])
            parseLevel(row["correctedLevel"])
            val key = row["sessionId"] to row["stepId"]
            val number = row["attemptNumber"].trim().toInt()
            require(number >= (lastNumber[key] ?: 0)) { "line $line: attemptNumber $number decreases within step ${row["stepId"]}" }
            lastNumber[key] = number
        }
    }

    /** Parses `results.csv` text (RFC 4180, optional BOM) into rows, undoing the formula guard. */
    fun parse(text: String): List<Row> {
        val records = records(text.removePrefix(""))
        if (records.isEmpty()) return emptyList()
        val header = records.first()
        return records.drop(1).filter { r -> r.any { it.isNotEmpty() } }.map { r ->
            Row(header.indices.associate { i -> header[i] to unguard(r.getOrElse(i) { "" }) })
        }
    }

    internal fun unguard(value: String): String =
        if (value.length >= 2 && value[0] == '\'' && value[1] in FORMULA_STARTS) value.substring(1) else value

    private fun records(text: String): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var record = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        var pending = false
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    ',' -> {
                        record += field.toString()
                        field.clear()
                    }
                    '\r' -> Unit
                    '\n' -> {
                        record += field.toString()
                        field.clear()
                        out += record
                        record = mutableListOf()
                        pending = false
                        i++
                        continue
                    }
                    else -> field.append(c)
                }
            }
            pending = true
            i++
        }
        if (pending) {
            record += field.toString()
            out += record
        }
        return out
    }
}
