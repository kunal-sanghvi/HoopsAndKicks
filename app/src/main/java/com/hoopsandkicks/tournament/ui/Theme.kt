package com.hoopsandkicks.tournament.ui

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface

val Bg = Color(0xFFF6F3EE)
val Surface = Color(0xFFFFFFFF)
val Ink = Color(0xFF1B1A17)
val Mute = Color(0xFF6B665C)
val Line = Color(0xFFE3DDD2)
val Accent = Color(0xFFC8431B)
val AccentSoft = Color(0xFFFBE6DD)
val Green = Color(0xFF1F7A4D)
val GreenSoft = Color(0xFFDDF0E5)
val Gold = Color(0xFFC79A00)
/** Informational "planned time / behind plan" note on Match ready (MatchReady design). Distinct from Accent. */
val PlanAmber = Color(0xFFFBEBD3)
val PlanAmberInk = Color(0xFFB4780A)

val Navy = Color(0xFF14171F)
val Navy2 = Color(0xFF1F2431)
val Navy3 = Color(0xFF2B3142)
val OnDark = Color(0xFFF5F2EA)
val MuteDark = Color(0xFFA7ACBA)
val AccentDark = Color(0xFFFF7A45)

val TeamPalette = listOf(
    Color(0xFFD9481C), Color(0xFF2F6FDE), Color(0xFF1F8A5B), Color(0xFF6B4FC4),
    Color(0xFFC79A00), Color(0xFF0E8FA0), Color(0xFFC2367A), Color(0xFF4A5568)
)

fun teamColor(index: Int): Color = TeamPalette[((index % TeamPalette.size) + TeamPalette.size) % TeamPalette.size]

/** Condensed system font for scores and headings (swap in Barlow Condensed via res/font if you like). */
val DisplayFont: FontFamily = FontFamily(Typeface(AndroidTypeface.create("sans-serif-condensed", AndroidTypeface.BOLD)))

@Composable
fun HoopsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Accent, background = Bg, surface = Surface, onSurface = Ink),
        content = content
    )
}
