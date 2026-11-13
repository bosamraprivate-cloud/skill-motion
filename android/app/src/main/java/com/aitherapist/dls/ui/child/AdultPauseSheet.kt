package com.aitherapist.dls.ui.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.theme.Spacing

/**
 * Adult pause sheet (UX "Adult pause sheet"), opened by the 2 s hold on the adult corner. Pause while the run is
 * going, Resume while paused, and Exit, which pauses and then asks for the PIN (the session stays resumable).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdultPauseSheet(
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
    cameraUnavailable: Boolean = false
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.S5, vertical = Spacing.S4),
            verticalArrangement = Arrangement.spacedBy(Spacing.S4)
        ) {
            Text(stringResource(R.string.adult_sheet_title), style = MaterialTheme.typography.titleLarge)
            if (cameraUnavailable) {
                // Adult-only: camera steps are being recorded as "cannot tell" until the camera works again.
                Text(
                    stringResource(R.string.adult_sheet_camera_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S4)) {
                if (paused) {
                    Button(onClick = onResume) { Text(stringResource(R.string.adult_sheet_resume)) }
                } else {
                    Button(onClick = onPause) { Text(stringResource(R.string.adult_sheet_pause)) }
                }
                OutlinedButton(onClick = onExit) { Text(stringResource(R.string.adult_sheet_exit)) }
            }
        }
    }
}
