package com.aitherapist.dls.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Light theme only (UX DESIGN.md), no dynamic colour. Every Material role is mapped to a DESIGN token or a tint of
 * `t-primary`, so no default purple/pink container can leak into rows, chips, segmented buttons, dialogs or cards.
 */
private val TherapistColors =
    lightColorScheme(
        primary = TPrimary,
        onPrimary = TOnPrimary,
        primaryContainer = TPrimarySoft,
        onPrimaryContainer = TOnPrimarySoft,
        inversePrimary = TPrimarySoft,
        secondary = TPrimary,
        onSecondary = TOnPrimary,
        secondaryContainer = TPrimarySoft,
        onSecondaryContainer = TOnPrimarySoft,
        tertiary = TPrimary,
        onTertiary = TOnPrimary,
        tertiaryContainer = TPrimarySoft,
        onTertiaryContainer = TOnPrimarySoft,
        background = TCanvas,
        onBackground = TInk,
        surface = TSurface,
        onSurface = TInk,
        surfaceVariant = TSurfaceMuted,
        onSurfaceVariant = TInkSecondary,
        surfaceTint = TPrimary,
        inverseSurface = TInk,
        inverseOnSurface = TSurface,
        outline = TFieldOutline,
        outlineVariant = TBorder,
        error = ErrorColor,
        onError = TOnPrimary,
        errorContainer = TSurfaceMuted,
        onErrorContainer = ErrorColor,
        scrim = TInk,
        surfaceBright = TSurface,
        surfaceDim = TCanvas,
        surfaceContainerLowest = TSurface,
        surfaceContainerLow = TSurface,
        surfaceContainer = TSurface,
        surfaceContainerHigh = TSurface,
        surfaceContainerHighest = TSurfaceMuted
    )

@Composable
fun DlsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TherapistColors, typography = DlsTypography, content = content)
}
