@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)



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
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandTag
import com.showcase.desertant.ui.hand.InitialCircle
import com.showcase.desertant.ui.hand.ItemRow
import com.showcase.desertant.ui.meetings.talkDate
import com.showcase.desertant.ui.theme.Type
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
import com.showcase.desertant.ui.hand.SheetTitle
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
        topEnd = { HandLink("more", { menu = true }, Modifier.padding(end = 4.dp)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialCircle(p.name, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (p.isMe) "${p.name} (you)" else p.name, style = Type.contentTitle, color = burrow.ink)
                Caption(
                    listOfNotNull(
                        mine.map { it.meetingId }.distinct().size.let { if (it == 1) "1 talk" else "$it talks" },
                        calls.size.takeIf { it > 0 }?.let { if (it == 1) "1 call" else "$it calls" },
                        chatLinks.filterNot { it.isMe }.sumOf { it.messages }.takeIf { it > 0 }?.let { "$it messages" },
                        "${formatClock(totalTalk)} talking",
                        languages.takeIf { it.isNotEmpty() }?.joinToString(", ") { languageName(it).substringBefore(" (") },
                    ).joinToString(" · "),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ContactRow(p, onLink = { pick.launch(Unit) })

        SectionHead("talks together")
        if (mine.isEmpty()) Body("Not in any talk yet. Open a talk's notes and tap “name them” next to their voice.", color = burrow.pencil)
        mine.forEachIndexed { n, s ->
            val m = byId[s.meetingId] ?: return@forEachIndexed
            if (n > 0) HandDivider(seed = n)
            ItemRow(
                m.title,
                meta = "${talkDate(context, m.createdAt)} · spoke ${formatClock(s.talkSec)} (${"%.0f".format(s.talkSec / m.durationSec.coerceAtLeast(1.0) * 100)}%)",
                onClick = { open("meeting/${m.id}") },
            )
        }

        SectionHead("calls")
        if (calls.isEmpty()) {
            Body(
                if (p.phones.isEmpty()) "Call recordings named after them show up here. Link their contact so Burrow also knows their number."
                else "None yet. Recordings from your phone app (talks → import) land here by their name or number.",
                color = burrow.pencil,
            )
        }
        calls.forEachIndexed { n, m ->
            if (n > 0) HandDivider(seed = n + 5)
            ItemRow(
                m.title,
                meta = "${talkDate(context, m.createdAt)}${if (m.durationSec > 0) " · " + formatClock(m.durationSec) else ""}${if (m.status != MeetingStatus.READY) " · notes not ready yet" else ""}",
                onClick = { open("meeting/${m.id}") },
                leading = { DoodleIcon(Doodle.PHONE, null, size = 24.dp, color = burrow.pencil) },
            )
        }

        val theirChats = chatLinks.mapNotNull { l -> chats.firstOrNull { it.id == l.chatId }?.let { it to l } }.distinctBy { it.first.id }
        SectionHead("chats", note = "Only ones you shared.")
        if (theirChats.isEmpty()) {
            Body("To add one: in WhatsApp open your chat with ${p.name.substringBefore(' ')} → ⋮ → More → Export chat → Burrow. Burrow keeps it on this phone and never reads your messages on its own.", color = burrow.pencil)
        }
        theirChats.forEachIndexed { n, (c, l) ->
            if (n > 0) HandDivider(seed = n + 9)
            ItemRow(
                c.title,
                meta = "${l.messages} of ${c.messageCount} messages from them · last ${relativeDay(c.lastAt).lowercase()}",
                onClick = { open("chat/${c.id}") },
                leading = { DoodleIcon(Doodle.BUBBLE, null, size = 24.dp, color = burrow.pencil) },
            )
        }

        if (topics.isNotEmpty()) {
            SectionHead("what you talk about")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { t -> HandTag((topicNames[t.slug] ?: t.slug).lowercase()) }
            }
        }

        VoiceSection(p.signatureCount) { samples -> people.enroll(id, samples) }
    }

    if (menu) {
        HandSheet({ menu = false }) {
            SheetTitle(p.name.substringBefore(' ').lowercase().ifEmpty { "this person" })
            ItemRow("Rename", onClick = { menu = false; renaming = true }, leading = { DoodleIcon(Doodle.PEN, null, size = 24.dp) })
            if (p.contactUri != null) ItemRow("Open their contact", onClick = { menu = false; runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.contactUri))) } }, leading = { DoodleIcon(Doodle.PERSON, null, size = 24.dp) })
            ItemRow(if (p.contactUri == null && p.phones.isEmpty()) "Link their contact" else "Add a number", onClick = { menu = false; pick.launch(Unit) }, leading = { DoodleIcon(Doodle.PLUS, null, size = 24.dp) })
            if (p.contactUri != null || p.phones.isNotEmpty()) {
                ItemRow("Unlink their contact", onClick = { menu = false; scope.launch { people.unlinkContact(id) } }, leading = { DoodleIcon(Doodle.CROSS, null, size = 24.dp) })
            }
            ItemRow("Forget this person", onClick = { menu = false; deleting = true }, titleColor = burrow.redPen, leading = { DoodleIcon(Doodle.TRASH, null, size = 24.dp, color = burrow.redPen) })
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(p.name) }
        HandSheet({ renaming = false }) {
            SheetTitle("rename")
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

    SectionHead("their voice")
    Meta(
        when (samples) {
            0 -> "Not learned yet. Name them in a talk, or record a sample, and Burrow suggests them in later talks."
            in 1..2 -> "Learned from $samples sample${if (samples > 1) "s" else ""}, only on this phone. Each one makes suggestions better."
            else -> "Learned from $samples samples, only on this phone. Burrow knows this voice well."
        },
    )
    Spacer(Modifier.height(8.dp))
    if (recording) {
        Body("Have them talk naturally… ${"%.0f".format(seconds)} of 15 s")
        HandProgress((seconds / 15).toFloat().coerceAtMost(1f))
        PillButton(if (seconds >= 15) "done" else "stop", { recorder.stop() }, Modifier.padding(top = 6.dp), leading = Doodle.STOP)
    } else {
        PillButton(
            "record a sample",
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
        Caption("So their call recordings and chats come here. Burrow sees only the one contact you pick.", Modifier.padding(top = 4.dp))
        return
    }
    if (number == null) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("call", { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }, leading = Doodle.PHONE)
        PillButton("message", { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) }, leading = Doodle.BUBBLE)
    }
}
