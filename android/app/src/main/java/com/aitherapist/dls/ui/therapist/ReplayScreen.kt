package com.aitherapist.dls.ui.therapist

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.BuildConfig
import com.aitherapist.dls.R
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.replay.Agreement
import com.aitherapist.dls.replay.ClipInput
import com.aitherapist.dls.replay.ClipProblem
import com.aitherapist.dls.replay.ClipResult
import com.aitherapist.dls.replay.ReplayRun
import com.aitherapist.dls.replay.ScriptedOutcome
import com.aitherapist.dls.replay.SkippedClip
import com.aitherapist.dls.ui.components.EmptyState
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.SectionCard
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.LevelColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.ui.theme.TSurfaceMuted
import java.util.Locale

/** Replay & evaluation (Story 3.7): reached only from Settings -> Advanced tools in debug builds. */
@Composable
fun ReplayRoute(viewModel: ReplayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.onEvent(ReplayEvent.Import(uris.map { resolver.clipInput(it) }))
        }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            // SAF cancel gives null: nothing is written.
            if (uri != null) viewModel.onEvent(ReplayEvent.Export { resolver.openOutputStream(uri, "wt") })
        }
    ReplayScreen(
        state,
        onExport = { exporter.launch(EXPORT_FILE_NAME) },
        onImport = { picker.launch(arrayOf("video/*")) },
        onRescore = { viewModel.onEvent(ReplayEvent.Rescore) },
        onMirrored = { viewModel.onEvent(ReplayEvent.SetMirrored(it)) }
    )
}

private const val EXPORT_FILE_NAME = "replay-results.csv"

private fun ContentResolver.clipInput(uri: Uri): ClipInput {
    val name =
        runCatching {
            query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
    return ClipInput(name) { openInputStream(uri) }
}

@Composable
fun ReplayScreen(
    state: ReplayUiState,
    onImport: () -> Unit,
    onRescore: () -> Unit,
    modifier: Modifier = Modifier,
    onMirrored: (Boolean) -> Unit = {},
    onExport: () -> Unit = {}
) {
    val idle = !state.busy && !state.loading
    val hasSession = state.run?.sessionId != null
    Column(modifier = modifier.fillMaxSize().padding(Spacing.S4), verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        PageHeader(stringResource(R.string.replay_title)) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.replay_busy), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onRescore, enabled = idle && hasSession, shape = RoundedCornerShape(Radius.Sm)) {
                Text(stringResource(R.string.replay_rescore))
            }
            if (BuildConfig.DEBUG) {
                OutlinedButton(onClick = onExport, enabled = idle && hasSession, shape = RoundedCornerShape(Radius.Sm)) {
                    Text(stringResource(R.string.replay_export))
                }
            }
            Button(onClick = onImport, enabled = idle, shape = RoundedCornerShape(Radius.Sm)) {
                Text(stringResource(R.string.replay_import))
            }
        }
        Text(
            stringResource(R.string.replay_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.mirrored, onCheckedChange = onMirrored, enabled = !state.busy)
            Text(stringResource(R.string.replay_mirrored), style = MaterialTheme.typography.bodyLarge)
        }
        state.error?.let { Text(stringResource(R.string.replay_error, it), color = MaterialTheme.colorScheme.error) }
        if (state.loading) Skeleton(rows = 4) else RunContent(state.run)
    }
}

@Composable
private fun RunContent(run: ReplayRun?) {
    val sessionId = run?.sessionId
    LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxSize()) {
        if (run != null && run.skipped.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.replay_skipped_title), Modifier.fillMaxWidth().padding(bottom = Spacing.S3)) {
                    run.skipped.forEach { SkippedRow(it) }
                }
            }
        }
        if (sessionId == null) {
            item {
                EmptyState(icon = Icons.Filled.Refresh, message = stringResource(R.string.replay_no_session))
            }
        } else {
            item { SummaryCards(run) }
            item {
                Text(
                    stringResource(R.string.replay_session, sessionId.take(SHORT_ID), run.catalogVersion.orEmpty()) +
                        "  ·  " + stringResource(R.string.replay_reason_no_zone_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.S3)
                )
            }
            stickyHeader { HeaderRow() }
            items(run.results) { ResultRow(it) }
        }
    }
}

@Composable
private fun SummaryCards(run: ReplayRun) {
    val total = run.results.size
    val agree = run.results.count { it.agreement == Agreement.AGREE }
    val cannotTell = run.results.count { it.verdict == AttemptVerdict.CANNOT_TELL }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), modifier = Modifier.fillMaxWidth()) {
        StatCard(stringResource(R.string.replay_stat_clips), total.toString(), null, Modifier.weight(1f))
        StatCard(
            stringResource(R.string.replay_stat_agreement),
            percent(agree, total),
            stringResource(
                R.string.replay_summary,
                agree,
                run.results.count { it.agreement == Agreement.DISAGREE },
                run.results.count { it.agreement == Agreement.NO_VERDICT }
            ),
            Modifier.weight(1f)
        )
        StatCard(stringResource(R.string.replay_stat_cannot_tell), percent(cannotTell, total), null, Modifier.weight(1f))
    }
}

