@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.meetings

import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.showcase.desertant.data.Moment
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.Prose
import com.showcase.desertant.ui.hand.Warning
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
import com.showcase.desertant.ui.theme.Type
import com.showcase.desertant.ui.hand.SheetTitle
import com.showcase.desertant.ui.theme.burrow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The summary view of a talk: the gist as prose on the paper (no box), then
 * what was decided and the key points, each with the moment it came from.
 * Without a summary yet: the way to get one, and the highlights Gist found.
 */
@Composable
fun SummarySection(m: MeetingEntity, onSeek: (Double) -> Unit, open: (String) -> Unit, highlights: List<Moment> = emptyList()) {
    val context = LocalContext.current
    val running by TalkSummaries.running.collectAsState()
    val failures by TalkSummaries.failures.collectAsState()
    val model by AskModel.state.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    val summary = remember(m.summaryJson) { TalkSummaries.decode(m.summaryJson) }
    val progress = running[m.id]

    when {
        progress != null -> {
            Caption(progress)
            HandProgress(null, Modifier.padding(vertical = 6.dp))
            Meta("Being written on this phone. It keeps going if you leave; long talks take a minute or two.")
        }
        summary != null -> SummaryBody(summary, onSeek, onAgain = { choosing = true })
        !AskModel.isReady(context) -> {
            Body("Burrow can write a summary of this talk, with what was decided and who does what, on this phone.")
            HandLink(
                if (model is AskModel.State.Downloading) "the model is downloading…" else "get summaries (${"%.1f".format(AskModel.SIZE_MB / 1000.0)} GB, once)",
                { open("models") },
            )
        }
        else -> {
            failures[m.id]?.let { Warning("The last try didn't work: $it") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton("summarise", { SummaryWorker.enqueue(context, m.id) }, leading = Doodle.SPARK)
                Spacer(Modifier.width(12.dp))
                HandLink("in another language", { choosing = true })
            }
        }
    }
    if (summary == null && highlights.isNotEmpty()) {
        SectionHead("worth remembering")
        highlights.forEach { mo -> PointLine(SummaryPoint(mo.text, mo.start), Doodle.ARROW, onSeek) }
    }
    if (choosing) {
        LanguageSheet(current = summary?.language, talk = SummarySettings.talkLanguage(m), onDismiss = { choosing = false }) { lang ->
            choosing = false
            SummaryWorker.enqueue(context, m.id, lang)
        }
    }
}

@Composable
private fun SummaryBody(s: TalkSummary, onSeek: (Double) -> Unit, onAgain: () -> Unit) {
    Prose(s.gist)
    if (s.decisions.isNotEmpty()) {
        SectionHead("decided")
        s.decisions.forEach { PointLine(it, Doodle.TICK, onSeek) }
    }
    if (s.points.isNotEmpty()) {
        SectionHead("key points")
        s.points.forEach { PointLine(it, Doodle.ARROW, onSeek) }
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.Top) {
        DoodleIcon(Doodle.CLOUD_OFF, null, Modifier.padding(top = 1.dp), size = 16.dp, color = burrow.pencil)
        Spacer(Modifier.width(6.dp))
        Caption("Written on this phone by ${s.model}, in ${s.language}. It can be wrong: tap a time to hear what was said.", Modifier.weight(1f))
    }
    HandLink("summarise again", onAgain)
}

/**
 * The to-dos view: the summary's to-dos, ticked off and remembered; or, before
 * there is a summary, the follow-ups Burrow found by their phrasing.
 */
