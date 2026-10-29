package com.showcase.desertant.ui.meetings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.showcase.desertant.app
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSource
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.meetings.MeetingImporter
import com.showcase.desertant.meetings.RecorderService
import com.showcase.desertant.meetings.RecorderState
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandEmpty
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.shell.LocalWideLayout
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

/** Talks: a big record button, search across everything said, and the list. */
@Composable
fun MeetingsHome(open: (String) -> Unit) {
    val context = LocalContext.current
    val db = context.app.db
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val meetings by remember(query) { if (query.isBlank()) db.meetings().all() else db.meetings().search(query.trim()) }
        .collectAsState(initial = null)
    val speakers by remember { db.speakers().all() }.collectAsState(initial = emptyList())
    val profiles by remember { db.profiles().all() }.collectAsState(initial = emptyList())
    val recorder by RecorderService.state.collectAsState()
    val wide = LocalWideLayout.current
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val id = MeetingImporter.import(context, uri, MeetingSource.IMPORTED)
                if (wide) selected = id
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't bring that file in: ${e.message ?: "unreadable"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val list = @Composable {
        HandPage(topEnd = { DoodleButton(Doodle.LOCK, "Privacy", { open("privacy") }) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("talks", style = Hand.headline, color = burrow.ink)
                    Meta("transcribed, sorted and remembered on this phone")
                }
                RecordButton(recorder as? RecorderState.Recording) { open("record") }
            }
            Spacer(Modifier.height(18.dp))
            HandField(query, { query = it }, placeholder = "search every conversation…", singleLine = true, leading = Doodle.SEARCH)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                HandLink("bring in a call recording", { open("recordings") })
                HandLink("a file", { picker.launch(arrayOf("audio/*", "video/*")) })
            }
            Spacer(Modifier.height(12.dp))
            val m = meetings
            if (m != null && m.isEmpty()) {
                if (query.isBlank()) HandEmpty(
                    "your conversations, understood.",
                    "Record a meeting or bring in a call. Burrow writes the notes, works out who said what and pulls out the follow-ups — on the phone.",
                    doodle = Doodle.MIC,
                ) else HandEmpty("no talk mentions “$query”.", "Search looks through every transcript on this phone.", doodle = Doodle.SEARCH)
            }
            m.orEmpty().forEachIndexed { n, meeting ->
                if (n > 0) HandDivider(seed = n)
                TalkRow(
                    meeting,
                    people = speakers.filter { it.meetingId == meeting.id }.mapNotNull { s -> profiles.firstOrNull { it.id == s.profileId } }.distinctBy { it.id },
                    highlight = query.takeIf { it.isNotBlank() },
                    selected = wide && selected == meeting.id,
                ) { if (wide) selected = meeting.id else open("meeting/${meeting.id}") }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f).fillMaxHeight()) { list() }
            Box(Modifier.weight(0.58f).fillMaxHeight().background(burrow.paper)) {
                val id = selected ?: meetings?.firstOrNull()?.id
                if (id != null) MeetingDetailContent(id, open = open, embedded = true)
                else HandEmpty("pick a talk", "Its notes open here.", Modifier.padding(top = 80.dp))
            }
        }
    } else {
        list()
    }
}

@Composable
private fun RecordButton(recording: RecorderState.Recording?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (recording != null) burrow.redPen else burrow.ink)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            DoodleIcon(if (recording != null) Doodle.WAVE else Doodle.MIC, if (recording != null) "Recording" else "Record", size = 34.dp, color = burrow.paper, stroke = 2.4.dp)
        }
        Text(
            if (recording != null) formatClock(recording.elapsedMs / 1000.0) else "record",
            style = Hand.small, color = burrow.ink, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TalkRow(meeting: MeetingEntity, people: List<ProfileEntity>, highlight: String?, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val topics = remember(meeting.topicsJson) { decodeTopics(meeting.topicsJson) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(burrow.smudge) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (meeting.source == MeetingSource.CALL) {
                DoodleIcon(Doodle.PHONE, "Call", size = 18.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(meeting.title, style = Hand.door, color = burrow.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Meta(
            buildString {
                append(dateTime(context, meeting.createdAt))
                if (meeting.durationSec > 0) append(" · ${formatClock(meeting.durationSec)}")
                val names = people.map { if (it.isMe) "you" else it.name.substringBefore(' ') }
                if (names.isNotEmpty()) append(" · ${names.joinToString(", ")}")
                else if (meeting.speakerCount > 0) append(" · ${meeting.speakerCount} voices")
            },
        )
        when (meeting.status) {
            MeetingStatus.READY -> {
                if (topics.isNotEmpty()) {
                    val words = topics.take(3).joinToString("  ·  ") { it.name.lowercase() }
                    MarkedText(words, Modifier.padding(top = 4.dp), mark = topics.first().name.lowercase(), style = Hand.small)
                }
                if (highlight != null) {
                    snippet(meeting.transcriptText, highlight)?.let { Meta("…$it…", Modifier.padding(top = 4.dp), color = burrow.ink, maxLines = 2) }
                }
            }
            MeetingStatus.FAILED -> Meta("couldn't finish — open to try again", Modifier.padding(top = 4.dp), color = burrow.redPen)
            else -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = burrow.ink)
                Spacer(Modifier.width(8.dp))
                Meta(if (meeting.status == MeetingStatus.RECORDING) "recording…" else meeting.progress?.lowercase() ?: "waiting to be written up")
            }
        }
    }
}

private fun snippet(text: String, query: String): String? {
    val at = text.indexOf(query, ignoreCase = true)
    if (at < 0) return null
    return text.substring((at - 40).coerceAtLeast(0), (at + query.length + 60).coerceAtMost(text.length))
}
