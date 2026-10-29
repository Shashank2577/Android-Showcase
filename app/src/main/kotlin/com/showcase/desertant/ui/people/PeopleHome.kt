package com.showcase.desertant.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.showcase.desertant.app
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.people.People
import com.showcase.desertant.ui.common.relativeDay
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandEmpty
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.handUnderline
import com.showcase.desertant.ui.meetings.AssignSpeakerDialog
import com.showcase.desertant.ui.meetings.decodeTopics
import com.showcase.desertant.ui.shell.LocalWideLayout
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

@Composable
fun PeopleHome(open: (String) -> Unit) {
    val context = LocalContext.current
    val db = context.app.db
    val profiles by remember { db.profiles().all() }.collectAsState(initial = null)
    val speakers by remember { db.speakers().all() }.collectAsState(initial = emptyList())
    val meetings by remember { db.meetings().all() }.collectAsState(initial = emptyList())
    val learnVoices by context.app.privacy.learnVoices.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf<MeetingSpeakerEntity?>(null) }
    val wide = LocalWideLayout.current
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    val ready = meetings.filter { it.status == MeetingStatus.READY }.associateBy { it.id }
    val unnamed = speakers.filter { it.profileId == null && it.meetingId in ready && it.words >= 5 }
        .sortedByDescending { ready[it.meetingId]?.createdAt ?: 0 }

    val list = @Composable {
        HandPage(topEnd = {
            DoodleButton(Doodle.PLUS, "Add person", { adding = true })
            DoodleButton(Doodle.LOCK, "Privacy", { open("privacy") })
        }) {
            MarkedText("people", mark = "people", style = Hand.headline)
            Meta(if (learnVoices) "voices are learned only when you name someone" else "voice learning is off", Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(20.dp))
            if (unnamed.isNotEmpty()) {
                SectionHead("voices to name — ${unnamed.size}", note = "needs you")
                Spacer(Modifier.height(4.dp))
                unnamed.take(6).forEachIndexed { n, s ->
                    if (n > 0) HandDivider(seed = n + 3)
                    UnnamedVoice(s, ready[s.meetingId], profiles.orEmpty().firstOrNull { it.id == s.suggestedProfileId }) { assigning = s }
                }
                Spacer(Modifier.height(22.dp))
            }
            val p = profiles
            SectionHead("your people", note = p?.takeIf { it.isNotEmpty() }?.let { "${it.size} profiles" })
            if (p != null && p.isEmpty()) {
                HandEmpty(
                    "know who said what.",
                    "Name the voices in a talk and Burrow keeps a page for each person: what you talk about, open follow-ups, every conversation — only on this phone.",
                    doodle = Doodle.PERSON,
                )
            }
            p.orEmpty().forEachIndexed { n, person ->
                if (n > 0) HandDivider(seed = n)
                PersonRow(person, speakers.filter { it.profileId == person.id }, ready, selected = wide && selected == person.id) {
                    if (wide) selected = person.id else open("person/${person.id}")
                }
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f).fillMaxHeight()) { list() }
            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                val id = selected ?: profiles?.firstOrNull()?.id
                if (id != null) PersonContent(id, open = open, embedded = true)
                else HandEmpty("no one yet", "People you name in talks show up here.", Modifier.padding(top = 80.dp))
            }
        }
    } else list()
    if (adding) AddPersonDialog { adding = false }
    assigning?.let { s -> AssignSpeakerDialog(s, profiles.orEmpty()) { assigning = null } }
}

@Composable
private fun UnnamedVoice(s: MeetingSpeakerEntity, meeting: MeetingEntity?, suggestion: ProfileEntity?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DoodleIcon(Doodle.WAVE, null, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            s.sampleQuote?.let { Body("“$it”", color = burrow.ink) }
            Meta("${meeting?.title ?: "a talk"} · ${formatClock(s.talkSec)} of talking", maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Text(suggestion?.let { "${it.name.substringBefore(' ')}?" } ?: "who is this?", Modifier.handUnderline(burrow.ink), style = Hand.small, color = burrow.ink)
    }
}

@Composable
private fun PersonRow(p: ProfileEntity, appearances: List<MeetingSpeakerEntity>, meetings: Map<Long, MeetingEntity>, selected: Boolean, onClick: () -> Unit) {
    val talks = appearances.map { it.meetingId }.distinct().size
    val last = appearances.mapNotNull { meetings[it.meetingId]?.createdAt }.maxOrNull()
    val topics = appearances.flatMap { decodeTopics(it.topicsJson) }.groupBy { it.name }
        .entries.sortedByDescending { e -> e.value.sumOf { it.score } }.take(3).map { it.key.lowercase() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(burrow.smudge) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).border(1.8.dp, burrow.ink, CircleShape), contentAlignment = Alignment.Center) {
            Text(p.name.trim().take(1).uppercase().ifEmpty { "?" }, style = Hand.title, color = burrow.ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (p.isMe) "${p.name} (you)" else p.name, style = Hand.door, color = burrow.ink)
            Meta(
                listOfNotNull(
                    if (talks == 1) "1 talk" else "$talks talks",
                    topics.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    last?.let { relativeDay(it).lowercase() },
                ).joinToString(" · "),
                maxLines = 1,
            )
        }
        DoodleIcon(Doodle.ARROW, null, size = 22.dp, color = burrow.pencil)
    }
}

@Composable
fun AddPersonDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var me by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a person") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(me, { me = it })
                    Text("This is me")
                }
                Text("Open their page afterwards to teach Burrow their voice with a short sample.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { scope.launch { People(context).create(name, me); onDismiss() } }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
