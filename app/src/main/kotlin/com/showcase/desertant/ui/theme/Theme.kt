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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showcase.desertant.R

/*
 * Burrow · Hand-drawn Minimal. White paper, ink, one marker-yellow accent, and
 * two ways of lettering it, chosen in settings ([TypeStyle]):
 *
 *  - handwritten (the default): the look Burrow had up to 3.9. Caveat for
 *    headings, buttons and also for the names and titles of things (row titles,
 *    content titles, tags), over a handwritten reading face.
 *  - formal: the 4.0 mapping. Caveat only for the app's own short words
 *    (titles, heads, buttons, nav); everything you read is a plain sans.
 *
 * Structure, spacing, sizes and colours (tokens 2.0.0) are the same in both.
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

/** Light: ink 17.4:1, pencil 7.5:1, graphite (placeholders and disabled only) 5.3:1, red pen 5.7:1 on paper. */
private val Light = BurrowColors(
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFF1A1A1A),
    pencil = Color(0xFF555555),
    graphite = Color(0xFF6B6B6B),
    rule = Color(0x331A1A1A),
    smudge = Color(0xFFF4F3EF),
    marker = Color(0xFFFFE45C),
    markerInk = Color(0xFF1A1A1A),
    redPen = Color(0xFFC81E1E),
)

/**
 * Dark is a night notebook, not white-on-black: a warm charcoal page and chalky
 * ink at 13.2:1, so thin strokes don't bloom, wells and rules that are actually
 * visible, and a marker toned down so a highlight glows instead of shouting.
 * Pencil is 8.1:1 and graphite 6.3:1 (5.2:1 in a well).
 */
private val Dark = BurrowColors(
    paper = Color(0xFF1E1D1B),
    ink = Color(0xFFE8E3D8),
    pencil = Color(0xFFB9B3A8),
    graphite = Color(0xFFA39D92),
    rule = Color(0x59E8E3D8),
    smudge = Color(0xFF2D2B28),
    marker = Color(0xFFD9BF45),
    markerInk = Color(0xFF1E1D1B),
    redPen = Color(0xFFFF8A80),
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

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
)

@OptIn(ExperimentalTextApi::class)
private fun shantell(informality: Float) = FontFamily(
    listOf(400, 600, 700).map { w ->
        Font(
            R.font.shantell_variable, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("INFM", informality)),
        )
    },
)

val PatrickHand = FontFamily(Font(R.font.patrickhand_regular, FontWeight.Normal))

/** The one choice at the top of the appearance settings: how Burrow is lettered. */
enum class TypeStyle(val label: String, val note: String) {
    HANDWRITTEN("handwritten", "Hand-lettered names and titles over a handwritten reading face."),
    FORMAL("formal", "A plain font for everything you read. Only headings and buttons stay hand-lettered."),
    ;

    /** The voice a style starts in. */
    val defaultVoice: Voice get() = if (this == HANDWRITTEN) Voice.NEAT else Voice.FRIENDLY

    /** The voices offered under this style, in the order they are listed. */
    val voices: List<Voice> get() = Voice.entries.filter { it.style == this }
}

/**
 * The face you read in, under the hand-lettering. The three hand voices are the
 * ones Burrow had up to 3.9 and belong to the handwritten style; the two sans
 * voices belong to the formal style. [hand] also decides the names-and-titles
 * styles in [Type]: Caveat for a hand voice, the voice's own face otherwise.
 * [scale] evens out a face that draws small (Patrick Hand).
 */
enum class Voice(val label: String, val note: String, val family: FontFamily, val hand: Boolean, val scale: Float = 1f) {
    NEAT("neat hand", "Shantell Sans, marker-neat", shantell(0f), hand = true),
    LOOSE("loose hand", "Shantell Sans, informal", shantell(100f), hand = true),
    HANDWRITTEN("all handwritten", "Patrick Hand", PatrickHand, hand = true, scale = 1.14f),
    FRIENDLY("friendly", "Nunito, a rounded sans", Nunito, hand = false),
    FORMAL("crisp", "Inter, a crisp sans", Inter, hand = false),
    ;

    val style: TypeStyle get() = if (hand) TypeStyle.HANDWRITTEN else TypeStyle.FORMAL

    companion object {
        /** A fresh install starts handwritten, in the neat hand, as before 4.0. */
        val DEFAULT = NEAT

        /** A stored voice name, or null when nothing (or something unknown) is stored. */
        fun fromStored(name: String?): Voice? = entries.firstOrNull { it.name == name }
    }
}

