@file:OptIn(ExperimentalMaterial3Api::class)

package com.showcase.desertant.ui.hand

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.bodyStyle
import com.showcase.desertant.ui.theme.burrow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A readable column on any width: a phone, the Fold's inner screen, landscape. */
val MaxColumn = 560.dp
val Gutter = 24.dp

/** Small deterministic wobble so a stroke looks drawn, and looks the same on every frame. */
private fun wobble(seed: Int, i: Int, amount: Float): Float {
    var x = seed * 374761393 + i * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f * 2f * amount - amount
}

/** A slightly uneven horizontal stroke from [start] to [end]. */
private fun DrawScope.handLine(start: Offset, end: Offset, color: Color, width: Float, seed: Int) {
    val p = Path().apply {
        moveTo(start.x, start.y)
        val segments = 4
        for (i in 1..segments) {
            val t = i / segments.toFloat()
            val x = start.x + (end.x - start.x) * t
            val y = start.y + (end.y - start.y) * t + wobble(seed, i, 1.2.dp.toPx())
            val cx = start.x + (end.x - start.x) * (t - 0.5f / segments)
            quadraticTo(cx, y + wobble(seed, i + 10, 1.dp.toPx()), x, y)
        }
    }
    drawPath(p, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** A highlighter swipe across [rect]: rough ends, slightly tilted. */
private fun DrawScope.swipe(rect: Rect, color: Color, seed: Int) {
    val pad = 3.dp.toPx()
    val top = rect.top + rect.height * 0.38f
    val bottom = rect.bottom - rect.height * 0.04f
    val l = rect.left - pad
    val r = rect.right + pad
    val p = Path().apply {
        moveTo(l + wobble(seed, 1, pad), top + wobble(seed, 2, 2.dp.toPx()))
        quadraticTo((l + r) / 2, top - 2.dp.toPx() + wobble(seed, 3, 1.dp.toPx()), r, top + wobble(seed, 4, 2.dp.toPx()))
        lineTo(r + wobble(seed, 5, pad), bottom + wobble(seed, 6, 2.dp.toPx()))
        quadraticTo((l + r) / 2, bottom + 2.dp.toPx(), l, bottom + wobble(seed, 7, 2.dp.toPx()))
        close()
    }
    drawPath(p, color)
}

/** Marker swipe behind the whole element. */
fun Modifier.marker(color: Color, seed: Int = 7): Modifier = drawBehind { swipe(Rect(Offset.Zero, size), color, seed) }

/** A wobbly underline below the element. */
fun Modifier.handUnderline(color: Color, width: Dp = 1.6.dp, seed: Int = 3): Modifier = drawBehind {
    val y = size.height - 1.dp.toPx()
    handLine(Offset(0f, y), Offset(size.width, y + wobble(seed, 0, 1.dp.toPx())), color, width.toPx(), seed)
}

/** A loose hand-drawn ring around the element that doesn't quite close. */
fun Modifier.handCircle(color: Color, progress: Float = 1f, width: Dp = 1.8.dp, seed: Int = 5): Modifier = drawBehind {
    if (progress <= 0f) return@drawBehind
    val cx = size.width / 2
    val cy = size.height / 2
    val rx = size.width / 2 + 9.dp.toPx()
    val ry = size.height / 2 + 4.dp.toPx()
    val start = -2.2f
    val sweep = (2 * PI.toFloat() + 0.45f) * progress
    val p = Path()
    val steps = 48
    for (i in 0..steps) {
        val a = start + sweep * i / steps
        val grow = 1f + 0.05f * i / steps + wobble(seed, i / 6, 0.015f)
        val x = cx + cos(a) * rx * grow
        val y = cy + sin(a) * ry * grow
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    drawPath(p, color, style = Stroke(width = width.toPx(), cap = StrokeCap.Round))
}

/** Text with a marker swipe behind the first occurrence of [mark]; null means no marker. */
@Composable
fun MarkedText(
    text: String,
    modifier: Modifier = Modifier,
    mark: String? = null,
    style: TextStyle = Hand.title,
    color: Color = burrow.ink,
    markColor: Color = burrow.marker,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val start = if (mark.isNullOrEmpty()) -1 else text.indexOf(mark, ignoreCase = true)
    val end = if (start < 0) -1 else start + mark!!.length
    // Marked words stay ink-on-yellow in both themes.
    val markInk = burrow.markerInk
    val annotated = remember(text, start, end, markInk) {
        buildAnnotatedString {
            append(text)
            if (start >= 0) addStyle(SpanStyle(color = markInk), start, end)
        }
    }
    Text(
        annotated,
        modifier.drawBehind {
            val l = layout ?: return@drawBehind
            if (start < 0 || end > l.layoutInput.text.length) return@drawBehind
            val first = l.getLineForOffset(start)
            val last = l.getLineForOffset((end - 1).coerceAtLeast(start))
            for (line in first..last) {
                val s = if (line == first) start else l.getLineStart(line)
                val e = if (line == last) end else l.getLineEnd(line, visibleEnd = true)
                if (e <= s) continue
                val left = l.getHorizontalPosition(s, true)
                val right = l.getHorizontalPosition(e, true)
                val rect = Rect(minOf(left, right), l.getLineTop(line), maxOf(left, right), l.getLineBottom(line))
                swipe(rect, markColor, line + 11)
            }
        },
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
    )
}

/** A sketchy hairline. */
@Composable
fun HandDivider(modifier: Modifier = Modifier, seed: Int = 1) {
    val c = burrow.rule
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        handLine(Offset(0f, size.height / 2), Offset(size.width, size.height / 2 + 1f), c, 1.2.dp.toPx(), seed)
    }
}

/** Quiet secondary text. */
@Composable
fun Meta(text: String, modifier: Modifier = Modifier, color: Color = burrow.pencil, textAlign: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, style = bodyStyle(13f, 18f), color = color, textAlign = textAlign, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Explanations. */
@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = burrow.ink, textAlign: TextAlign? = null) {
    Text(text, modifier, style = bodyStyle(15f, 22f), color = color, textAlign = textAlign)
}

/** The primary action: a black pill with hand-lettered white text. One per screen. */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    trailing: Doodle? = null,
    danger: Boolean = false,
) {
    val c = burrow
    val bg = if (danger) c.redPen else c.ink
    Row(
        modifier
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = c.paper, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = Hand.label.copy(fontSize = 23.sp), color = c.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            DoodleIcon(trailing, null, size = 20.dp, color = c.paper)
        }
    }
}

