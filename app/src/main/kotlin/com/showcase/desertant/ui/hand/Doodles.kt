package com.showcase.desertant.ui.hand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.showcase.desertant.ui.theme.burrow

/**
 * The hand-drawn icon set. Each doodle is a few loose strokes on a 24×24 grid,
 * drawn slightly off-true on purpose, so they sit with the Caveat lettering
 * rather than with Material's geometric icons.
 */
enum class Doodle(internal val d: String, internal val dots: List<Pair<Float, Float>> = emptyList()) {
    ARROW("M3 12.6 C8 11.7 14 12.5 20.2 11.9 M15.2 7.1 C17 8.9 18.9 10.6 20.6 11.9 C18.8 13.3 17 15 15.3 16.9"),
    BACK("M21 12.4 C16 11.6 10 12.5 3.8 12 M8.8 7 C7 8.8 5.1 10.5 3.4 12 C5.2 13.4 7 15.1 8.7 17"),
    CLOSE("M5.5 5.8 C10 10.2 14 14.1 18.6 18.4 M18.3 5.4 C13.9 9.8 10 13.9 5.6 18.7"),
    LOCK("M6 11.2 C10 10.8 14.2 11 18.2 11.3 C18.5 14.2 18.4 17.4 18 20.5 C14 20.9 10 20.8 6.1 20.4 C5.7 17.4 5.7 14.2 6 11.2 Z M8.3 11 L8.3 8.2 C8.4 4.4 15.8 4.3 15.8 8.3 L15.7 11.1 M12 14.6 L12.1 17.2"),
    LOCK_OPEN("M6 11.2 C10 10.8 14.2 11 18.2 11.3 C18.5 14.2 18.4 17.4 18 20.5 C14 20.9 10 20.8 6.1 20.4 C5.7 17.4 5.7 14.2 6 11.2 Z M8.3 11 L8.3 8.2 C8.4 4.6 15.4 4 15.9 7.4 M12 14.6 L12.1 17.2"),
    TICK("M4.2 12.8 C6 14.4 7.6 16.2 9.4 18.2 C12.6 13.6 16 9.2 20.2 5.2"),
    CROSS("M5.5 5.8 C10 10.2 14 14.1 18.6 18.4 M18.3 5.4 C13.9 9.8 10 13.9 5.6 18.7"),
    STAR("M12 3.2 L14.4 9.1 L20.8 9.4 L15.8 13.4 L17.6 19.7 L12.1 16.1 L6.5 19.8 L8.2 13.5 L3.2 9.5 L9.6 9 Z"),
    MIC("M9.2 6.3 C9.2 2.6 14.8 2.6 14.8 6.3 L14.8 11.6 C14.8 15.3 9.2 15.3 9.2 11.6 Z M6 11.4 C6.2 19 17.8 19 18 11.2 M12 17.4 L12 21 M8.6 21.1 L15.4 20.9"),
    PLAY("M8.2 5 C11.9 7.2 15.5 9.6 19.2 12 C15.5 14.4 11.9 16.9 8.3 19.1 C8 14.4 8 9.7 8.2 5 Z"),
    PAUSE("M8.6 5.4 L8.4 18.8 M15.4 5.2 L15.6 18.6"),
    STOP("M6.4 6.6 C10.2 6.2 14 6.3 17.6 6.5 C17.9 10.2 17.8 14 17.5 17.6 C13.8 17.9 10 17.8 6.5 17.5 C6.1 13.9 6.1 10.2 6.4 6.6 Z"),
    EYE("M2.6 12.2 C6.2 6 17.6 5.8 21.4 12 C17.8 18.1 6.4 18.3 2.6 12.2 Z M12 9.2 C15.8 9.3 15.8 14.9 12 14.9 C8.3 14.9 8.3 9.2 12 9.2"),
    EYE_OFF("M2.6 12.2 C6.2 6 17.6 5.8 21.4 12 C17.8 18.1 6.4 18.3 2.6 12.2 Z M4.4 4.4 C10 10 14 14 19.8 19.6"),
    SEARCH("M10.4 3.8 C15 3.9 17 7.6 16.6 10.6 C16.1 14.6 12 16.8 8.6 16 C5 15.1 3.4 11.4 4.2 8.4 C4.9 5.6 7.4 3.8 10.4 3.8 M15 15.2 C16.8 17 18.6 18.8 20.6 20.6"),
    PLUS("M12.1 4.4 C12 9.5 11.9 14.6 12 19.6 M4.4 12.2 C9.5 11.9 14.5 11.9 19.6 12.1"),
    SHARE("M12 15 L12.1 3.6 M7.8 7.6 C9.2 6.2 10.6 4.9 12 3.5 C13.4 4.9 14.8 6.3 16.2 7.7 M6.6 11.4 L4.8 11.5 C4.6 14.6 4.7 17.8 5 20.6 C9.6 21 14.4 21 19 20.6 C19.3 17.6 19.3 14.6 19.1 11.5 L17.4 11.4"),
    EXCLAIM("M12.2 3.6 C12 7.6 11.9 11.4 11.8 15", listOf(11.9f to 19.6f)),
    SQUIGGLE("M2.5 12.5 C4.5 8.5 6.5 8.5 8 12 C9.5 15.6 11.6 15.6 13 12 C14.6 8.4 16.6 8.6 18 12 C19.2 14.8 20.6 14.6 21.6 12.4"),
    PHONE("M8 3.4 C10.7 3.2 13.3 3.2 16 3.4 C16.4 9.1 16.4 14.9 16 20.6 C13.3 20.9 10.7 20.9 8 20.6 C7.6 14.9 7.6 9.1 8 3.4 Z M10.9 17.8 L13.1 17.8"),
    TRASH("M4.6 6.6 L19.4 6.4 M9.4 6.4 C9.4 4.6 14.6 4.6 14.6 6.4 M6.4 6.8 C6.8 11.4 7.2 16 7.8 20.4 C10.6 20.8 13.4 20.8 16.2 20.4 C16.8 16 17.2 11.4 17.6 6.8"),
    PERSON("M12 3.6 C15.4 3.6 15.6 9.8 12 9.9 C8.5 9.9 8.6 3.6 12 3.6 M4.6 20.4 C5.4 13.6 18.6 13.4 19.4 20.4"),
    WAVE("M3 12 L5 12 M6.6 9 L6.6 15 M9.4 6 L9.4 18 M12.2 9.6 L12.2 14.4 M15 4.8 L15 19.2 M17.8 8.4 L17.8 15.6 M20.6 11 L20.6 13"),
    PEN("M4.6 19.6 C4.8 18.2 5 16.8 5.4 15.4 L15.6 5.2 C16.6 4.2 18.6 4.4 19.4 5.4 C20.2 6.4 20 8 19 9 L8.8 19.2 C7.4 19.6 6 19.8 4.6 19.6 Z M14 6.8 L17.4 10.2"),
    GEAR("M12 5.8 C15.5 5.8 18.2 8.5 18.2 12 C18.2 15.5 15.5 18.2 12 18.2 C8.5 18.2 5.8 15.5 5.8 12 C5.8 8.5 8.5 5.8 12 5.8 Z M12 9.8 C13.3 9.8 14.2 10.7 14.2 12 C14.2 13.3 13.3 14.2 12 14.2 C10.7 14.2 9.8 13.3 9.8 12 C9.8 10.7 10.7 9.8 12 9.8 M12 2.8 L12 5.8 M12 18.2 L12 21.2 M2.8 12 L5.8 12 M18.2 12 L21.2 12 M5.6 5.6 L7.7 7.7 M16.3 16.3 L18.4 18.4 M5.6 18.4 L7.7 16.3 M16.3 7.7 L18.4 5.6"),
    SUN("M12 8.4 C14.1 8.4 15.6 9.9 15.6 12 C15.6 14.1 14.1 15.6 12 15.6 C9.9 15.6 8.4 14.1 8.4 12 C8.4 9.9 9.9 8.4 12 8.4 Z M12 2.6 L12.1 5.2 M12 18.8 L11.9 21.4 M2.6 12 L5.2 11.9 M18.8 12 L21.4 12.1 M5.3 5.3 L7.2 7.2 M16.8 16.8 L18.7 18.7 M5.3 18.7 L7.2 16.8 M16.8 7.2 L18.7 5.3"),
    MOON("M15.8 4.2 C10.6 4.4 7 8.4 7.2 13 C7.4 17.6 11.6 20.8 16.2 19.8 C17.8 19.4 19.2 18.6 20.2 17.4 C15.6 17.8 12 14.6 11.9 10.4 C11.8 7.8 13.4 5.6 15.8 4.2 Z", listOf(5.4f to 6.4f, 4.4f to 15.2f)),
    CAMERA("M3.6 8.2 C3.6 7.2 4.4 6.6 5.4 6.6 L8.2 6.6 L9.6 4.6 L14.4 4.6 L15.8 6.6 L18.6 6.6 C19.6 6.6 20.4 7.3 20.4 8.3 L20.3 17.6 C20.3 18.6 19.5 19.3 18.5 19.3 L5.5 19.4 C4.5 19.4 3.7 18.7 3.7 17.7 Z M12 9.4 C14.2 9.4 15.6 11 15.6 12.9 C15.6 14.9 14.1 16.4 12 16.4 C9.9 16.4 8.4 14.9 8.4 12.9 C8.4 11 9.8 9.4 12 9.4"),
    BUBBLE("M4 6.4 C4 4.9 5.2 4 6.6 4 L17.4 4 C18.9 4 20 5 20 6.4 L20 13.6 C20 15 18.9 16 17.4 16 L10.2 16 L6 19.8 L6.6 16 C5.2 16 4 15 4 13.6 Z M8 8.6 L16 8.4 M8 11.6 L13.4 11.5"),
    PEOPLE("M8.6 4.6 C11 4.6 11.2 9.4 8.6 9.4 C6 9.4 6.2 4.6 8.6 4.6 M2.8 18.8 C3.2 13.8 14 13.6 14.4 18.8 M15.6 5.6 C17.8 5.6 18 9.8 15.6 9.8 M16.6 13.2 C19.2 13.6 21 15.6 21.2 18.6"),
    MASK("M2.8 9 C6 8 9 8.4 12 10 C15 8.4 18 8 21.2 9 C21.2 13.2 19 16 16 16 C14 16 12.8 14.6 12 13.4 C11.2 14.6 10 16 8 16 C5 16 2.8 13.2 2.8 9 Z M6.6 11.4 C7.6 10.8 8.8 10.9 9.6 11.6 M14.4 11.6 C15.2 10.9 16.4 10.8 17.4 11.4"),
    CLOUD_OFF("M7 17.6 C4.6 17.6 3 16 3 14 C3 12 4.6 10.6 6.6 10.6 C7 7.6 9.4 5.6 12.2 5.6 C15 5.6 17.2 7.6 17.6 10.2 C19.6 10.4 21 11.8 21 13.8 C21 15.9 19.4 17.6 17.2 17.6 Z M4 4 L20 20.4"),
    CURL("M3 5 C8 3.6 12.6 5.4 12.2 9 C11.8 12.4 7.4 12 8.2 9.2 C9 6.6 15 7.4 17.4 12.6 C18.4 14.8 18.8 17 18.6 19.6 M15.6 17.2 L18.6 19.8 L21 16.8"),
    HEART("M12 20 C7 16.4 3.6 13.2 3.8 9.4 C4 6.6 6.2 4.8 8.6 5 C10.2 5.1 11.3 6 12 7.2 C12.7 6 13.8 5.1 15.4 5 C17.8 4.8 20 6.6 20.2 9.4 C20.4 13.2 17 16.4 12 20 Z"),
    SPARK("M12 3 C12.6 8.4 14.2 10.6 20 12 C14.2 13.4 12.6 15.6 12 21 C11.4 15.6 9.8 13.4 4 12 C9.8 10.6 11.4 8.4 12 3 Z"),
}

private val parsed = HashMap<Doodle, Path>()

private fun Doodle.path(): Path = synchronized(parsed) { parsed.getOrPut(this) { PathParser().parsePathString(d).toPath() } }

@Composable
fun DoodleIcon(
    doodle: Doodle,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = burrow.ink,
    stroke: Dp = 2.dp,
    fill: Boolean = false,
) {
    val path = remember(doodle) { doodle.path() }
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(size).then(semantics)) {
        val k = this.size.minDimension / 24f
        val w = stroke.toPx() / k
        scale(k, pivot = Offset.Zero) {
            if (fill) drawPath(path, color)
            drawPath(path, color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            doodle.dots.forEach { (x, y) -> drawCircle(color, radius = w * 0.75f, center = Offset(x, y)) }
        }
    }
}
