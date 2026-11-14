package com.aitherapist.dls.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Child-mode palette from UX DESIGN.md (fixed light theme, low saturation). */
object ChildColors {
    val Canvas = Color(0xFFF6F4EE)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF2E3A40)
    val InkSoft = Color(0xFF5C6B70)
    val Done = Color(0xFF5E9C8A)
    val DonePressed = Color(0xFF4C8473)
    val Help = Color(0xFF7FA7C9)
    val Repeat = Color(0xFFB9A6D3)
    val Star = Color(0xFFE8C26A)
    val StarEmpty = Color(0xFFE4E0D6)
    val ScheduleCurrent = Color(0xFF5E9C8A)
    val ScheduleDone = Color(0xFFA9CFC2)
    val ScheduleTodo = Color(0xFFE4E0D6)
    val FramingOk = Color(0xFF5E9C8A)
    val FramingWait = Color(0xFFC9C3B6)

    /** Backdrop of the generated step clips and stills (sampled from the frames): step cards use it so no inner rectangle shows. */
    val ClipBackdrop = Color(0xFFE9E6DE)
}

/** Therapist level ramp and plan colours (DESIGN.md); levels never use these in child mode. */
object LevelColors {
    val Level3 = Color(0xFF3E8E6E)
    val Level2 = Color(0xFFD2A03C)
    val Level1 = Color(0xFFC46B4E)
    val NotScored = Color(0xFF9AA5B1)
    val CannotTell = Color(0xFF7B8794)
    val Corrected = Color(0xFF6B5BA8)
    val PlanStrength = Color(0xFF3E8E6E)
    val PlanMild = Color(0xFF8DB86B)
    val PlanModerate = Color(0xFFD2A03C)
    val PlanSevere = Color(0xFFC46B4E)
}

/** 8dp grid spacing and corner radii (DESIGN.md spacing / rounded). */
object Spacing {
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 16.dp
    val S4 = 24.dp
    val S5 = 32.dp
    val S6 = 48.dp
    val S7 = 64.dp
}

object Radius {
    val Sm = 8.dp
    val Md = 16.dp
    val Lg = 28.dp
}

/** Setup-zone colours (therapist mode): calm, distinct from the level ramp; every zone is also labelled by name. */
object ZoneColors {
    val Mat = Color(0xFF5E9C8A)
    val Sink = Color(0xFF4F86B5)
    val Place = Color(0xFF8A73B8)
    val Other = Color(0xFF7B8794)

    fun of(name: String): Color = when (name) {
        "mat" -> Mat
        "sink" -> Sink
        "place" -> Place
        else -> Other
    }
}
