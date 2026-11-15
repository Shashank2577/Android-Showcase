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
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
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
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.InitialCircle
import com.showcase.desertant.ui.hand.ItemRow
import com.showcase.desertant.ui.hand.ListPane
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandEmpty
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.SwitchRow
import com.showcase.desertant.ui.meetings.AssignSpeakerDialog
import com.showcase.desertant.ui.meetings.decodeTopics
import com.showcase.desertant.ui.shell.LocalWideLayout
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.hand.SheetTitle
import com.showcase.desertant.ui.theme.Type
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
        HandPage(
            topEnd = {
                HandLink("add someone", { adding = true })
                DoodleButton(Doodle.GEAR, "Settings", { open("settings") })
                DoodleButton(Doodle.LOCK, "Privacy", { open("privacy") })
            },
        ) {
            PageIntro(
                "people",
                if (learnVoices) "Everyone you've named in a talk, and what you talk about."
                else "Everyone you've named in a talk. Voice learning is off, so Burrow won't suggest people in new talks.",
            )
            if (unnamed.isNotEmpty()) {
                val count = unnamed.size.toString()
                SectionHead("voices to name · $count", mark = count, first = true)
                unnamed.take(6).forEachIndexed { n, s ->
                    if (n > 0) HandDivider(seed = n + 3)
                    UnnamedVoice(s, ready[s.meetingId], profiles.orEmpty().firstOrNull { it.id == s.suggestedProfileId }) { assigning = s }
                }
                SectionHead("your people")
            }
            val p = profiles
            if (p != null && p.isEmpty()) {
                HandEmpty(
                    "know who said what.",
                    "Name the voices in a talk and Burrow keeps a page for each person, only on this phone.",
                    doodle = Doodle.PERSON,
                )
            }
            p.orEmpty().forEachIndexed { n, person ->
                if (n > 0) HandDivider(seed = n)
                PersonRow(person, speakers.filter { it.profileId == person.id }, ready, selected = wide && (selected ?: p?.firstOrNull()?.id) == person.id) {
                    if (wide) selected = person.id else open("person/${person.id}")
                }
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(ListPane).fillMaxHeight()) { list() }
            Box(Modifier.weight(1f).fillMaxHeight()) {
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
    ItemRow(
        s.sampleQuote?.let { "“$it”" } ?: "A voice in ${meeting?.title ?: "a talk"}",
        titleStyle = Type.body,
        meta = "${meeting?.title ?: "A talk"} · ${formatClock(s.talkSec)} of talking",
        metaLines = 1,
        leading = { DoodleIcon(Doodle.WAVE, null, size = 24.dp, color = burrow.pencil) },
        trailing = { HandLink(suggestion?.let { "${it.name.substringBefore(' ')}?" } ?: "name them", onClick) },
    )
}

@Composable
private fun PersonRow(p: ProfileEntity, appearances: List<MeetingSpeakerEntity>, meetings: Map<Long, MeetingEntity>, selected: Boolean, onClick: () -> Unit) {
    val talks = appearances.map { it.meetingId }.distinct().size
    val last = appearances.mapNotNull { meetings[it.meetingId]?.createdAt }.maxOrNull()
    ItemRow(
        if (p.isMe) "${p.name} (you)" else p.name,
        meta = listOfNotNull(if (talks == 1) "1 talk" else "$talks talks", last?.let { relativeDay(it).lowercase() }).joinToString(" · "),
        onClick = onClick,
        selected = selected,
        leading = { InitialCircle(p.name) },
    )
}

@Composable
fun AddPersonDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var me by remember { mutableStateOf(false) }
    HandSheet(onDismiss) {
        SheetTitle("add someone")
        Body("Usually you name people straight from a talk's notes. Add someone here to teach Burrow their voice first, with a short sample on their page.")
        HandField(name, { name = it }, placeholder = "their name", singleLine = true)
        SwitchRow("this is me", me, { me = it })
        InkButton("add", { scope.launch { People(context).create(name, me); onDismiss() } }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
    }
}
