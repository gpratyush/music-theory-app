package com.gpratyush.musictheory.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Same indigo accent as the iOS app. */
private val Indigo = Color(0xFF5856D6)
private val IndigoDark = Color(0xFF7E7AFF)

val CorrectGreen = Color(0xFF34C759)
val WrongRed = Color(0xFFFF3B30)

@Composable
fun MusicTheoryTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = IndigoDark, secondary = IndigoDark, tertiary = Color(0xFFBF7AF0))
    } else {
        lightColorScheme(primary = Indigo, secondary = Indigo, tertiary = Color(0xFFAF52DE))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
