package com.aitherapist.dls.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Light theme only (UX DESIGN.md).
private val TherapistColors =
    lightColorScheme(
        primary = TPrimary,
        onPrimary = TOnPrimary,
        background = TCanvas,
        onBackground = TInk,
        surface = TSurface,
        onSurface = TInk,
        surfaceVariant = TBorder,
        onSurfaceVariant = TInkSecondary,
        outline = TBorder,
        error = ErrorColor
    )

@Composable
fun DlsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TherapistColors, typography = DlsTypography, content = content)
}