/** Light, dark, or whatever the phone is set to. */
enum class ThemeMode(val label: String) { SYSTEM("like the phone"), LIGHT("light"), DARK("dark") }

data class BodyType(val family: FontFamily, val hand: Boolean = false, val scale: Float = 1f)

val LocalBodyType = staticCompositionLocalOf { BodyType(Voice.DEFAULT.family, Voice.DEFAULT.hand, Voice.DEFAULT.scale) }

/** The font for reading text under the hand-lettering, per the chosen [Voice]. */
val bodyFont: FontFamily @Composable get() = LocalBodyType.current.family

/** A body text style in the chosen voice. Only Theme.kt and HandUi.kt build sizes; screens use [Type]. */
@Composable
internal fun bodyStyle(size: Float, lineHeight: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    bodyText(LocalBodyType.current, size, lineHeight, weight)

private fun bodyText(body: BodyType, size: Float, lineHeight: Float, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = body.family, fontWeight = weight, fontSize = (size * body.scale).sp, lineHeight = (lineHeight * body.scale).sp)

/**
 * A names-and-titles style: hand-lettered ([hand], Caveat) when the voice is a
 * hand voice, otherwise the plain body role 4.0 gave it. This is the one place
 * the handwritten / formal choice is made; screens only name the style.
 */
@Composable
private fun voiced(hand: TextStyle, clamp: Boolean, size: Float, lineHeight: Float, weight: FontWeight = FontWeight.Normal): TextStyle {
    val body = LocalBodyType.current
    return when {
        !body.hand -> bodyText(body, size, lineHeight, weight)
        clamp -> hand.clampHand(LocalDensity.current.fontScale)
        else -> hand
    }
}

/** Monospace for placeholders, tool calls and logs, with real fallbacks rather than an alias. */
val Mono: FontFamily = FontFamily.Monospace

/**
 * The roles for content: what you read, and the names of things (talks, people,
 * models, files). Reading text is always the voice's own face. The names and
 * titles ([contentTitle], [rowTitle], [tag], [note], [aside]) are Caveat in the
 * handwritten style, as they were up to 3.9, and the plain face in the formal
 * style (tokens 2.0.0 `type.body`). The sizes after each name are formal / handwritten.
 */
object Type {
    /** Title of a talk or person on its own page. 22/28, 600 · Caveat 34/38. */
    val contentTitle: TextStyle @Composable get() = voiced(HandBase.content, true, 22f, 28f, FontWeight.SemiBold)

    /** Summaries, transcripts, answers. 17/26. */
    val reading: TextStyle @Composable get() = bodyStyle(17f, 26f)

    /** Title of a content row: a talk, a person, a model, a file. 17/24, 600 · Caveat 26/30. */
    val rowTitle: TextStyle @Composable get() = voiced(HandBase.row, true, 17f, 24f, FontWeight.SemiBold)

    /** Sentences, intros, field text. 16/24. */
    val body: TextStyle @Composable get() = bodyStyle(16f, 24f)

    /** A short line of the notebook's own: a to-do, a ledger line, a done note, a choice in a list. 16/24 · Caveat 22/26. */
    val note: TextStyle @Composable get() = voiced(HandBase.note, false, 16f, 24f)

    /** Second line of a row, notes under a head (pencil). 14/20. */
    val meta: TextStyle @Composable get() = bodyStyle(14f, 20f)

    /** A small remark beside a row ("→ Priya"). 14/20 · Caveat 18/22. */
    val aside: TextStyle @Composable get() = voiced(HandBase.small, false, 14f, 20f)

    /** Timestamps, counts, sizes (pencil). 13/18. */
    val caption: TextStyle @Composable get() = bodyStyle(13f, 18f)

    /** Tags, step numbers, badges. 13/18, 600 · Caveat 18/22. */
    val tag: TextStyle @Composable get() = voiced(HandBase.small, false, 13f, 18f, FontWeight.SemiBold)

    /** Placeholders like [NAME_1], tool calls, logs. 13/20, ink. */
    val mono: TextStyle get() = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 20.sp)

    /** The sample sentence for [voice] in the settings list, set in that voice. */
    fun sample(voice: Voice): TextStyle = bodyText(BodyType(voice.family, voice.hand, voice.scale), 16f, 24f)
}

/**
 * Hand-lettered sizes. The first five are tokens 2.0.0 `type.hand`. The last
 * four are Burrow's own, for the handwritten style: the 3.9 hand styles for
 * names, titles, notes and tags, which the generated tokens no longer carry.
 */
