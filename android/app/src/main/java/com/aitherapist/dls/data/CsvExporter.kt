package com.aitherapist.dls.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.scoring.StepLevel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One session as the results export reads it (Story 5.6): only the child's code, never the name. [blind] maps an
 * attempt id to its blind rating.
 */
data class ExportSession(
    val session: SessionEntity,
    val childCode: String,
    val attempts: List<AttemptEntity>,
    val corrections: List<CorrectionEntity>,
    val blind: Map<String, AttemptVerdict>,
    /** The session's child is the hidden eval child. */
    val evalChild: Boolean = false
)

/** Which accuracy population a session belongs to (Story 5.8). */
enum class Population { VOLUNTEER, LIVE, OTHER }

/** Volunteer = a replay session of the eval child; live = an assessment of a visible child; anything else is excluded. */
fun population(kind: SessionKind, evalChild: Boolean): Population = when {
    kind == SessionKind.REPLAY && evalChild -> Population.VOLUNTEER
    kind == SessionKind.ASSESSMENT && !evalChild -> Population.LIVE
    else -> Population.OTHER
}

/** Sessions without a child code; the export and the dashboard both refuse them and name the count. */
class MissingChildCodeException(val count: Int) : IllegalArgumentException("$count sessions have no child code")

/** Export rows of one population for the dashboard; [excluded] counts sessions in neither population. */
data class MetricsRows(val rows: List<Map<String, String>>, val excluded: Int)

/**
 * Anonymised `results.csv` (Story 5.6), the input of `tools/eval/compute_metrics.py`. Pure: RFC 4180 quoting, LF line
 * ends, no BOM. Never names, frames or the model's free text (`modelReason`); levels are computed by [RulesEngine].
 */
object CsvExporter {
    val COLUMNS =
        listOf(
            "sessionId",
            "childCode",
            "taskId",
            "stepId",
            "attemptId",
            "attemptNumber",
            "promptLevel",
            "verdict",
            "reason",
            "verifier",
            "modelId",
            "latencyMs",
            "level",
            "correctedLevel",
            "blindVerdict",
            "clipFile"
        )

    const val NOT_SCORED = "not_scored"

    /** Sessions with a blank child code: the export and the dashboard refuse them all (never dropped silently). */
    fun brokenSessions(sessions: List<ExportSession>): Int = sessions.count { it.childCode.isBlank() }

    /** Throws [MissingChildCodeException] naming how many sessions have no child code. */
    fun requireChildCodes(sessions: List<ExportSession>) {
        val broken = brokenSessions(sessions)
        if (broken > 0) throw MissingChildCodeException(broken)
    }