/** The secondary action: hand-lettered text with a wobbly underline. */
@Composable
fun HandLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = burrow.ink, enabled: Boolean = true, style: TextStyle = Hand.label) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Text(text, Modifier.handUnderline(color.copy(alpha = 0.8f), seed = text.length), style = style, color = color)
    }
}

/** A drawn toggle: an ink pill with a ticked knob when on, an outline when off. */
@Composable
fun HandSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = burrow
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(220), label = "switch")
    val toggle = if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange) else Modifier
    Box(
        modifier
            .size(52.dp, 30.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(toggle),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.height / 2
            if (t > 0f) drawRoundRect(c.ink.copy(alpha = t), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            drawRoundRect(c.ink, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(1.8.dp.toPx()))
            val knob = r - 4.dp.toPx()
            val cx = r + (size.width - 2 * r) * t
            drawCircle(if (t > 0.5f) c.paper else c.paper, knob, Offset(cx, r))
            drawCircle(c.ink, knob, Offset(cx, r), style = Stroke(1.6.dp.toPx()))
        }
        if (checked) DoodleIcon(Doodle.TICK, null, Modifier.align(Alignment.CenterEnd).padding(end = 7.dp), size = 16.dp, stroke = 2.2.dp)
    }
}

/** A row with a hand-lettered label and a switch: the privacy and permission rows. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, body: String? = null, enabled: Boolean = true, doodle: Doodle? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (doodle != null) {
            DoodleIcon(doodle, null, size = 26.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Hand.door.copy(fontSize = 24.sp), color = burrow.ink)
            if (body != null) Meta(body)
        }
        Spacer(Modifier.width(12.dp))
        HandSwitch(checked, null, enabled = enabled)
    }
}

/**
 * A door: one big hand-lettered line that leads somewhere, with a drawn arrow.
 * Today and the Photos page are made of these.
 */
@Composable
fun Door(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    mark: String? = null,
    meta: String? = null,
    alert: Boolean = false,
    style: TextStyle = Hand.door.copy(fontSize = 28.sp, lineHeight = 32.sp),
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                // A door with nowhere to go is drawn in pencil, so only live ones read as tappable.
                MarkedText(text, Modifier.weight(1f, fill = false), mark = mark, style = style, color = if (onClick != null) burrow.ink else burrow.pencil)
                if (alert) DoodleIcon(Doodle.EXCLAIM, "needs you", Modifier.padding(start = 2.dp, top = 4.dp), size = 22.dp, color = burrow.redPen, stroke = 2.6.dp)
            }
            if (meta != null) Meta(meta, Modifier.padding(top = 2.dp))
        }
        if (onClick != null) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(40.dp).border(1.6.dp, burrow.ink, CircleShape), contentAlignment = Alignment.Center) {
                DoodleIcon(Doodle.ARROW, null, size = 22.dp)
            }
        }
    }
}

