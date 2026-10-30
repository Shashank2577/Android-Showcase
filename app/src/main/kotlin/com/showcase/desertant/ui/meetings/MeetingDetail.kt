package com.showcase.desertant.ui.meetings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.app
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.core.languageName
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.Moment
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.data.Utterance
import com.showcase.desertant.meetings.MeetingWorker
import com.showcase.desertant.people.People
import com.showcase.desertant.speech.IndicSpeech
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.ConfirmSheet
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.Gutter
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.MaxColumn
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.marker
import com.showcase.desertant.ui.settings.IndicLanguages
import com.showcase.desertant.ui.shell.LocalWideLayout
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun MeetingDetailScreen(id: Long, onBack: () -> Unit, open: (String) -> Unit) {
    MeetingDetailContent(id, open = open, embedded = false, onBack = onBack)
}

/** A talk's notes. [embedded] drops the back button for the Fold's two-pane layout. */
@Composable
fun MeetingDetailContent(id: Long, open: (String) -> Unit, embedded: Boolean, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db = context.app.db
    val scope = rememberCoroutineScope()
    val meeting by remember(id) { db.meetings().observe(id) }.collectAsState(initial = null)
    val speakers by remember(id) { db.speakers().forMeeting(id) }.collectAsState(initial = emptyList())
    val profiles by remember { db.profiles().all() }.collectAsState(initial = emptyList())
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf<MeetingSpeakerEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val m = meeting ?: return
    fun retry() = scope.launch {
        db.meetings().setStatus(id, MeetingStatus.QUEUED, "Waiting to be written up")
        MeetingWorker.enqueue(context, id)
    }

    Column(Modifier.fillMaxSize().background(burrow.paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) DoodleButton(Doodle.BACK, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (m.status == MeetingStatus.READY) DoodleButton(Doodle.SHARE, "Share notes", { sharing = true })
            Box {
                PillButton("options ▾", { menu = true }, Modifier.padding(horizontal = 8.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Write the notes again") }, onClick = { menu = false; retry() })
                    DropdownMenuItem(text = { Text("Delete this talk") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        if (m.status != MeetingStatus.READY) ProcessingState(m, ::retry)
        else Notes(m, speakers, profiles, onRename = { renaming = true }, onAssign = { assigning = it }, open = open)
    }

    if (renaming) RenameSheet(m.title, onDismiss = { renaming = false }) { t -> scope.launch { db.meetings().rename(id, t) }; renaming = false }
    if (sharing) ShareDialog(m, speakers, profiles) { sharing = false }
    assigning?.let { s -> AssignSpeakerDialog(s, profiles, onDismiss = { assigning = null }) }
    if (confirmDelete) {
        ConfirmSheet(
            "delete this talk?",
            "The recording, transcript and notes are removed from this phone for good.",
            "delete it",
            {
                confirmDelete = false
                scope.launch {
                    File(m.audioPath).delete()
                    db.meetings().delete(id)
                    if (!embedded) onBack()
                }
            },
            { confirmDelete = false },
            danger = true,
        )
    }
}

@Composable
private fun ProcessingState(m: MeetingEntity, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(m.title, style = Hand.headline, color = burrow.ink)
        val indic = IndicSpeech.languageFor(m.language)
        val context = LocalContext.current
        val languages by IndicSpeech.state.collectAsState()
        if (m.status == MeetingStatus.FAILED && indic != null) {
            // A talk in a language Voz doesn't speak: one download away from its notes.
            val ready = languages[indic.code] == IndicSpeech.State.Ready || IndicSpeech.isReady(context, indic.code)
            Body(
                if (ready) "${indic.name} is on the phone now. Write the notes." else "This talk is in ${indic.name} (${indic.native}). Burrow needs the ${indic.name} speech model once to write its notes.",
                color = burrow.ink,
            )
            if (ready) InkButton("write the notes", onRetry, Modifier.fillMaxWidth()) else IndicLanguages()
        } else if (m.status == MeetingStatus.FAILED) {
            DoodleIcon(Doodle.CROSS, null, size = 40.dp, color = burrow.redPen, stroke = 3.dp)
            Body(m.error ?: "The notes couldn't be written.", color = burrow.redPen)
            InkButton("try again", onRetry)
        } else {
            CircularProgressIndicator(color = burrow.ink, strokeWidth = 2.dp)
            Text((m.progress ?: "waiting to be written up").lowercase(), style = Hand.door, color = burrow.ink)
            Meta("cleaning the audio → the language → the words → who spoke when → topics and follow-ups. It all runs on this phone; you'll get a quiet notification.")
        }
    }
}

@Composable
private fun Notes(
    m: MeetingEntity,
    speakers: List<MeetingSpeakerEntity>,
    profiles: List<ProfileEntity>,
    onRename: () -> Unit,
    onAssign: (MeetingSpeakerEntity) -> Unit,
    open: (String) -> Unit,
) {
    val utterances = remember(m.utterancesJson) { decodeUtterances(m.utterancesJson) }
    val topics = remember(m.topicsJson) { decodeTopics(m.topicsJson) }
    val highlights = remember(m.highlightsJson) { decodeMoments(m.highlightsJson) }
    val followUps = remember(m.followUpsJson) { decodeMoments(m.followUpsJson) }
    val audio = remember(m.audioPath) { File(m.audioPath).exists() }
    val player = rememberMeetingPlayer(m.audioPath)
    var filter by rememberSaveable { mutableStateOf("") }
    val done = remember { mutableStateListOf<Int>() }
    val shown = remember(utterances, filter) { if (filter.isBlank()) utterances else utterances.filter { it.text.contains(filter, true) } }
    val active = utterances.indexOfLast { player.positionSec >= it.start - 0.05 }.takeIf { player.playing || player.positionSec > 0 }
    val wide = LocalWideLayout.current
    val context = LocalContext.current

    val overview: LazyListScope.() -> Unit = {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Text(m.title, Modifier.weight(1f), style = Hand.headline, color = burrow.ink)
                DoodleButton(Doodle.PEN, "Rename", onRename, size = 22.dp)
            }
            Meta(
                listOfNotNull(dateTime(context, m.createdAt), formatClock(m.durationSec), m.language?.let { languageName(it) }).joinToString(" · "),
                Modifier.padding(top = 2.dp),
            )
            if (m.languageSupported == false) Meta("transcription isn't trained on this language", color = burrow.redPen)
            if (topics.isNotEmpty()) {
                val words = topics.take(4).joinToString("  ·  ") { it.name.lowercase() }
                MarkedText(words, Modifier.padding(top = 10.dp), mark = topics.first().name.lowercase(), style = Hand.label)
            }
        }
        item {
            if (audio && player.available) PlayerLine(player, m.durationSec)
            else Meta("The audio was deleted after 30 days. The notes stay.", Modifier.padding(vertical = 8.dp))
        }
        if (speakers.isNotEmpty()) item { Lanes(utterances, speakers, profiles, m.durationSec, player) }
        item { WhoWasThere(speakers, profiles, onAssign, open) }
        if (speakers.any { it.profileId == null }) item { Meta("Name a voice once and Burrow suggests that person in later talks.") }
        if (highlights.isNotEmpty()) item {
            SectionHead("worth remembering")
            highlights.forEach { mo -> MomentLine(mo, player) }
        }
        if (followUps.isNotEmpty()) item {
            SectionHead("to do", note = "tap to tick")
            followUps.forEachIndexed { i, mo ->
                val ticked = i in done
                Row(
                    Modifier.fillMaxWidth().clickable { if (ticked) done.remove(i) else done.add(i) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.padding(top = 2.dp).size(20.dp).border(1.6.dp, burrow.ink, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        if (ticked) DoodleIcon(Doodle.TICK, null, size = 22.dp, stroke = 2.4.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            mo.text,
                            style = Hand.label.copy(textDecoration = if (ticked) TextDecoration.LineThrough else null),
                            color = if (ticked) burrow.graphite else burrow.ink,
                        )
                        Meta("${lookFor(mo.speaker, speakers, profiles).name} · ${formatClock(mo.start)}")
                    }
                }
            }
        }
    }
    val transcript: LazyListScope.() -> Unit = {
        item {
            SectionHead("every word", note = "${utterances.size} turns")
            Spacer(Modifier.height(8.dp))
            HandField(filter, { filter = it }, placeholder = "find in this talk", singleLine = true, leading = Doodle.SEARCH)
        }
        itemsIndexed(shown, key = { i, u -> "${u.start}-$i" }) { _, u ->
            Line(u, lookFor(u.speaker, speakers, profiles), isActive = active != null && utterances.getOrNull(active) == u) { player.seek(u.start) }
        }
        if (utterances.isEmpty()) item { Meta("No speech was found in this recording.") }
    }

    val pad = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 40.dp)
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(16.dp), content = overview)
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp), content = transcript)
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = MaxColumn + Gutter * 2).fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                overview()
                transcript()
            }
        }
    }
}

