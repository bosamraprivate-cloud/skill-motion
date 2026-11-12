package com.aitherapist.dls.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Cairo placeholder: the system font is used until the Cairo font file is bundled. */
val AppFontFamily: FontFamily = FontFamily.Default

// Therapist type scale from UX DESIGN.md (t-headline, t-title, t-body, t-label).
val DlsTypography =
    Typography(
        headlineMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    )
