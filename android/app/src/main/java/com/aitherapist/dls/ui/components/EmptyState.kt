package com.aitherapist.dls.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.ui.theme.DlsTheme

/** Shared empty state (AD-13): icon, message and one primary action. Therapist mode only. */
@Composable
fun EmptyState(icon: ImageVector, message: String, primaryActionLabel: String, onPrimaryAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onPrimaryAction) { Text(primaryActionLabel) }
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    DlsTheme { EmptyState(Icons.Filled.Info, "Nothing here yet", "Reload", {}) }
}