/** A drawn play circle and a scrub line; tap the line to jump. */
@Composable
private fun PlayerLine(player: MeetingPlayer, duration: Double) {
    val total = (player.durationSec.takeIf { it > 0 } ?: duration).coerceAtLeast(0.1)
    val ink = burrow.ink
    val rule = burrow.rule
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).border(1.8.dp, ink, CircleShape).clickable(onClick = player::toggle),
            contentAlignment = Alignment.Center,
        ) { DoodleIcon(if (player.playing) Doodle.PAUSE else Doodle.PLAY, if (player.playing) "Pause" else "Play", size = 22.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(total) { detectTapGestures { o -> player.seek(o.x / size.width * total, andPlay = player.playing) } },
            ) {
                val y = size.height / 2
                drawLine(rule, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
                val x = (player.positionSec / total * size.width).toFloat().coerceIn(0f, size.width)
                drawLine(ink, Offset(0f, y), Offset(x, y), 2.6.dp.toPx(), StrokeCap.Round)
                drawCircle(ink, 5.dp.toPx(), Offset(x, y))
            }
            Row {
                Meta(formatClock(player.positionSec), Modifier.weight(1f))
                Meta(formatClock(total))
            }
        }
    }
}

/** One thin inked lane per voice, each with its own stroke texture instead of a color. */
@Composable
private fun Lanes(utterances: List<Utterance>, speakers: List<MeetingSpeakerEntity>, profiles: List<ProfileEntity>, duration: Double, player: MeetingPlayer) {
    val ink = burrow.ink
    val d = duration.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        speakers.forEachIndexed { row, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Meta(lookFor(s.index, speakers, profiles).name.substringBefore(' '), Modifier.width(72.dp), maxLines = 1)
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .pointerInput(d) { detectTapGestures { o -> player.seek(o.x / size.width * d) } },
                ) {
                    val y = size.height / 2
                    val effect = when (row % 3) {
                        0 -> null
                        1 -> PathEffect.dashPathEffect(floatArrayOf(14f, 6f))
                        else -> PathEffect.dashPathEffect(floatArrayOf(4f, 5f))
                    }
                    utterances.filter { it.speaker == s.index }.forEach { u ->
                        val x0 = (u.start / d * size.width).toFloat()
                        val x1 = (u.end / d * size.width).toFloat().coerceAtLeast(x0 + 3f)
                        drawLine(ink, Offset(x0, y), Offset(x1, y), 2.6.dp.toPx(), StrokeCap.Round, effect)
                    }
                    val x = (player.positionSec / d * size.width).toFloat()
                    if (player.positionSec > 0) drawLine(ink.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun WhoWasThere(speakers: List<MeetingSpeakerEntity>, profiles: List<ProfileEntity>, onAssign: (MeetingSpeakerEntity) -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val people = remember { People(context) }
    val total = speakers.sumOf { it.talkSec }.coerceAtLeast(1e-6)
    SectionHead("who was there")
    if (speakers.isEmpty()) {
        Meta("No distinct voices found.")
        return
    }
    Text(
        speakers.joinToString("  ·  ") { s -> "${lookFor(s.index, speakers, profiles).name.substringBefore(' ').lowercase()} ${"%.0f".format(s.talkSec / total * 100)}%" },
        style = Hand.label, color = burrow.ink, modifier = Modifier.padding(top = 4.dp),
    )
    speakers.forEach { s ->
        val look = lookFor(s.index, speakers, profiles)
        val suggestion = s.suggestedProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { if (look.profile != null) open("person/${look.profile.id}") else onAssign(s) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(look.name, style = Hand.door.copy(fontSize = 22.sp), color = burrow.ink)
                Meta("${formatClock(s.talkSec)} of talking · ${s.words} words")
            }
            when {
                look.profile == null && suggestion != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (suggestion.isMe) "you?" else "${suggestion.name.substringBefore(' ')}?", style = Hand.small, color = burrow.ink)
                    DoodleButton(Doodle.TICK, "Yes, it's ${suggestion.name}", { scope.launch { people.assign(s.id, suggestion.id) } }, size = 20.dp)
                    DoodleButton(Doodle.CROSS, "No", { scope.launch { people.dismissSuggestion(s.id) } }, size = 18.dp, color = burrow.redPen)
                }
                look.profile == null -> PillButton("name them", { onAssign(s) })
                else -> DoodleIcon(Doodle.ARROW, null, size = 20.dp, color = burrow.pencil)
            }
        }
    }
}

@Composable
private fun MomentLine(mo: Moment, player: MeetingPlayer) {
    Row(Modifier.fillMaxWidth().clickable { player.seek(mo.start) }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(formatClock(mo.start), Modifier.width(52.dp).padding(top = 2.dp), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = burrow.pencil)
        Body(mo.text, Modifier.weight(1f))
    }
}

@Composable
private fun Line(u: Utterance, look: SpeakerLook, isActive: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(look.name.lowercase(), style = Hand.small, color = burrow.ink, modifier = if (isActive) Modifier.marker(burrow.marker) else Modifier)
            Spacer(Modifier.width(8.dp))
            Meta(formatClock(u.start))
        }
        Body(u.text)
    }
    HandDivider(seed = u.start.toInt())
}

@Composable
private fun RenameSheet(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    HandSheet(onDismiss) {
        Text("rename this talk", style = Hand.headline.copy(fontSize = 34.sp), color = burrow.ink)
        HandField(text, { text = it }, singleLine = true)
        InkButton("save", { if (text.isNotBlank()) onSave(text.trim()) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
    }
}
