@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.meetings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.app
import com.showcase.desertant.ask.AskModel
import com.showcase.desertant.ask.SummarySettings
import com.showcase.desertant.ask.TalkSummaries
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.SummaryPoint
import com.showcase.desertant.data.SummaryTodo
import com.showcase.desertant.data.TalkSummary
import com.showcase.desertant.meetings.SummaryWorker
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.ChoicePill
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.bodyStyle
import com.showcase.desertant.ui.theme.burrow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A talk's summary, or the way to get one. Every line plays the moment it came from. */
@Composable
fun SummarySection(m: MeetingEntity, onSeek: (Double) -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val running by TalkSummaries.running.collectAsState()
    val failures by TalkSummaries.failures.collectAsState()
    val model by AskModel.state.collectAsState()
    val done by app.summaries.done.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    val summary = remember(m.summaryJson) { TalkSummaries.decode(m.summaryJson) }
    val progress = running[m.id]

    when {
        progress != null -> {
            SectionHead("summary", note = "on this phone")
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                HandProgress(null, Modifier.width(56.dp))
                Spacer(Modifier.width(12.dp))
                Meta(progress)
            }
            Meta("Keeps going if you leave. Long talks take a minute or two.")
        }
        summary != null -> SummaryBody(m.id, summary, done, onSeek, onTick = { i -> app.summaries.toggleDone(m.id, i) }, onAgain = { choosing = true })
        !AskModel.isReady(context) -> {
            SectionHead("summary")
            Meta("Burrow can write a summary of this talk — what was decided and who does what — on this phone, with the ask model.")
            Spacer(Modifier.height(6.dp))
            HandLink(
                if (model is AskModel.State.Downloading) "the model is downloading…" else "get summaries (${"%.1f".format(AskModel.SIZE_MB / 1000.0)} GB, once)",
                { open("models") }, style = Hand.small,
            )
        }
        else -> {
            SectionHead("summary")
            failures[m.id]?.let { Meta("Last try didn't work: $it", color = burrow.redPen) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton("summarise", { SummaryWorker.enqueue(context, m.id) }, leading = Doodle.SPARK)
                Spacer(Modifier.width(12.dp))
                HandLink("in another language", { choosing = true }, style = Hand.small)
            }
        }
    }
    if (choosing) {
        LanguageSheet(current = summary?.language, talk = SummarySettings.talkLanguage(m), onDismiss = { choosing = false }) { lang ->
            choosing = false
            SummaryWorker.enqueue(context, m.id, lang)
        }
    }
}

@Composable
private fun SummaryBody(id: Long, s: TalkSummary, done: Set<String>, onSeek: (Double) -> Unit, onTick: (Int) -> Unit, onAgain: () -> Unit) {
    SectionHead("summary", note = s.language.lowercase())
    Column(Modifier.fillMaxWidth().border(1.8.dp, burrow.ink, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Body(s.gist)
        if (s.decisions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("decided", style = Hand.title.copy(fontSize = 24.sp), color = burrow.ink)
            s.decisions.forEach { PointLine(it, Doodle.TICK, onSeek) }
        }
        if (s.points.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("key points", style = Hand.title.copy(fontSize = 24.sp), color = burrow.ink)
            s.points.forEach { PointLine(it, Doodle.ARROW, onSeek) }
        }
    }
    if (s.todos.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SectionHead("to do", note = "tap the box when it's done")
        s.todos.forEachIndexed { i, t -> TodoLine(t, ticked = "$id/$i" in done, onTick = { onTick(i) }, onSeek = onSeek) }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        DoodleIcon(Doodle.CLOUD_OFF, null, size = 16.dp, color = burrow.pencil)
        Spacer(Modifier.width(6.dp))
        Meta("Written on this phone by ${s.model}. It can be wrong: tap a line to hear what was said.", Modifier.weight(1f))
    }
    HandLink("summarise again, or in another language", onAgain, style = Hand.small)
}

@Composable
private fun PointLine(p: SummaryPoint, doodle: Doodle, onSeek: (Double) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (p.at != null) Modifier.clickable { onSeek(p.at) } else Modifier).padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DoodleIcon(doodle, null, Modifier.padding(top = 3.dp), size = 16.dp, color = burrow.pencil)
        Spacer(Modifier.width(8.dp))
        Text(p.text, Modifier.weight(1f), style = bodyStyle(15f, 21f), color = burrow.ink)
        p.at?.let { Text(formatClock(it), Modifier.padding(start = 8.dp, top = 2.dp), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = burrow.pencil) }
    }
}

@Composable
fun TodoLine(t: SummaryTodo, ticked: Boolean, onTick: () -> Unit, onSeek: ((Double) -> Unit)? = null, context: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).clickable(onClick = onTick).border(1.6.dp, burrow.ink, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) { if (ticked) DoodleIcon(Doodle.TICK, "done", size = 22.dp, stroke = 2.4.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).then(if (onSeek != null && t.at != null) Modifier.clickable { onSeek(t.at) } else Modifier)) {
            Text(
                t.what,
                style = Hand.label.copy(textDecoration = if (ticked) TextDecoration.LineThrough else null),
                color = if (ticked) burrow.graphite else burrow.ink,
            )
            Meta(
                listOfNotNull(t.who?.takeIf { it.isNotBlank() }, dueLabel(t), context, t.at?.let(::formatClock)).joinToString(" · "),
                color = if (!ticked && isOverdue(t)) burrow.redPen else burrow.pencil,
            )
        }
    }
}

fun dueDate(t: SummaryTodo): LocalDate? = t.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun isOverdue(t: SummaryTodo, today: LocalDate = LocalDate.now()) = dueDate(t)?.isBefore(today) == true

/** "today", "tomorrow", "Tue 3 Nov", or the talk's own words when Burrow couldn't place them. */
fun dueLabel(t: SummaryTodo, today: LocalDate = LocalDate.now()): String? {
    val d = dueDate(t) ?: return t.dueWords?.takeIf { it.isNotBlank() }?.let { "“$it”" }
    return when {
        d == today -> "due today"
        d == today.plusDays(1) -> "due tomorrow"
        d.isBefore(today) -> "was due ${d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))}"
        else -> "due ${d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))}"
    }
}

/** Which language to write a summary in. */
@Composable
fun LanguageSheet(current: String?, talk: String?, onDismiss: () -> Unit, offerSameAsTalk: Boolean = false, onPick: (String) -> Unit) {
    HandSheet(onDismiss) {
        Text("write it in…", style = Hand.headline.copy(fontSize = 32.sp), color = burrow.ink)
        Meta(if (offerSameAsTalk) "Summaries are written in this language, whatever language the talk was in." else "Any of these, whatever language the talk was in. Change the usual one in settings → summaries.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (offerSameAsTalk) ChoicePill("each talk's own language", current == SummarySettings.SAME_AS_TALK, { onPick(SummarySettings.SAME_AS_TALK) })
            if (talk != null) ChoicePill("the talk's own ($talk)", current == talk, { onPick(talk) })
            SummarySettings.LANGUAGES.forEach { l ->
                ChoicePill(if (l.native == l.english) l.english else "${l.native} · ${l.english}", current == l.english, { onPick(l.english) })
            }
        }
    }
}