private object HandBase {
    val display = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-0.01).em)
    val headline = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 42.sp)
    val title = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)
    val door = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp)
    val label = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp)

    val content = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp)
    val row = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp)
    val note = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp)
    val small = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp)
}

/** Hand styles grow with the phone's text size only up to 1.3x, so headings don't push the content off the screen. */
private fun TextStyle.clampHand(fontScale: Float): TextStyle =
    if (fontScale <= MAX_HAND_SCALE) this else copy(fontSize = fontSize * (MAX_HAND_SCALE / fontScale), lineHeight = lineHeight * (MAX_HAND_SCALE / fontScale))

private const val MAX_HAND_SCALE = 1.3f

/**
 * Hand-lettered styles: the wordmark and the record timer ([display]), page
 * titles ([headline]), section heads, sheet titles and empty states ([title]),
 * rows that lead to a place in the app and switch titles ([door]), and
 * buttons, links, nav words and choice pills ([label]). Four words or fewer.
 */
object Hand {
    val display: TextStyle @Composable get() = HandBase.display.clampHand(LocalDensity.current.fontScale)
    val headline: TextStyle @Composable get() = HandBase.headline.clampHand(LocalDensity.current.fontScale)
    val title: TextStyle @Composable get() = HandBase.title.clampHand(LocalDensity.current.fontScale)
    val door: TextStyle @Composable get() = HandBase.door.clampHand(LocalDensity.current.fontScale)
    val label: TextStyle @Composable get() = HandBase.label.clampHand(LocalDensity.current.fontScale)
}

private fun typography(body: BodyType, fontScale: Float): Typography {
    val display = HandBase.display.clampHand(fontScale)
    val headline = HandBase.headline.clampHand(fontScale)
    val title = HandBase.title.clampHand(fontScale)
    val label = HandBase.label.clampHand(fontScale)
    val caption = bodyText(body, 13f, 18f)
    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = headline,
        headlineLarge = headline,
        headlineMedium = headline,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = HandBase.door.clampHand(fontScale),
        titleSmall = label,
        labelLarge = label,
        bodyLarge = bodyText(body, 17f, 26f),
        bodyMedium = bodyText(body, 16f, 24f),
        bodySmall = bodyText(body, 14f, 20f),
        labelMedium = caption,
        labelSmall = caption,
    )
}

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
fun BurrowTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    voice: Voice = Voice.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) Dark else Light
    val body = BodyType(voice.family, voice.hand, voice.scale)
    val fontScale = LocalDensity.current.fontScale
    CompositionLocalProvider(LocalBurrowColors provides colors, LocalBodyType provides body) {
        MaterialTheme(colorScheme = scheme(colors, dark), typography = typography(body, fontScale), shapes = BurrowShapes, content = content)
    }
}

/** Whether the theme in effect is the dark one (for system bars and previews). */
val isBurrowDark: Boolean @Composable get() = LocalBurrowColors.current.paper == Dark.paper

/**
 * Speakers and labels are told apart by ink texture first; where a hue is still
 * needed (a speaker dot, a chart), these are muted, pencil-box tones. Dark has
 * its own set, each at least 6:1 on dark paper.
 */
val SeriesColors = listOf(
    Color(0xFF1A1A1A), Color(0xFF8A6D1F), Color(0xFF3E6B5A), Color(0xFF7A4A6B),
    Color(0xFF4A5E85), Color(0xFF9A4B3C), Color(0xFF5F6B2E), Color(0xFF6B6B6B),
)

val SeriesColorsDark = listOf(
    Color(0xFFE8E3D8), Color(0xFFBD9834), Color(0xFF6EA691), Color(0xFFB890AC),
    Color(0xFF8E9DBC), Color(0xFFC98E82), Color(0xFF92A34D), Color(0xFF9B9B9B),
)

private fun wrap(index: Int) = ((index % SeriesColors.size) + SeriesColors.size) % SeriesColors.size

/** The light series colour for [index]; draw it through [themed] so dark mode gets its own set. */
fun seriesColor(index: Int, dark: Boolean = false): Color = (if (dark) SeriesColorsDark else SeriesColors)[wrap(index)]

/** A series colour as the current theme should draw it. */
@Composable
fun themed(series: Color): Color {
    if (!isBurrowDark) return series
    val i = SeriesColors.indexOf(series)
    return if (i >= 0) SeriesColorsDark[i] else series
}
