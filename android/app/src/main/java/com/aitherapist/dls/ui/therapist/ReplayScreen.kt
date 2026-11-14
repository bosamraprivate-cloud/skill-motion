package com.aitherapist.dls.ui.therapist

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.R
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.replay.Agreement
import com.aitherapist.dls.replay.ClipInput
import com.aitherapist.dls.replay.ClipProblem
import com.aitherapist.dls.replay.ClipResult
import com.aitherapist.dls.replay.ReplayRun
import com.aitherapist.dls.replay.ScriptedOutcome
import com.aitherapist.dls.replay.SkippedClip
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.Skeleton
import com.aitherapist.dls.ui.theme.DlsTheme

/** Replay & evaluation (Story 3.7): reached only from Settings -> Advanced tools in debug builds. */
@Composable
fun ReplayRoute(viewModel: ReplayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.onEvent(ReplayEvent.Import(uris.map { resolver.clipInput(it) }))
        }
    ReplayScreen(
        state,
        onImport = { picker.launch(arrayOf("video/*")) },
        onRescore = { viewModel.onEvent(ReplayEvent.Rescore) },
        onMirrored = { viewModel.onEvent(ReplayEvent.SetMirrored(it)) }
    )
}

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
    onMirrored: (Boolean) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(stringResource(R.string.replay_title))
        Text(stringResource(R.string.replay_body), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.replay_reason_no_zone_hint), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.mirrored, onCheckedChange = onMirrored, enabled = !state.busy)
            Text(stringResource(R.string.replay_mirrored), style = MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val idle = !state.busy && !state.loading
            Button(onClick = onImport, enabled = idle) { Text(stringResource(R.string.replay_import)) }
            OutlinedButton(onClick = onRescore, enabled = idle && state.run?.sessionId != null) {
                Text(stringResource(R.string.replay_rescore))
            }
            if (state.busy) {
                CircularProgressIndicator()
                Text(stringResource(R.string.replay_busy))
            }
        }
        state.error?.let { Text(stringResource(R.string.replay_error, it), color = MaterialTheme.colorScheme.error) }
        if (state.loading) Skeleton(rows = 4) else RunContent(state.run)
    }
}

@Composable
private fun RunContent(run: ReplayRun?) {
    val headers =
        listOf(
            stringResource(R.string.replay_col_step),
            stringResource(R.string.replay_col_scripted),
            stringResource(R.string.replay_col_verdict),
            stringResource(R.string.replay_col_reason),
            stringResource(R.string.replay_col_agreement)
        )
    val empty = stringResource(R.string.replay_no_session)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (run != null && run.skipped.isNotEmpty()) {
            item { Text(stringResource(R.string.replay_skipped_title), style = MaterialTheme.typography.titleMedium) }
            items(run.skipped) { SkippedRow(it) }
            item { HorizontalDivider() }
        }
        val sessionId = run?.sessionId
        if (sessionId == null) {
            item { Text(empty, style = MaterialTheme.typography.bodyLarge) }
        } else {
            item {
                Text(stringResource(R.string.replay_session, sessionId.take(SHORT_ID), run.catalogVersion.orEmpty()))
                Text(
                    stringResource(
                        R.string.replay_summary,
                        run.results.count { it.agreement == Agreement.AGREE },
                        run.results.count { it.agreement == Agreement.DISAGREE },
                        run.results.count { it.agreement == Agreement.NO_VERDICT }
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item { ResultLine(headers, header = true) }
            items(run.results) { ResultRow(it) }
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
    Text(stringResource(R.string.replay_skipped_item, skipped.fileName, problem), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ResultRow(result: ClipResult) {
    val agreement =
        when (result.agreement) {
            Agreement.AGREE -> R.string.replay_agree
            Agreement.DISAGREE -> R.string.replay_disagree
            Agreement.NO_VERDICT -> R.string.replay_no_verdict
        }
    ResultLine(
        listOf(result.clipFile, result.scripted.token, result.verdict.name.lowercase(), result.reason, stringResource(agreement)),
        color = if (result.agreement == Agreement.DISAGREE) MaterialTheme.colorScheme.error else null
    )
}

@Composable
private fun ResultLine(cells: List<String>, header: Boolean = false, color: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                modifier = Modifier.weight(if (i == 0) 2f else 1f),
                fontWeight = if (header) FontWeight.SemiBold else null,
                color = color ?: MaterialTheme.colorScheme.onSurface
            )
        }
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
