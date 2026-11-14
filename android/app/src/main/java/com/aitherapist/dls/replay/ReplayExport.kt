package com.aitherapist.dls.replay

import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.SessionEntity

/**
 * Replay results as CSV for `tools/eval/accuracy_report.py` (Story 3.9, debug builds only). One row per attempt with a
 * clip file. Holds no child data and never the model's free text (`modelReason`).
 */
object ReplayExport {
    val COLUMNS =
        listOf(
            "clip_file",
            "step",
            "scripted_outcome",
            "verdict",
            "reason",
            "verifier",
            "model_id",
            "prompt_version",
            "latency_ms",
            "catalog_version",
            "rules_version"
        )

    fun csv(session: SessionEntity, attempts: List<AttemptEntity>): String = buildString {
        append(COLUMNS.joinToString(",")).append('\n')
        for (row in attempts) {
            val clipFile = row.clipFile ?: continue
            val scripted = (ClipName.parse(clipFile) as? ClipName.Parsed.Ok)?.clip?.outcome?.token.orEmpty()
            val cells =
                listOf(
                    clipFile,
                    row.stepId,
                    scripted,
                    row.verdict.name.lowercase(),
                    row.reason,
                    row.verifier.name.lowercase(),
                    row.modelId.orEmpty(),
                    row.promptVersion.orEmpty(),
                    row.latencyMs.toString(),
                    session.catalogVersion,
                    session.rulesVersion
                )
            append(cells.joinToString(",") { escape(it) }).append('\n')
        }
    }

    private fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
}
