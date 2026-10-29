package com.showcase.desertant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showcase.desertant.R

/*
 * Burrow · Hand-drawn Minimal. The tokens mirror design/burrow-a/theme/tokens.json:
 * white paper, ink, one marker-yellow accent, Caveat hand-lettering over a quiet Inter.
 */

/** The theme's own palette, for the hand-drawn components; Material roles are mapped from it. */
@Immutable
data class BurrowColors(
    val paper: Color,
    val ink: Color,
    val pencil: Color,
    val graphite: Color,
    val rule: Color,
    val smudge: Color,
    val marker: Color,
    val markerInk: Color,
    val redPen: Color,
)

private val Light = BurrowColors(
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFF1A1A1A),
    pencil = Color(0xFF6B6B6B),
    graphite = Color(0xFFA3A3A3),
    rule = Color(0x261A1A1A),
    smudge = Color(0xFFF4F3EF),
    marker = Color(0xFFFFE45C),
    markerInk = Color(0xFF1A1A1A),
    redPen = Color(0xFFDC2626),
)

private val Dark = BurrowColors(
    paper = Color(0xFF141414),
    ink = Color(0xFFF2F0EA),
    pencil = Color(0xFFA8A6A0),
    graphite = Color(0xFF6E6C67),
    rule = Color(0x26F2F0EA),
    smudge = Color(0xFF1F1F1E),
    marker = Color(0xFFFFE45C),
    markerInk = Color(0xFF1A1A1A),
    redPen = Color(0xFFF87171),
)

val LocalBurrowColors = staticCompositionLocalOf { Light }

/** Shorthand for the palette inside composables. */
val burrow: BurrowColors @Composable get() = LocalBurrowColors.current

val Caveat = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal),
    Font(R.font.caveat_semibold, FontWeight.SemiBold),
    Font(R.font.caveat_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** Hand-lettered styles that have no Material role. */
object Hand {
    val display = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-0.01).em)
    val headline = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 42.sp)
    val title = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp)
    val door = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp)
    val label = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 24.sp)
    val small = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 21.sp)
}

private val BurrowTypography = Typography(
    displayLarge = Hand.display.copy(fontSize = 64.sp, lineHeight = 64.sp),
    displayMedium = Hand.display,
    displaySmall = Hand.headline,
    headlineLarge = Hand.headline,
    headlineMedium = Hand.title.copy(fontSize = 34.sp, lineHeight = 36.sp),
    headlineSmall = Hand.title,
    titleLarge = Hand.title,
    titleMedium = Hand.door.copy(fontSize = 24.sp, lineHeight = 27.sp),
    titleSmall = Hand.label,
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = Hand.label.copy(fontSize = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

private val BurrowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(50),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun scheme(c: BurrowColors, dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = c.ink,
    onPrimary = c.paper,
    primaryContainer = c.marker,
    onPrimaryContainer = c.markerInk,
    inversePrimary = c.marker,
    secondary = c.ink,
    onSecondary = c.paper,
    secondaryContainer = c.smudge,
    onSecondaryContainer = c.ink,
    tertiary = c.pencil,
    onTertiary = c.paper,
    tertiaryContainer = c.smudge,
    onTertiaryContainer = c.ink,
    background = c.paper,
    onBackground = c.ink,
    surface = c.paper,
    onSurface = c.ink,
    surfaceVariant = c.smudge,
    onSurfaceVariant = c.pencil,
    surfaceTint = Color.Transparent,
    surfaceBright = c.paper,
    surfaceDim = c.smudge,
    surfaceContainerLowest = c.paper,
    surfaceContainerLow = c.paper,
    surfaceContainer = c.paper,
    surfaceContainerHigh = c.smudge,
    surfaceContainerHighest = c.smudge,
    outline = c.ink.copy(alpha = 0.55f),
    outlineVariant = c.rule,
    error = c.redPen,
    onError = c.paper,
    errorContainer = c.redPen.copy(alpha = 0.12f),
    onErrorContainer = c.redPen,
    scrim = Color.Black.copy(alpha = 0.32f),
)

@Composable
fun BurrowTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) Dark else Light
    CompositionLocalProvider(LocalBurrowColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors, dark), typography = BurrowTypography, shapes = BurrowShapes, content = content)
    }
}

/**
 * Speakers and labels are told apart by ink texture, not by color; where a hue
 * is still needed (a chart, a legend), these are muted, pencil-box tones.
 */
val SeriesColors = listOf(
    Color(0xFF1A1A1A), Color(0xFF8A6D1F), Color(0xFF3E6B5A), Color(0xFF7A4A6B),
    Color(0xFF4A5E85), Color(0xFF9A4B3C), Color(0xFF5F6B2E), Color(0xFF6B6B6B),
)

fun seriesColor(index: Int): Color = SeriesColors[((index % SeriesColors.size) + SeriesColors.size) % SeriesColors.size]
