package com.aitherapist.dls.ui.child

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aitherapist.dls.BuildConfig
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One saved attempt as the debug strip shows it: ids and codes only, never child data. */
data class DebugVerdict(val stepId: String, val verdict: String, val reason: String, val verifier: String, val seq: Long)

/**
 * Debug-build feed of saved verdicts for the tester (phone test of Story 3.5). Fed from the runner's debug trace line;
 * release builds never publish (the trace lambda is gated on BuildConfig.DEBUG) and never draw the strip.
 */
object DebugVerdictTrace {
    private val latestFlow = MutableStateFlow<DebugVerdict?>(null)
    val latest: StateFlow<DebugVerdict?> = latestFlow.asStateFlow()
    private var seq = 0L

    /** Parses `step=… verdict=… reason=… verifier=…`; replay lines and anything else are ignored. */
    fun publish(line: String) {
        parse(line, ++seq)?.let { latestFlow.value = it }
    }

    internal fun parse(line: String, seq: Long): DebugVerdict? {
        if (!line.startsWith("step=")) return null
        val fields = line.split(' ').mapNotNull { part ->
            part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        val step = fields["step"] ?: return null
        val verdict = fields["verdict"] ?: return null
        return DebugVerdict(step, verdict.lowercase(), fields["reason"].orEmpty(), fields["verifier"].orEmpty(), seq)
    }
}

/** Small corner chip with the last verdict for [SHOW_MS]; draws nothing in release builds and never takes touches. */
@Composable
fun DebugResultStrip(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    val latest by DebugVerdictTrace.latest.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(latest?.seq) {
        if (latest == null) return@LaunchedEffect
        visible = true
        delay(SHOW_MS)
        visible = false
    }
    Box(modifier.fillMaxSize().padding(Spacing.S2), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = visible && latest != null, enter = fadeIn(), exit = fadeOut()) {
            val v = latest ?: return@AnimatedVisibility
            // Neutral ink chip (no red/orange in child mode); no click modifiers, so touches pass through.
            Surface(
                shape = RoundedCornerShape(8),
                color = ChildColors.Ink.copy(alpha = 0.72f),
                contentColor = ChildColors.Surface,
                modifier = Modifier.clearAndSetSemantics {}
            ) {
                Text(
                    "${v.stepId} · ${v.verdict} · ${v.reason} · ${v.verifier}",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1)
                )
            }
        }
    }
}

private const val SHOW_MS = 3_000L
