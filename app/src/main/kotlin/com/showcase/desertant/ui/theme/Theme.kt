package com.showcase.desertant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Sand = Color(0xFFB8742A)
private val Dune = Color(0xFF7A4A1E)
private val Ink = Color(0xFF3B2414)

private val LightColors = lightColorScheme(
    primary = Sand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBC),
    onPrimaryContainer = Ink,
    secondary = Dune,
    tertiary = Color(0xFF4F6B3A),
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFBF7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB86E),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6A3C0A),
    onPrimaryContainer = Color(0xFFFFDCBC),
    secondary = Color(0xFFE3BF9F),
    tertiary = Color(0xFFB4D29A),
)

@Composable
fun ShowcaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Distinct, theme-independent colors for speakers, PII labels and the like. */
val SeriesColors = listOf(
    Color(0xFF2F7DD1), Color(0xFFD1612F), Color(0xFF2FA36B), Color(0xFF9B4FD1),
    Color(0xFFC9A227), Color(0xFFD13F7A), Color(0xFF2AA9B8), Color(0xFF7A8C2F),
)

fun seriesColor(index: Int): Color = SeriesColors[((index % SeriesColors.size) + SeriesColors.size) % SeriesColors.size]
