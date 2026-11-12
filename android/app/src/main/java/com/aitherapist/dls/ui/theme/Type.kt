package com.aitherapist.dls.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aitherapist.dls.R

/** Cairo (SIL OFL 1.1, notice in app/licenses/cairo-OFL.txt), bundled for Arabic and Latin. */
val AppFontFamily: FontFamily =
    FontFamily(
        Font(R.font.cairo_regular, FontWeight.Normal),
        Font(R.font.cairo_semibold, FontWeight.SemiBold),
        Font(R.font.cairo_bold, FontWeight.Bold)
    )

// Therapist type scale from UX DESIGN.md (t-headline, t-title, t-body, t-label, t-data).
val DlsTypography =
    Typography(
        headlineMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    )

/** Child-mode text styles (DESIGN.md child-instruction / child-button / child-count), used from later stories. */
object ChildType {
    val Instruction = TextStyle(fontFamily = AppFontFamily, fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 1.4.em)
    val Button = TextStyle(fontFamily = AppFontFamily, fontSize = 32.sp, fontWeight = FontWeight.Bold)

    // Cairo's heaviest bundled weight is bold (700); DESIGN asks 800, bold is the closest available.
    val Count = TextStyle(fontFamily = AppFontFamily, fontSize = 96.sp, fontWeight = FontWeight.Bold)
}