    /**
     * The export rows (one list of [COLUMNS] cells per row), shared by the file and the metrics dashboard. Sessions by
     * startedAt (stable for ties), steps in [stepOrder] of the task (steps not in the catalogue after, in saved order),
     * attempts in saved order (the order Rule 1 reads, so attemptNumber never decreases within a step). A step with a
     * correction but no attempts gets one row with empty attemptId, attemptNumber, promptLevel, verdict, reason,
     * verifier, modelId, latencyMs, level, blindVerdict and clipFile; only correctedLevel is set. Any blank childCode
     * throws [MissingChildCodeException]: the export never writes an unidentified row.
     */
    fun rows(sessions: List<ExportSession>, stepOrder: (taskId: String) -> List<String>): Sequence<List<String>> {
        requireChildCodes(sessions)
        return sequence {
            sessions.sortedBy { it.session.startedAt }.forEach { export ->
                val order = stepOrder(export.session.taskId)
                val byStep = export.attempts.groupBy { it.stepId }
                val corrections =
                    export.corrections.filter { RulesEngine.isValidCorrection(it.level) }.groupBy { it.stepId }
                        .mapValues { (_, rows) -> latestCorrection(rows) }
                val steps = (byStep.keys + corrections.keys).sortedBy { id -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
                steps.forEach { stepId ->
                    val saved = byStep[stepId].orEmpty()
                    val corrected = corrections[stepId]?.let { levelText(RulesEngine.correctionLevel(it.level)) }.orEmpty()
                    val head = listOf(export.session.id, export.childCode, export.session.taskId, stepId)
                    if (saved.isEmpty()) {
                        yield(head + List(COLUMNS.size - 7) { "" } + listOf(corrected, "", ""))
                    } else {
                        val level = levelText(RulesEngine.stepLevel(saved.map { it.toRuleAttempt() }))
                        saved.forEach { a ->
                            yield(
                                head +
                                    listOf(
                                        a.id,
                                        a.number.toString(),
                                        a.promptLevel.toString(),
                                        a.verdict.storageName(),
                                        a.reason,
                                        a.verifier.storageName(),
                                        a.modelId.orEmpty(),
                                        a.latencyMs.toString(),
                                        level,
                                        corrected,
                                        export.blind[a.id]?.storageName().orEmpty(),
                                        a.clipFile.orEmpty()
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    /** Streams the whole file to [out] ([rows] plus the header). */
    fun write(sessions: List<ExportSession>, stepOrder: (taskId: String) -> List<String>, out: Appendable) {
        val rows = rows(sessions, stepOrder)
        out.append(line(COLUMNS))
        rows.forEach { out.append(line(it)) }
    }

    /** The whole file as text (tests). */
    fun format(sessions: List<ExportSession>, stepOrder: (taskId: String) -> List<String>): String =
        StringBuilder().also { write(sessions, stepOrder, it) }.toString()

    /** `3`/`2`/`1`/`not_scored`, or empty while the step is open. */
    fun levelText(level: StepLevel?): String = when (level) {
        StepLevel.THREE -> "3"
        StepLevel.TWO -> "2"
        StepLevel.ONE -> "1"
        StepLevel.NOT_SCORED -> NOT_SCORED
        null -> ""
    }

    /** RFC 4180: a field with a comma, quote, CR or LF is quoted and its quotes doubled. */
    fun quote(field: String): String {
        val safe = if (field.isNotEmpty() && field[0] in FORMULA_STARTS) "'$field" else field
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** Formula guard: a cell starting with one of these gets a leading `'` (`compute_metrics.py` strips it). */
    private const val FORMULA_STARTS = "=+-@"

    private fun line(fields: List<String>): String = fields.joinToString(",", transform = ::quote) + "\n"
}

/** Writes `results.csv` to a document the therapist picked (SAF). Read-only on data. Faked in unit tests. */
interface ResultsExport {
    /** Writes the export to [uri] (a SAF document URI string); throws [IOException] when it cannot be written. */
    suspend fun exportTo(uri: String)
}

/** The same rows the export writes, read in memory for the metrics dashboard (Story 5.8). Faked in unit tests. */
interface ResultsRead {
    suspend fun metricsRows(volunteer: Boolean): MetricsRows
}

@Singleton
class ResultsExportRepository
@Inject
internal constructor(
    private val holder: DatabaseHolder,
    private val catalog: CatalogRepository,
    @ApplicationContext private val context: Context
) : ResultsExport,
    ResultsRead {
    /** Everything the export needs, read-only, in one read transaction. */
    internal suspend fun sessions(): List<ExportSession> {
        val db = holder.get()
        val children = mutableMapOf<String, ChildEntity?>()
        return db.withTransaction {
            db.sessionDao().exportable().map { session ->
                val child = children.getOrPut(session.childId) { db.childDao().byId(session.childId) }
                ExportSession(
                    session = session,
                    childCode = child?.code.orEmpty(),
                    evalChild = child?.isEval == true,
                    attempts = db.attemptDao().forSession(session.id),
                    corrections = db.correctionDao().forSession(session.id),
                    blind = db.blindRatingDao().forSession(session.id).associate { it.attemptId to it.verdict }
                )
            }
        }
    }

    /** The export rows in memory (no file) for the metrics dashboard, one population only. */
    override suspend fun metricsRows(volunteer: Boolean): MetricsRows {
        val tasks = catalog.load().getOrThrow().tasks.associate { task -> task.id to task.steps.map { it.id } }
        val all = sessions()
        CsvExporter.requireChildCodes(all)
        val wanted = if (volunteer) Population.VOLUNTEER else Population.LIVE
        val groups = all.groupBy { population(it.session.kind, it.evalChild) }
        val rows = CsvExporter.rows(groups[wanted].orEmpty()) { tasks[it].orEmpty() }.map { CsvExporter.COLUMNS.zip(it).toMap() }.toList()
        return MetricsRows(rows, groups[Population.OTHER].orEmpty().size)
    }

    /** On any failure (catalogue, missing child code, IO, out of memory) the created document is deleted and the error rethrown. */
    override suspend fun exportTo(uri: String) {
        val target = Uri.parse(uri)
        try {
            val tasks = catalog.load().getOrThrow().tasks.associate { task -> task.id to task.steps.map { it.id } }
            val sessions = sessions()
            CsvExporter.requireChildCodes(sessions)
            val stream = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("no output stream")
            stream.bufferedWriter(Charsets.UTF_8).use { writer -> CsvExporter.write(sessions, { tasks[it].orEmpty() }, writer) }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Throwable
        ) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
            throw e
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ResultsExportModule {
    @Binds
    abstract fun resultsExport(impl: ResultsExportRepository): ResultsExport

    @Binds
    abstract fun resultsRead(impl: ResultsExportRepository): ResultsRead
}
