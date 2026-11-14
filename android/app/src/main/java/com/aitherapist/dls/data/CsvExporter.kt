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
    val blind: Map<String, AttemptVerdict>
)

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

    /**
     * Streams the whole file to [out]. Sessions by startedAt (stable for ties), steps in [stepOrder] of the task (steps
     * not in the catalogue after, in saved order), attempts in saved order (the order Rule 1 reads, so attemptNumber
     * never decreases within a step). A step with a correction but no attempts gets one row with empty attemptId,
     * attemptNumber, promptLevel, verdict, reason, verifier, modelId, latencyMs, level, blindVerdict and clipFile; only
     * correctedLevel is set. A blank childCode throws: the export never writes an unidentified row.
     */
    fun write(sessions: List<ExportSession>, stepOrder: (taskId: String) -> List<String>, out: Appendable) {
        out.append(line(COLUMNS))
        sessions.sortedBy { it.session.startedAt }.forEach { export ->
            require(export.childCode.isNotBlank()) { "missing child code" }
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
                    out.append(line(head + List(COLUMNS.size - 7) { "" } + listOf(corrected, "", "")))
                    return@forEach
                }
                val level = levelText(RulesEngine.stepLevel(saved.map { it.toRuleAttempt() }))
                saved.forEach { a ->
                    out.append(
                        line(
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
                    )
                }
            }
        }
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

@Singleton
class ResultsExportRepository
@Inject
internal constructor(
    private val holder: DatabaseHolder,
    private val catalog: CatalogRepository,
    @ApplicationContext private val context: Context
) : ResultsExport {
    /** Everything the export needs, read-only, in one read transaction. */
    internal suspend fun sessions(): List<ExportSession> {
        val db = holder.get()
        val codes = mutableMapOf<String, String>()
        return db.withTransaction {
            db.sessionDao().exportable().map { session ->
                ExportSession(
                    session = session,
                    childCode = codes.getOrPut(session.childId) { db.childDao().byId(session.childId)?.code.orEmpty() },
                    attempts = db.attemptDao().forSession(session.id),
                    corrections = db.correctionDao().forSession(session.id),
                    blind = db.blindRatingDao().forSession(session.id).associate { it.attemptId to it.verdict }
                )
            }
        }
    }

    /** On any failure (catalogue, missing child code, IO, out of memory) the created document is deleted and the error rethrown. */
    override suspend fun exportTo(uri: String) {
        val target = Uri.parse(uri)
        try {
            val tasks = catalog.load().getOrThrow().tasks.associate { task -> task.id to task.steps.map { it.id } }
            val sessions = sessions()
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
}