/** A hand-lettered section head with optional right-hand note. */
@Composable
fun SectionHead(text: String, modifier: Modifier = Modifier, note: String? = null, mark: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        MarkedText(text, Modifier.weight(1f, fill = false), mark = if (mark) text else null, style = Hand.title.copy(fontSize = 27.sp))
        Spacer(Modifier.weight(0.01f))
        if (note != null) Meta(note, Modifier.padding(start = 12.dp, bottom = 4.dp))
    }
}

/** A small drawn icon button (back, lock, close…), 48 dp to touch. */
@Composable
fun DoodleButton(doodle: Doodle, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = burrow.ink, size: Dp = 26.dp) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { DoodleIcon(doodle, contentDescription, size = size, color = color) }
}

/** The quiet page every screen sits on. */
@Composable
fun HandPage(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    wordmark: Boolean = false,
    topEnd: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(burrow.paper)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.widthIn(max = MaxColumn + Gutter * 2).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) DoodleButton(Doodle.BACK, "Back", onBack)
                if (wordmark) Text("burrow.", Modifier.padding(start = if (onBack == null) 12.dp else 0.dp), style = Hand.title.copy(fontSize = 30.sp), color = burrow.ink)
                Spacer(Modifier.weight(1f))
                topEnd()
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = MaxColumn).fillMaxWidth().padding(horizontal = Gutter).padding(bottom = 32.dp),
                content = content,
            )
        }
        if (bottom != null) {
            Box(Modifier.fillMaxWidth().background(burrow.paper), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = MaxColumn).fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), content = bottom)
            }
        }
    }
}

/** A photo as a small plain snapshot: white border, slight tilt, a 2 dp lift. */
@Composable
fun Snapshot(modifier: Modifier = Modifier, tilt: Float = 0f, faded: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .rotate(tilt)
            .shadow(2.dp, RoundedCornerShape(2.dp))
            .background(burrow.paper)
            .padding(4.dp)
            .alpha(if (faded) 0.38f else 1f),
        content = content,
    )
}

/** A hand-drawn progress line: graphite track, inked part. */
@Composable
fun HandProgress(fraction: Float?, modifier: Modifier = Modifier) {
    val c = burrow
    val f by animateFloatAsState(fraction ?: 0f, tween(400), label = "progress")
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val y = size.height / 2
        handLine(Offset(0f, y), Offset(size.width, y), c.rule, 2.dp.toPx(), 9)
        if (fraction != null && f > 0f) handLine(Offset(0f, y), Offset(size.width * f, y), c.ink, 3.dp.toPx(), 9)
    }
}

/** A text well: the only filled shape, used for inputs and the mask box. */
@Composable
fun HandField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = false,
    minHeight: Dp = 0.dp,
    leading: Doodle? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = bodyStyle(16f, 23f),
) {
    val c = burrow
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxOf(minHeight, 52.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.smudge)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (leading != null) {
                    DoodleIcon(leading, null, size = 20.dp, color = c.pencil)
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = c.graphite)
                    inner()
                }
            }
        },
    )
}

/** A bottom sheet on paper, with a drawn squiggle for a handle. */
@Composable
fun HandSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = burrow
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.paper,
        contentColor = c.ink,
        dragHandle = { DoodleIcon(Doodle.SQUIGGLE, null, Modifier.padding(top = 10.dp), size = 34.dp, color = c.graphite) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** A yes/no question on a sheet. The confirm button is the one ink pill. */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    cancel: String = "not now",
) {
    HandSheet(onDismiss) {
        Text(title, style = Hand.headline.copy(fontSize = 34.sp), color = burrow.ink)
        Body(body, color = burrow.pencil)
        Spacer(Modifier.height(4.dp))
        InkButton(confirm, onConfirm, Modifier.fillMaxWidth(), danger = danger)
        HandLink(cancel, onDismiss, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** A gentle hand-lettered empty state. */
@Composable
fun HandEmpty(title: String, body: String, modifier: Modifier = Modifier, doodle: Doodle = Doodle.SPARK, action: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DoodleIcon(doodle, null, size = 40.dp, color = burrow.pencil)
        Text(title, style = Hand.title, color = burrow.ink, textAlign = TextAlign.Center)
        Body(body, color = burrow.pencil, textAlign = TextAlign.Center)
        action()
    }
}

/** Tabs at the foot of the four homes: words, with the current one circled. */
enum class HandTab(val route: String, val label: String) { TODAY("today", "today"), PHOTOS("photos", "photos"), TALKS("talks", "talks"), PEOPLE("people", "people") }

@Composable
fun HandNav(selected: HandTab?, onSelect: (HandTab) -> Unit, modifier: Modifier = Modifier) {
    val c = burrow
    Column(modifier.fillMaxWidth().background(c.paper).navigationBarsPadding()) {
        HandDivider(Modifier.padding(horizontal = Gutter))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HandTab.entries.forEachIndexed { i, tab ->
                if (i > 0) Text("·", style = Hand.label, color = c.graphite)
                val on = tab == selected
                val ring by animateFloatAsState(if (on) 1f else 0f, tween(450), label = "ring")
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(tab) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        tab.label,
                        Modifier.handCircle(c.ink, ring, seed = i + 2),
                        style = Hand.label.copy(fontSize = 23.sp),
                        color = if (on) c.ink else c.pencil,
                    )
                }
            }
        }
    }
}