@Composable
fun TodosSection(m: MeetingEntity, followUps: List<Moment>, speakerName: (Int?) -> String, onSeek: (Double) -> Unit) {
    val app = LocalContext.current.app
    val done by app.summaries.done.collectAsState()
    val summary = remember(m.summaryJson) { TalkSummaries.decode(m.summaryJson) }
    val local = remember { mutableStateListOf<Int>() }
    when {
        summary != null && summary.todos.isNotEmpty() ->
            summary.todos.forEachIndexed { i, t -> TodoLine(t, ticked = "${m.id}/$i" in done, onTick = { app.summaries.toggleDone(m.id, i) }, onSeek = onSeek) }
        summary == null && followUps.isNotEmpty() -> {
            Meta("Found by their wording (\u201cI'll send…\u201d, \u201cwe need to…\u201d). A summary lists them with dates.")
            Spacer(Modifier.height(4.dp))
            followUps.forEachIndexed { i, mo ->
                TodoLine(
                    SummaryTodo(what = mo.text, who = speakerName(mo.speaker), at = mo.start),
                    ticked = i in local,
                    onTick = { if (i in local) local.remove(i) else local.add(i) },
                    onSeek = onSeek,
                )
            }
        }
        else -> Body("No to-dos in this talk.", color = burrow.pencil)
    }
}

@Composable
private fun PointLine(p: SummaryPoint, doodle: Doodle, onSeek: (Double) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (p.at != null) Modifier.clickable { onSeek(p.at) } else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DoodleIcon(doodle, null, Modifier.padding(top = 4.dp), size = 16.dp, color = burrow.pencil)
        Spacer(Modifier.width(10.dp))
        Text(p.text, Modifier.weight(1f), style = Type.body, color = burrow.ink)
        p.at?.let { Caption(formatClock(it), Modifier.padding(start = 10.dp, top = 3.dp)) }
    }
}

/**
 * A to-do: a box to tick (a full 48 dp target), the to-do in ink, and who and
 * when underneath. [onOpen] opens its talk; [onSeek] plays the moment it came from.
 */
@Composable
fun TodoLine(
    t: SummaryTodo,
    ticked: Boolean,
    onTick: () -> Unit,
    onSeek: ((Double) -> Unit)? = null,
    context: String? = null,
    onOpen: (() -> Unit)? = null,
    showTime: Boolean = true,
) {
    val overdue = !ticked && isOverdue(t)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).toggleable(ticked, role = Role.Checkbox, onValueChange = { onTick() }),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp).border(1.6.dp, burrow.ink, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                if (ticked) DoodleIcon(Doodle.TICK, "done", size = 22.dp, stroke = 2.4.dp)
            }
        }
        Spacer(Modifier.width(4.dp))
        val open = onOpen ?: if (onSeek != null && t.at != null) ({ onSeek(t.at) }) else null
        Column(
            Modifier
                .weight(1f)
                .then(if (open != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = open) else Modifier)
                .padding(vertical = 12.dp),
        ) {
            Text(
                t.what,
                style = Type.body.copy(textDecoration = if (ticked) TextDecoration.LineThrough else null),
                color = if (ticked) burrow.pencil else burrow.ink,
            )
            val meta = listOfNotNull(t.who?.takeIf { it.isNotBlank() }, dueLabel(t), context, t.at?.takeIf { showTime }?.let(::formatClock)).joinToString(" · ")
            if (meta.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                if (overdue) {
                    DoodleIcon(Doodle.EXCLAIM, "overdue", size = 14.dp, color = burrow.redPen, stroke = 2.4.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Meta(meta)
            }
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
        SheetTitle("write it in…")
        Body(if (offerSameAsTalk) "Summaries are written in this language, whatever language the talk was in." else "Any of these, whatever language the talk was in. Change the usual one in settings → summaries.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (offerSameAsTalk) ChoicePill("each talk's own language", current == SummarySettings.SAME_AS_TALK, { onPick(SummarySettings.SAME_AS_TALK) })
            if (talk != null) ChoicePill("the talk's own ($talk)", current == talk, { onPick(talk) })
            SummarySettings.LANGUAGES.forEach { l ->
                ChoicePill(if (l.native == l.english) l.english else "${l.native} · ${l.english}", current == l.english, { onPick(l.english) })
            }
        }
    }
}
