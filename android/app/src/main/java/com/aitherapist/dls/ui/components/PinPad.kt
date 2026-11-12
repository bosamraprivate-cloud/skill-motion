package com.aitherapist.dls.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.theme.DlsTheme

private val KEY_ROWS = listOf("123", "456", "789")
private val KEY_SIZE = 64.dp

/** Filled dots for the digits entered so far; the digits themselves are never shown. */
@Composable
fun PinDots(filled: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(total) { i ->
            val dot = Modifier.size(16.dp)
            Box(
                if (i < filled) {
                    dot.background(MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    dot.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                }
            )
        }
    }
}

/** Numeric keypad (phone layout, always left-to-right) for PIN entry. */
@Composable
fun PinPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KEY_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { d -> DigitKey(d, onDigit, enabled) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val deleteLabel = stringResource(R.string.pin_delete)
                TextButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier.size(KEY_SIZE).semantics { contentDescription = deleteLabel }
                ) { Text("⌫", style = MaterialTheme.typography.titleLarge) }
                DigitKey('0', onDigit, enabled)
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, onDigit: (Char) -> Unit, enabled: Boolean) {
    FilledTonalButton(onClick = { onDigit(digit) }, enabled = enabled, modifier = Modifier.size(KEY_SIZE), shape = CircleShape) {
        Text(digit.toString(), style = MaterialTheme.typography.titleLarge)
    }
}

@Preview
@Composable
private fun PinPadPreview() {
    DlsTheme {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PinDots(filled = 2, total = 6)
            PinPad(onDigit = {}, onDelete = {})
        }
    }
}
