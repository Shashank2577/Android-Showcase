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
import androidx.compose.foundation.layout.widthIn
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
import com.showcase.desertant.core.languageName
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSource
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.meetings.MeetingImporter
import com.showcase.desertant.meetings.RecorderService
import com.showcase.desertant.meetings.RecorderState
import com.showcase.desertant.speech.SpeechPacks
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.ItemRow
import com.showcase.desertant.ui.hand.ListPane
import com.showcase.desertant.ui.hand.SheetTitle
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandEmpty
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.shell.LocalWideLayout
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

/**
 * Talks. Its job: find a talk, or start one. One sentence, search across
 * everything said, the list newest first; "import" in the top row opens a
 * sheet (a file, a call, a chat); the one ink pill records.
 */
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
    var importing by remember { mutableStateOf(false) }

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
    val recording = recorder as? RecorderState.Recording

    val list = @Composable {
        HandPage(
            topEnd = {
                HandLink("import", { importing = true })
                DoodleButton(Doodle.GEAR, "Settings", { open("settings") })
                DoodleButton(Doodle.LOCK, "Privacy", { open("privacy") })
            },
            bottom = {
                InkButton(
                    if (recording != null) "recording · ${formatClock(recording.elapsedMs / 1000.0)}" else "record",
                    { open("record") },
                    Modifier.widthIn(max = if (wide) 320.dp else 400.dp).fillMaxWidth(),
                    trailing = if (recording != null) Doodle.WAVE else Doodle.MIC,
                )
            },
        ) {
            PageIntro("talks", "Everything you recorded, with notes.")
            if (meetings.orEmpty().isNotEmpty() || query.isNotBlank()) {
                HandField(query, { query = it }, placeholder = "search what was said", singleLine = true, leading = Doodle.SEARCH)
                Spacer(Modifier.height(8.dp))
            }
            val m = meetings
            if (m != null && m.isEmpty()) {
                if (query.isBlank()) HandEmpty(
                    "your talks, understood.",
                    "Record a meeting or bring in a call. Burrow writes the notes, works out who said what and pulls out the to-dos, on this phone.",
                    doodle = Doodle.MIC,
                ) else HandEmpty("nothing found", "No talk mentions “$query”. Search looks through every transcript on this phone.", doodle = Doodle.SEARCH)
            }
            m.orEmpty().forEachIndexed { n, meeting ->
                if (n > 0) HandDivider(seed = n)
                TalkRow(
                    meeting,
                    people = speakers.filter { it.meetingId == meeting.id }.mapNotNull { s -> profiles.firstOrNull { it.id == s.profileId } }.distinctBy { it.id },
                    highlight = query.takeIf { it.isNotBlank() },
                    selected = wide && (selected ?: meetings?.firstOrNull()?.id) == meeting.id,
                ) { if (wide) selected = meeting.id else open("meeting/${meeting.id}") }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(ListPane).fillMaxHeight()) { list() }
            Box(Modifier.weight(1f).fillMaxHeight().background(burrow.paper)) {
                val id = selected ?: meetings?.firstOrNull()?.id
                if (id != null) MeetingDetailContent(id, open = open, embedded = true)
                else HandEmpty("pick a talk", "Its notes open here.", Modifier.padding(top = 80.dp))
            }
        }
    } else {
        list()
    }

    if (importing) {
        HandSheet({ importing = false }) {
            SheetTitle("bring a talk in")
            ItemRow(
                "A recording file", meta = "Any audio or video file on this phone.",
                onClick = { importing = false; picker.launch(arrayOf("audio/*", "video/*")) },
                leading = { DoodleIcon(Doodle.PLUS, null, size = 24.dp) },
            )
            ItemRow(
                "A phone call", meta = "Recordings your phone app made. Burrow finds them for you.",
                onClick = { importing = false; open("recordings") },
                leading = { DoodleIcon(Doodle.PHONE, null, size = 24.dp) },
            )
            ItemRow(
                "A chat export", meta = "In WhatsApp, open a chat, then ⋮ → More → Export chat → Burrow. It lands on that person's page.",
                leading = { DoodleIcon(Doodle.BUBBLE, null, size = 24.dp) },
                metaLines = 3,
            )
            Caption("You can also share any recording to Burrow from another app.")
        }
    }
}

@Composable
private fun TalkRow(meeting: MeetingEntity, people: List<ProfileEntity>, highlight: String?, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val topics = remember(meeting.topicsJson) { decodeTopics(meeting.topicsJson) }
    val writing = meeting.status == MeetingStatus.QUEUED || meeting.status == MeetingStatus.PROCESSING || meeting.status == MeetingStatus.RECORDING
    val meta = buildString {
        append(talkDate(context, meeting.createdAt))
        if (meeting.durationSec > 0) append(" · ${(meeting.durationSec / 60).toInt().coerceAtLeast(1)} min")
        val names = people.map { if (it.isMe) "you" else it.name.substringBefore(' ') }
        if (names.isNotEmpty()) append(" · ${names.joinToString(", ")}")
        else if (meeting.speakerCount > 0) append(" · ${meeting.speakerCount} voices")
    }
    val caption = when (meeting.status) {
        MeetingStatus.READY -> highlight?.let { h -> snippet(meeting.transcriptText, h)?.let { "…$it…" } }
            ?: topics.take(3).takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.name.lowercase() }
        MeetingStatus.FAILED -> SpeechPacks.route(meeting.language)?.takeIf { meeting.languageSupported == false }?.let { _ ->
            val name = languageName(meeting.language).substringBefore(" (")
            "In $name. Open it to get $name and write its notes."
        } ?: "Couldn't finish. Open it to try again."
        MeetingStatus.RECORDING -> "Recording…"
        else -> meeting.progress ?: "Waiting to be written up"
    }
    ItemRow(
        meeting.title,
        meta = meta,
        caption = caption,
        onClick = onClick,
        selected = selected,
        leading = if (meeting.source == MeetingSource.CALL) ({ DoodleIcon(Doodle.PHONE, "Call", size = 24.dp, color = burrow.pencil) }) else null,
        below = if (writing) ({ HandProgress(null, Modifier.padding(top = 6.dp)) }) else null,
    )
}

private fun snippet(text: String, query: String): String? {
    val at = text.indexOf(query, ignoreCase = true)
    if (at < 0) return null
    return text.substring((at - 40).coerceAtLeast(0), (at + query.length + 60).coerceAtMost(text.length))
}
