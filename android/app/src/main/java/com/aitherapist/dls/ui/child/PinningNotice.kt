package com.aitherapist.dls.ui.child

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R

/** Remembers on the tablet (no child data) that the adult has read the screen pinning explanation once. */
object PinningIntro {
    private const val PREFS = "dls_ui"
    private const val KEY = "pinning_explained"

    fun seen(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

/**
 * Adult-only, first hand-off only: what screen pinning does, that Android will show its own "App is pinned" message,
 * and how the adult leaves (adult corner + PIN). [onContinue] goes on to the hand-off.
 */
@Composable
fun PinningExplainerDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.pinning_intro_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pinning_intro_what), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.pinning_intro_system), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.pinning_intro_exit), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = { Button(onClick = onContinue) { Text(stringResource(R.string.pinning_intro_ok)) } }
    )
}

/**
 * Small, non-blocking hint on the therapist screen after a visit that ran without screen pinning. Never shown in child
 * mode; the adult dismisses it with the close icon.
 */
@Composable
fun UnpinnedHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(16.dp).widthIn(max = 560.dp).semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.pinning_unpinned_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pinning_hint_dismiss))
            }
        }
    }
}