/** A small pill tag ("roadmap", "on this phone"). */
@Composable
fun HandTag(text: String, modifier: Modifier = Modifier, marked: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = burrow
    Box(
        modifier
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (marked) Modifier.background(c.marker) else Modifier.border(1.2.dp, c.rule, CircleShape))
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) { Text(text, style = Hand.small, color = if (marked) c.markerInk else c.ink, maxLines = 1, softWrap = false) }
}

/** The "stays on this phone" stamp used in headers. */
@Composable
fun OnDeviceStamp(modifier: Modifier = Modifier, text: String = "on this phone") {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        DoodleIcon(Doodle.LOCK, null, size = 14.dp, color = burrow.pencil, stroke = 1.6.dp)
        Spacer(Modifier.width(5.dp))
        Meta(text)
    }
}


/** A secondary action that still looks like a button: an outlined pill. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: Doodle? = null, enabled: Boolean = true, color: Color = burrow.ink) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .border(1.6.dp, color, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            DoodleIcon(leading, null, size = 18.dp, color = color)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Hand.label, color = color, maxLines = 1, softWrap = false)
    }
}

/**
 * The top of a page: what it is, and in one plain line what you can do here.
 * Every screen starts with one, so nobody has to guess.
 */
@Composable
fun PageIntro(title: String, purpose: String, modifier: Modifier = Modifier, mark: String? = null, doodle: Doodle? = null) {
    Column(modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            MarkedText(title, Modifier.weight(1f, fill = false), mark = mark, style = Hand.headline)
            // A small drawing of what the page is about, tilted like a margin sketch.
            if (doodle != null) DoodleIcon(doodle, null, Modifier.padding(start = 12.dp, bottom = 6.dp).rotate(-8f), size = 40.dp, color = burrow.pencil, stroke = 1.8.dp)
        }
        Spacer(Modifier.height(4.dp))
        Body(purpose, color = burrow.pencil)
    }
}

/** A choice drawn as a pill: filled in ink when it's the one picked. */
@Composable
fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: Doodle? = null) {
    val c = burrow
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(c.ink) else Modifier.border(1.6.dp, c.ink, CircleShape))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            DoodleIcon(leading, null, size = 18.dp, color = if (selected) c.paper else c.ink)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Hand.label, color = if (selected) c.paper else c.ink, maxLines = 1, softWrap = false)
    }
}

/** Numbered steps, so a flow says up front what will happen and in what order. */
@Composable
fun Steps(steps: List<String>, modifier: Modifier = Modifier, current: Int = -1) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val on = i == current
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .then(if (on) Modifier.background(burrow.ink) else Modifier.border(1.4.dp, burrow.ink, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = Hand.small.copy(fontSize = 15.sp), color = if (on) burrow.paper else burrow.ink) }
                Spacer(Modifier.width(10.dp))
                Text(step, style = bodyStyle(14f, 19f), color = if (current < 0 || on) burrow.ink else burrow.pencil)
            }
        }
    }
}

/** A numbered section head for step-by-step pages ("1  paste it"). */
@Composable
fun StepHead(number: Int, text: String, modifier: Modifier = Modifier, done: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (done) burrow.ink else burrow.paper).border(1.6.dp, burrow.ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) DoodleIcon(Doodle.TICK, null, size = 18.dp, color = burrow.paper, stroke = 2.4.dp)
            else Text("$number", style = Hand.label, color = burrow.ink)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = Hand.title.copy(fontSize = 26.sp), color = burrow.ink)
    }
}

/**
 * The state of a photo you can pick, drawn on the photo itself: a filled tick
 * when it's chosen, an empty ring when it isn't. The label underneath says what
 * the choice means ("let go", "keep", "to the vault").
 */
@Composable
fun PickMark(picked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (picked) burrow.ink else burrow.paper.copy(alpha = 0.85f))
            .border(1.8.dp, if (picked) burrow.paper else burrow.ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (picked) DoodleIcon(Doodle.TICK, null, size = 18.dp, color = burrow.paper, stroke = 2.6.dp) }
}

/** A quiet confirmation strip after something happened, with what to do next. */
@Composable
fun DoneNote(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(burrow.smudge).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DoodleIcon(Doodle.TICK, null, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), style = Hand.label, color = burrow.ink)
        if (action != null && onAction != null) HandLink(action, onAction, style = Hand.small)
    }
}
