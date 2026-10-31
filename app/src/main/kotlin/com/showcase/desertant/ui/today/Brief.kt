package com.showcase.desertant.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.showcase.desertant.app
import com.showcase.desertant.ask.TalkSummaries
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSource
import com.showcase.desertant.data.SummaryTodo
import com.showcase.desertant.people.Contacts
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.meetings.TodoLine
import com.showcase.desertant.ui.meetings.dueDate
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.bodyStyle
import com.showcase.desertant.ui.theme.burrow
import java.time.LocalDate
import java.time.ZoneId

/** A to-do from some talk's summary, ready for the brief. */
data class BriefTodo(val talk: MeetingEntity, val index: Int, val todo: SummaryTodo, val due: LocalDate?)

/** What the brief shows; worked out from stored summaries, no model needed. */
data class BriefData(val recent: List<MeetingEntity>, val dueNow: List<BriefTodo>, val thisWeek: List<BriefTodo>) {
    val isEmpty get() = recent.isEmpty() && dueNow.isEmpty() && thisWeek.isEmpty()

    companion object {
        fun build(talks: List<MeetingEntity>, done: Set<String>, me: String?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): BriefData {
            val since = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val recent = talks.filter { it.createdAt >= since }.sortedByDescending { it.createdAt }.take(4)
            val myName = me?.let(Contacts::plain)?.substringBefore(' ')
            val open = talks.flatMap { m ->
                TalkSummaries.decode(m.summaryJson)?.todos.orEmpty().mapIndexedNotNull { i, t ->
                    if ("${m.id}/$i" in done) return@mapIndexedNotNull null
                    val mine = myName != null && t.who?.let(Contacts::plain)?.substringBefore(' ') == myName
                    BriefTodo(m, i, if (mine) t.copy(who = "you") else t, dueDate(t))
                }
            }
            val dueNow = open.filter { it.due != null && !it.due.isAfter(today.plusDays(1)) && !it.due.isBefore(today.minusDays(14)) }.sortedBy { it.due }
            val week = open.filter { it.due != null && it.due.isAfter(today.plusDays(1)) && !it.due.isAfter(today.plusDays(7)) }.sortedBy { it.due }
            return BriefData(recent, dueNow, week)
        }
    }
}

/** "your brief": yesterday's and today's talks in a line each, and what's due. Shown only when there's something. */
@Composable
fun Brief(open: (String) -> Unit) {
    val app = LocalContext.current.app
    val talks by remember { app.db.meetings().summarized(80) }.collectAsState(initial = emptyList())
    val recentReady by remember { app.db.meetings().readySince(System.currentTimeMillis() - 2 * 86_400_000L) }.collectAsState(initial = emptyList())
    val profiles by remember { app.db.profiles().all() }.collectAsState(initial = emptyList())
    val done by app.summaries.done.collectAsState()
    val me = profiles.firstOrNull { it.isMe }?.name
    val data = remember(talks, recentReady, done, me) { BriefData.build((talks + recentReady).distinctBy { it.id }, done, me, LocalDate.now()) }
    BriefContent(data, onTick = { t -> app.summaries.toggleDone(t.talk.id, t.index) }, open = open)
}

@Composable
fun BriefContent(data: BriefData, onTick: (BriefTodo) -> Unit, open: (String) -> Unit) {
    if (data.isEmpty) return
    SectionHead("your brief", note = "from your talks")
    if (data.recent.isNotEmpty()) {
        Text("since yesterday", Modifier.padding(top = 6.dp), style = Hand.small, color = burrow.pencil)
        data.recent.forEach { m -> RecentTalk(m) { open("meeting/${m.id}") } }
    }
    if (data.dueNow.isNotEmpty()) {
        Text("due now", Modifier.padding(top = 10.dp), style = Hand.small, color = burrow.pencil)
        data.dueNow.forEach { b -> TodoLine(b.todo, ticked = false, onTick = { onTick(b) }, onSeek = { open("meeting/${b.talk.id}") }, context = b.talk.title) }
    }
    if (data.thisWeek.isNotEmpty()) {
        Text("this week", Modifier.padding(top = 10.dp), style = Hand.small, color = burrow.pencil)
        data.thisWeek.take(4).forEach { b -> TodoLine(b.todo, ticked = false, onTick = { onTick(b) }, onSeek = { open("meeting/${b.talk.id}") }, context = b.talk.title) }
        if (data.thisWeek.size > 4) Meta("and ${data.thisWeek.size - 4} more")
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RecentTalk(m: MeetingEntity, onClick: () -> Unit) {
    val s = remember(m.summaryJson) { TalkSummaries.decode(m.summaryJson) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DoodleIcon(if (m.source == MeetingSource.CALL) Doodle.PHONE else Doodle.MIC, null, Modifier.padding(top = 3.dp), size = 20.dp, color = burrow.pencil)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = Hand.label, color = burrow.ink, maxLines = 1)
            if (s != null) {
                Text(firstSentence(s.gist), style = bodyStyle(14f, 19f), color = burrow.ink)
                val bits = listOfNotNull(
                    s.decisions.size.takeIf { it > 0 }?.let { if (it == 1) "1 decision" else "$it decisions" },
                    s.todos.size.takeIf { it > 0 }?.let { if (it == 1) "1 to-do" else "$it to-dos" },
                )
                if (bits.isNotEmpty()) Meta(bits.joinToString(" · "))
            } else {
                Meta("no summary yet — open it to write one")
            }
        }
        DoodleIcon(Doodle.ARROW, null, Modifier.padding(top = 3.dp), size = 18.dp, color = burrow.pencil)
    }
}

/** The first sentence, for a one-line recap (works for "." and "।"). */
fun firstSentence(text: String): String {
    val end = Regex("[.!?।](\\s|$)").find(text)?.range?.first ?: return text
    return text.substring(0, end + 1)
}
