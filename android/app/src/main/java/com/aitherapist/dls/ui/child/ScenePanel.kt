package com.aitherapist.dls.ui.child

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.data.Zone as StoredZone
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Radius
import com.aitherapist.dls.ui.theme.Spacing
import com.aitherapist.dls.ui.therapist.ZoneMath
import com.aitherapist.dls.vision.SceneIssue
import com.aitherapist.dls.vision.SceneIssueCode
import com.aitherapist.dls.vision.SceneObject
import com.aitherapist.dls.vision.ScenePlan

/** Scene-phase buttons (Story 6.4). */
class SceneActions(val onCheckAgain: () -> Unit, val onSkip: () -> Unit)

/** The AI scene check panel: status, one message per issue, Check again and Start anyway. */
@Composable
internal fun ScenePanel(scene: SceneUi, cameraGranted: Boolean, actions: SceneActions, modifier: Modifier, fillHeight: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S3)) {
        Text(
            stringResource(R.string.scene_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(
                R.string.scene_subtitle,
                scene.zoneKinds.map {
                    sceneKindLabel(it)
                }.joinToString(stringResource(R.string.scene_separator))
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (cameraGranted) {
            when (scene.status) {
                SceneStatus.CAPTURING, SceneStatus.CHECKING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S2),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(
                        stringResource(if (scene.status == SceneStatus.CAPTURING) R.string.scene_capturing else R.string.scene_checking),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                SceneStatus.ISSUES -> Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.S2),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                ) { scene.issues.forEach { IssueLine(sceneIssueText(it)) } }
                SceneStatus.UNAVAILABLE -> IssueLine(stringResource(R.string.scene_unavailable))
                else -> Unit
            }
        }
        if (fillHeight) Spacer(Modifier.weight(1f))
        val retry = scene.status == SceneStatus.ISSUES || scene.status == SceneStatus.UNAVAILABLE
        if (retry) {
            Button(
                onClick = actions.onCheckAgain,
                shape = RoundedCornerShape(Radius.Md),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(
                    stringResource(R.string.scene_check_again),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Spacing.S2)
                )
            }
        }
        // Doubt is never failure: the therapist can always go on; zone steps then end as cannot tell.
        if (retry || !cameraGranted) {
            TextButton(onClick = actions.onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.scene_start_anyway), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IssueLine(text: String) {
    Surface(
        shape = RoundedCornerShape(Radius.Md),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(Spacing.S3),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The AI-found boxes over the mirrored preview, each labelled with its kind. */
@Composable
internal fun SceneBoxes(objects: List<SceneObject>, modifier: Modifier) {
    if (objects.isEmpty()) return
    val rects = objects.mapNotNull { o ->
        o.box.toZone()?.let { z ->
            o.kind to
                ZoneMath.toDisplay(StoredZone(z.left, z.top, z.right, z.bottom))
        }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        Canvas(Modifier.matchParentSize()) {
            rects.forEach { (kind, r) ->
                val color = boxColor(kind)
                val topLeft = Offset(r.left * size.width, r.top * size.height)
                val s = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height)
                drawRect(color.copy(alpha = 0.18f), topLeft, s)
                drawRect(color, topLeft, s, style = Stroke(width = 3.dp.toPx()))
            }
        }
        rects.forEach { (kind, r) ->
            val x = with(density) { (r.left * constraints.maxWidth).toDp() }
            val y = with(density) { (r.top * constraints.maxHeight).toDp() }
            Surface(color = boxColor(kind), shape = RoundedCornerShape(Radius.Sm), modifier = Modifier.offset(x, y).padding(Spacing.S1)) {
                Row(Modifier.padding(horizontal = Spacing.S2, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(
                        sceneKindLabel(kind),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

private fun boxColor(kind: String): Color = if (kind == ScenePlan.PERSON) ChildColors.FramingWait else ChildColors.FramingOk

/** Arabic (or English) name of a catalogue zone kind; an unknown kind falls back to a generic name. */
@Composable
internal fun sceneKindLabel(kind: String?): String = stringResource(sceneKindRes(kind))

internal fun sceneKindRes(kind: String?): Int = when (kind) {
    "mat" -> R.string.scene_kind_mat
    "sink" -> R.string.scene_kind_sink
    "place" -> R.string.scene_kind_place
    ScenePlan.PERSON -> R.string.scene_kind_person
    else -> R.string.scene_kind_unknown
}

/** One message per issue code (task-aware through the kind name). */
internal fun sceneIssueRes(code: SceneIssueCode): Int = when (code) {
    SceneIssueCode.MISSING_OBJECT -> R.string.scene_issue_missing_object
    SceneIssueCode.PARTLY_VISIBLE -> R.string.scene_issue_partly_visible
    SceneIssueCode.TOO_DARK -> R.string.scene_issue_too_dark
    SceneIssueCode.PERSON_IN_VIEW -> R.string.scene_issue_person_in_view
    SceneIssueCode.CAMERA_TILTED -> R.string.scene_issue_camera_tilted
    SceneIssueCode.TOO_FAR -> R.string.scene_issue_too_far
    SceneIssueCode.TOO_CLOSE -> R.string.scene_issue_too_close
    SceneIssueCode.UNCLEAR -> R.string.scene_issue_unclear
}

@Composable
private fun sceneIssueText(issue: SceneIssue): String = stringResource(sceneIssueRes(issue.code), sceneKindLabel(issue.kind))
