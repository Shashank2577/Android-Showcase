package com.showcase.desertant.ui.models

import ai.desertant.shapes.Options
import ai.desertant.shapes.Point
import ai.desertant.shapes.Rotation
import ai.desertant.shapes.Shape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.ui.components.ChoiceChips
import com.showcase.desertant.ui.components.LabeledSlider
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.prettify
import com.showcase.desertant.ui.components.rememberRunner
import com.showcase.desertant.ui.theme.seriesColor

/** Something on the canvas: a recognized shape, or raw ink Shapes declined. */
private sealed interface Mark {
    data class Clean(val shape: Shape) : Mark
    data class Ink(val points: List<Offset>) : Mark
}

private val OPEN_KINDS = setOf(Shape.Kind.LINE, Shape.Kind.CURVE, Shape.Kind.ARROW, Shape.Kind.SCRIBBLE)

@Composable
fun ShapesScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val runner = rememberRunner(ModelKind.SHAPES)
    val marks = remember { mutableStateListOf<Mark>() }
    val stroke = remember { mutableStateListOf<Offset>() }
    var rotation by rememberSaveable { mutableStateOf(Rotation.SNAPPED) }
    var minConfidence by rememberSaveable { mutableFloatStateOf(0f) }
    var last by remember { mutableStateOf("Draw a shape with one stroke.") }

    val inkColor = MaterialTheme.colorScheme.onSurfaceVariant
    val canvasColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    fun finishStroke() {
        val points = stroke.toList()
        stroke.clear()
        if (points.size < 2) return
        runner.runLatest {
            val shape = hub.shapes.recognize(
                points.map { Point(it.x.toDouble(), it.y.toDouble()) },
                Options(rotation = rotation, minimumConfidence = minConfidence.toDouble()),
            )
            when (shape) {
                null -> {
                    marks.add(Mark.Ink(points))
                    last = "Not a shape — kept as ink."
                }
                is Shape.Scribble -> {
                    // A scribble is the erase gesture: remove whatever its hull covers.
                    val before = marks.size
                    marks.removeAll { covered(it, shape.hull) }
                    last = "Scribble: erased ${before - marks.size} mark(s)."
                }
                else -> {
                    marks.add(Mark.Clean(shape))
                    last = "Recognized: ${prettify(shape.kind.name.lowercase())}"
                }
            }
        }
    }

    ModelScreen(ModelKind.SHAPES, onBack, runner) {
        SectionCard("Canvas") {
            Text(last, style = MaterialTheme.typography.titleMedium)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(canvasColor)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { stroke.clear(); stroke.add(it) },
                            onDrag = { change, _ -> stroke.add(change.position) },
                            onDragEnd = { finishStroke() },
                            onDragCancel = { stroke.clear() },
                        )
                    },
            ) {
                marks.forEachIndexed { i, mark ->
                    when (mark) {
                        is Mark.Ink -> drawPolyline(mark.points, inkColor.copy(alpha = 0.6f), closed = false, width = 3f)
                        is Mark.Clean -> drawShape(mark.shape, seriesColor(i))
                    }
                }
                drawPolyline(stroke, inkColor, closed = false, width = 4f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex) }) { Text("Undo") }
                OutlinedButton(onClick = { marks.clear(); last = "Draw a shape with one stroke." }) { Text("Clear") }
            }
            Text(
                "Try: line, curve, arrow, rectangle, ellipse, triangle, diamond, pentagon, hexagon, star, heart, cloud. Scribble over a shape to erase it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SectionCard("Options") {
            Text("Rotation", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(Rotation.entries, rotation, { rotation = it }) { prettify(it.name.lowercase()) }
            LabeledSlider("Minimum confidence", minConfidence, { minConfidence = it }, 0f..0.9f, "%.2f".format(minConfidence))
        }
    }
}

private fun DrawScope.drawShape(shape: Shape, color: Color) {
    val outline = shape.outline.map { Offset(it.x.toFloat(), it.y.toFloat()) }
    drawPolyline(outline, color, closed = shape.kind !in OPEN_KINDS, width = 5f)
    if (shape is Shape.Arrow) {
        shape.arrowheads(length = 28.0).forEach { head ->
            drawPolyline(head.map { Offset(it.x.toFloat(), it.y.toFloat()) }, color, closed = false, width = 5f)
        }
    }
}

private fun DrawScope.drawPolyline(points: List<Offset>, color: Color, closed: Boolean, width: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        if (closed) close()
    }
    drawPath(path, color, style = Stroke(width = width * density / 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Whether most of [mark]'s points fall inside the scribble [hull]. */
private fun covered(mark: Mark, hull: List<Point>): Boolean {
    val points = when (mark) {
        is Mark.Ink -> mark.points.map { Point(it.x.toDouble(), it.y.toDouble()) }
        is Mark.Clean -> mark.shape.outline
    }
    if (points.isEmpty() || hull.size < 3) return false
    return points.count { inside(it, hull) } >= points.size / 2
}

private fun inside(p: Point, polygon: List<Point>): Boolean {
    var result = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) result = !result
        j = i
    }
    return result
}
