

package com.showcase.desertant.ui.people

import ai.desertant.gist.PostTopics
import ai.desertant.gist.RollupOptions
import ai.desertant.gist.channelTopics
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.people.Contacts
import com.showcase.desertant.ui.common.relativeDay
import com.showcase.desertant.ui.hand.DoodleIcon
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.showcase.desertant.app
import com.showcase.desertant.audio.AudioRecorder
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.audio.resample
import com.showcase.desertant.core.languageName
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.people.People
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.ConfirmSheet
import com.showcase.desertant.ui.hand.DoneNote
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.meetings.decodeTopics
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun PersonScreen(id: Long, onBack: () -> Unit, open: (String) -> Unit) = PersonContent(id, open, embedded = false, onBack = onBack)

@Composable
fun PersonContent(id: Long, open: (String) -> Unit, embedded: Boolean, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db = context.app.db
    val scope = rememberCoroutineScope()
    val people = remember { People(context) }
    val person by remember(id) { db.profiles().observe(id) }.collectAsState(initial = null)
    val appearances by remember(id) { db.speakers().forProfile(id) }.collectAsState(initial = emptyList())
    val meetings by remember { db.meetings().all() }.collectAsState(initial = emptyList())
    val calls by remember(id) { db.meetings().callsWith(id) }.collectAsState(initial = emptyList())
    val chatLinks by remember(id) { db.chats().forProfile(id) }.collectAsState(initial = emptyList())
    val chats by remember { db.chats().all() }.collectAsState(initial = emptyList())
    val pick = rememberLauncherForActivityResult(Contacts.PickNumber()) { uri ->
        uri?.let { u -> Contacts.read(context, u)?.let { picked -> scope.launch { people.linkContact(id, picked) } } }
    }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val p = person ?: return
    val byId = meetings.filter { it.status == MeetingStatus.READY }.associateBy { it.id }
    val mine = appearances.filter { it.meetingId in byId }.sortedByDescending { byId[it.meetingId]?.createdAt ?: 0 }
    val totalTalk = mine.sumOf { it.talkSec }
    // Gist's channel roll-up over each conversation's topics, weighted toward recent ones.
    val topics = remember(mine) {
        channelTopics(
            mine.map { s -> PostTopics(decodeTopics(s.topicsJson).associate { it.slug to it.score }, byId[s.meetingId]?.createdAt?.toDouble()) },
            RollupOptions(minPosts = 1, halfLifeDays = 90.0, nowMillis = System.currentTimeMillis().toDouble()),
        )
    }
    val topicNames = remember(mine) { mine.flatMap { decodeTopics(it.topicsJson) }.associate { it.slug to it.name } }
    val languages = mine.mapNotNull { it.language }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    HandPage(
        onBack = if (embedded) null else onBack,
        topEnd = {
            Box {
                PillButton("options ▾", { menu = true }, Modifier.padding(end = 8.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    if (person?.contactUri != null || person?.phones?.isNotEmpty() == true) {
                        DropdownMenuItem(text = { Text("Unlink their contact") }, onClick = { menu = false; scope.launch { people.unlinkContact(id) } })
                    }
                    DropdownMenuItem(text = { Text("Forget this person") }, onClick = { menu = false; deleting = true })
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).border(2.dp, burrow.ink, CircleShape), contentAlignment = Alignment.Center) {
                Text(p.name.trim().take(1).uppercase().ifEmpty { "?" }, style = Hand.headline, color = burrow.ink)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (p.isMe) "${p.name} (you)" else p.name, style = Hand.headline.copy(fontSize = 36.sp), color = burrow.ink)
                Meta(
                    listOfNotNull(
                        mine.map { it.meetingId }.distinct().size.let { if (it == 1) "1 talk" else "$it talks" },
                        calls.size.takeIf { it > 0 }?.let { if (it == 1) "1 call" else "$it calls" },
                        chatLinks.filterNot { it.isMe }.sumOf { it.messages }.takeIf { it > 0 }?.let { "$it messages" },
                        "${formatClock(totalTalk)} talking",
                        languages.takeIf { it.isNotEmpty() }?.joinToString(", ") { languageName(it) },
                    ).joinToString(" · "),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        ContactRow(p, onLink = { pick.launch(Unit) })
        Spacer(Modifier.height(20.dp))
        VoiceSection(p.signatureCount) { samples -> people.enroll(id, samples) }
        if (topics.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHead("what you talk about")
            topics.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((topicNames[t.slug] ?: t.slug).lowercase(), Modifier.width(120.dp), style = Hand.label, color = burrow.ink)
                    HandProgress(t.share.toFloat(), Modifier.weight(1f))
                }
            }
        }
        val quotes = mine.mapNotNull { s -> s.sampleQuote?.let { it to byId[s.meetingId] } }.take(4)
        if (quotes.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHead("in their words")
            quotes.forEach { (q, m) ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Body("“$q”")
                    m?.let { Meta(it.title) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionHead("talks together", note = "tap one to open its notes")
        if (mine.isEmpty()) Meta("Not in any talk yet. Open a talk's notes and tap “name them” next to their voice.")
        mine.forEachIndexed { n, s ->
            val m = byId[s.meetingId] ?: return@forEachIndexed
            if (n > 0) HandDivider(seed = n)
            Door(
                m.title, { open("meeting/${m.id}") },
                meta = "${dateTime(context, m.createdAt)} · spoke ${formatClock(s.talkSec)} (${"%.0f".format(s.talkSec / m.durationSec.coerceAtLeast(1.0) * 100)}%)",
                style = Hand.door,
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionHead("calls", note = calls.size.takeIf { it > 0 }?.let { if (it == 1) "1 recording" else "$it recordings" })
        if (calls.isEmpty()) {
            Meta(
                if (p.phones.isEmpty()) "Call recordings named after them show up here. Link their contact so Burrow also knows their number."
                else "None yet. Recordings from your phone app (talks → call recordings) land here by their name or number.",
            )
        }
        calls.forEachIndexed { n, m ->
            if (n > 0) HandDivider(seed = n + 5)
            Door(
                m.title, { open("meeting/${m.id}") },
                meta = "${dateTime(context, m.createdAt)}${if (m.durationSec > 0) " · " + formatClock(m.durationSec) else ""}${if (m.status != MeetingStatus.READY) " · notes not ready yet" else ""}",
                style = Hand.door,
                leading = { DoodleIcon(Doodle.PHONE, null, size = 24.dp, color = burrow.pencil) },
            )
        }

        Spacer(Modifier.height(16.dp))
        val theirChats = chatLinks.mapNotNull { l -> chats.firstOrNull { it.id == l.chatId }?.let { it to l } }.distinctBy { it.first.id }
        SectionHead("chats", note = "only ones you shared")
        if (theirChats.isEmpty()) {
            Meta("To add one: in WhatsApp open your chat with ${p.name.substringBefore(' ')} → ⋮ → More → Export chat → Burrow. Burrow keeps it on this phone and never reads your messages on its own.")
        }
        theirChats.forEachIndexed { n, (c, l) ->
            if (n > 0) HandDivider(seed = n + 9)
            Door(
                c.title, { open("chat/${c.id}") },
                meta = "${l.messages} of ${c.messageCount} messages from them · last ${relativeDay(c.lastAt).lowercase()}",
                style = Hand.door,
                leading = { DoodleIcon(Doodle.BUBBLE, null, size = 24.dp, color = burrow.pencil) },
            )
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(p.name) }
        HandSheet({ renaming = false }) {
            Text("rename", style = Hand.headline.copy(fontSize = 34.sp), color = burrow.ink)
            HandField(name, { name = it }, singleLine = true)
            InkButton("save", { scope.launch { people.rename(id, name) }; renaming = false }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        }
    }
    if (deleting) {
        ConfirmSheet(
            "forget ${p.name.substringBefore(' ')}?",
            "Burrow deletes their learned voice and unlinks them from every talk. The talks stay, with their turns shown as an unnamed voice.",
            "forget them",
            { scope.launch { people.delete(id); deleting = false; if (!embedded) onBack() } },
            { deleting = false },
            danger = true,
        )
    }
}

/** How well Burrow knows their voice, and a 15-second sample to teach it. */
@Composable
private fun VoiceSection(samples: Int, onSample: suspend (FloatArray) -> Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { AudioRecorder() }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0.0) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun record() {
        recording = true
        message = null
        job = scope.launch {
            val clip = recorder.record(maxSeconds = 25.0) { _, s -> seconds = s }
            recording = false
            val ok = onSample(resample(clip.samples, clip.sampleRate, 16_000))
            message = if (ok) "Got it — their voice is learned a little better." else "Too little speech heard. Try again and keep them talking for 15 seconds."
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) record() }

    SectionHead("their voice", note = "only on this phone")
    Body(
        when (samples) {
            0 -> "Not learned yet. Name them in a talk, or record a short sample here, and Burrow will suggest them in future talks."
            in 1..2 -> "Learned from $samples sample${if (samples > 1) "s" else ""}. Each one makes suggestions better."
            else -> "Learned from $samples samples — Burrow knows this voice well."
        },
        color = burrow.pencil,
    )
    HandProgress((samples / 5f).coerceAtMost(1f), Modifier.padding(vertical = 6.dp))
    if (recording) {
        Text("have them talk naturally… ${"%.0f".format(seconds)} of 15 s", style = Hand.label, color = burrow.ink)
        HandProgress((seconds / 15).toFloat().coerceAtMost(1f))
        PillButton(if (seconds >= 15) "done" else "stop", { recorder.stop() }, Modifier.padding(top = 6.dp), leading = Doodle.STOP)
    } else {
        PillButton(
            "record a 15-second sample",
            {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) record()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            leading = Doodle.MIC,
        )
    }
    message?.let { DoneNote(it, Modifier.padding(top = 8.dp)) }
}

/** Their phone contact: call, text or open it — or link one. Burrow only ever sees the contact you pick. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactRow(p: ProfileEntity, onLink: () -> Unit) {
    val context = LocalContext.current
    val number = Contacts.phonesOf(p).firstOrNull()
    if (p.contactUri == null && number == null) {
        PillButton("link their contact", onLink, leading = Doodle.PERSON)
        Meta("Pick them in your contacts so their call recordings and chats come here. Burrow sees only the one contact you pick.", Modifier.padding(top = 4.dp))
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (number != null) {
            PillButton("call", { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }, leading = Doodle.PHONE)
            PillButton("message", { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) }, leading = Doodle.BUBBLE)
        }
        p.contactUri?.let { u -> PillButton("contact", { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } }, leading = Doodle.PERSON) }
        PillButton("add a number", onLink, leading = Doodle.PLUS)
    }
}