/** Western digits for data (DESIGN.md), whatever the UI locale. */
private fun percent(part: Int, total: Int): String = if (total == 0) "–" else String.format(Locale.ROOT, "%.0f%%", part * PERCENT / total)

private const val PERCENT = 100.0

@Composable
private fun StatCard(label: String, value: String, detail: String?, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(Radius.Md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(Modifier.padding(Spacing.S3), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val SHORT_ID = 8

@Composable
private fun SkippedRow(skipped: SkippedClip) {
    val problem =
        stringResource(
            when (skipped.problem) {
                ClipProblem.MALFORMED_NAME -> R.string.replay_problem_malformed_name
                ClipProblem.BAD_ATTEMPT -> R.string.replay_problem_bad_attempt
                ClipProblem.BAD_OUTCOME -> R.string.replay_problem_bad_outcome
                ClipProblem.UNKNOWN_STEP -> R.string.replay_problem_unknown_step
                ClipProblem.MISSING_FILE -> R.string.replay_problem_missing_file
                ClipProblem.COPY_FAILED -> R.string.replay_problem_copy_failed
                ClipProblem.SAVE_FAILED -> R.string.replay_problem_save_failed
                ClipProblem.DUPLICATE -> R.string.replay_problem_duplicate
                ClipProblem.TOO_LONG -> R.string.replay_problem_too_long
            }
        )
    Text(stringResource(R.string.replay_skipped_item, skipped.fileName, problem), style = MaterialTheme.typography.bodyMedium)
}

private val COLUMN_WEIGHTS = listOf(2.4f, 1f, 1.2f, 1.4f, 1.1f)

@Composable
private fun HeaderRow() {
    val headers =
        listOf(
            R.string.replay_col_step,
            R.string.replay_col_scripted,
            R.string.replay_col_verdict,
            R.string.replay_col_reason,
            R.string.replay_col_agreement
        )
    Surface(color = TSurfaceMuted, shape = RoundedCornerShape(topStart = Radius.Sm, topEnd = Radius.Sm)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.S3, vertical = Spacing.S2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S3)
        ) {
            headers.forEachIndexed { i, h ->
                Text(
                    stringResource(h),
                    modifier = Modifier.weight(COLUMN_WEIGHTS[i]),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultRow(result: ClipResult) {
    val (agreementText, agreementColor) =
        when (result.agreement) {
            Agreement.AGREE -> R.string.replay_agree to LevelColors.Level3
            Agreement.DISAGREE -> R.string.replay_disagree to LevelColors.Level1
            Agreement.NO_VERDICT -> R.string.replay_no_verdict to LevelColors.CannotTell
        }
    val verdictColor =
        when (result.verdict) {
            AttemptVerdict.DONE -> LevelColors.Level3
            AttemptVerdict.NOT_DONE -> LevelColors.Level1
            AttemptVerdict.CANNOT_TELL -> LevelColors.CannotTell
        }
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Spacing.S3, vertical = Spacing.S2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                result.clipFile,
                modifier = Modifier.weight(COLUMN_WEIGHTS[0]),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(result.scripted.token, modifier = Modifier.weight(COLUMN_WEIGHTS[1]), style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(COLUMN_WEIGHTS[2])) { DataChip(result.verdict.name.lowercase(), verdictColor) }
            Text(
                result.reason,
                modifier = Modifier.weight(COLUMN_WEIGHTS[3]),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(Modifier.weight(COLUMN_WEIGHTS[4])) { DataChip(stringResource(agreementText), agreementColor) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Pill chip: tinted fill, coloured dot and the word (colour is never the only signal). */
@Composable
private fun DataChip(text: String, color: Color) {
    Row(
        Modifier.clip(
            RoundedCornerShape(percent = 50)
        ).background(color.copy(alpha = 0.14f)).padding(horizontal = Spacing.S2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S1)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Preview(device = Devices.TABLET)
@Composable
private fun ReplayPreview() {
    DlsTheme {
        ReplayScreen(
            ReplayUiState(
                loading = false,
                run =
                ReplayRun(
                    "1234567890",
                    "cv1",
                    listOf(
                        ClipResult(
                            "step.a__1__done__v1.mp4",
                            "step.a",
                            ScriptedOutcome.DONE,
                            AttemptVerdict.DONE,
                            "check_passed",
                            Agreement.AGREE
                        )
                    ),
                    listOf(SkippedClip("foo.mp4", ClipProblem.MALFORMED_NAME))
                )
            ),
            {},
            {}
        )
    }
}
