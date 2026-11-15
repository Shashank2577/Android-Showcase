@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.meetings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import com.showcase.desertant.ask.TalkSummaries
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.ChoicePill
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.HandTag
import com.showcase.desertant.ui.hand.ItemRow
import com.showcase.desertant.ui.hand.ReadingColumn
import com.showcase.desertant.ui.hand.Warning
import com.showcase.desertant.ui.theme.themed
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
import com.showcase.desertant.speech.SpeechPacks
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.ConfirmSheet
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.Gutter
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.MaxColumn
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.marker
import com.showcase.desertant.ui.settings.LanguageSheet
import com.showcase.desertant.ui.settings.PackRow
import com.showcase.desertant.ui.shell.LocalWideLayout
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.Type
import com.showcase.desertant.ui.hand.SheetTitle
import com.showcase.desertant.ui.theme.burrow
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun MeetingDetailScreen(id: Long, onBack: () -> Unit, open: (String) -> Unit) {
    MeetingDetailContent(id, open = open, embedded = false, onBack = onBack)
}

/**
 * A talk's notes. Its job: read what was said and what was decided. Three
 * views behind pills (summary, to-dos, transcript); rename, language and
 * delete live in the "more" sheet. [embedded] drops the back button for the
 * two-pane layout.
 */
@Composable
fun MeetingDetailContent(id: Long, open: (String) -> Unit, embedded: Boolean, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db = context.app.db
    val scope = rememberCoroutineScope()
    val meeting by remember(id) { db.meetings().observe(id) }.collectAsState(initial = null)
    val speakers by remember(id) { db.speakers().forMeeting(id) }.collectAsState(initial = emptyList())
    val profiles by remember { db.profiles().all() }.collectAsState(initial = emptyList())
    var more by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf<MeetingSpeakerEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var choosingLanguage by remember { mutableStateOf(false) }

    val m = meeting ?: return
    fun retry() = scope.launch {
        db.meetings().setStatus(id, MeetingStatus.QUEUED, "Waiting to be written up")
        MeetingWorker.enqueue(context, id)
    }

    Column(Modifier.fillMaxSize().background(burrow.paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) DoodleButton(Doodle.BACK, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (m.status == MeetingStatus.READY) DoodleButton(Doodle.SHARE, "Share notes", { sharing = true })
            HandLink("more", { more = true }, Modifier.padding(end = 4.dp))
        }
        if (m.status != MeetingStatus.READY) ProcessingState(m, ::retry) { choosingLanguage = true }
        else Notes(m, speakers, profiles, onAssign = { assigning = it }, open = open)
    }

    if (more) {
        HandSheet({ more = false }) {
            SheetTitle("this talk")
            SheetAction("rename", Doodle.PEN) { more = false; renaming = true }
            SheetAction("language: ${m.language?.let { languageName(it).substringBefore(" (") } ?: "worked out by Burrow"}", Doodle.BUBBLE) { more = false; choosingLanguage = true }
            SheetAction("write the notes again", Doodle.CURL) { more = false; retry() }
            SheetAction("delete this talk", Doodle.TRASH, danger = true) { more = false; confirmDelete = true }
            Caption(
                if (File(m.audioPath).exists()) "The audio is deleted after 30 days. The notes stay." else "The audio was deleted after 30 days. The notes stay.",
            )
        }
    }
    if (choosingLanguage) {
        LanguageSheet(m.language, onDismiss = { choosingLanguage = false }) { code ->
            choosingLanguage = false
            scope.launch {
                db.meetings().chooseLanguage(id, code)
                retry()
            }
        }
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

/** One action in a sheet: a doodle and a hand-lettered word or two, 56 dp high. */
@Composable
private fun SheetAction(text: String, doodle: Doodle, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) burrow.redPen else burrow.ink
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DoodleIcon(doodle, null, size = 24.dp, color = color)
        Spacer(Modifier.width(14.dp))
        Text(text, style = Hand.door, color = color)
    }
}

@Composable
private fun ProcessingState(m: MeetingEntity, onRetry: () -> Unit, onChooseLanguage: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Gutter, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(m.title, style = Type.contentTitle, color = burrow.ink)
        val context = LocalContext.current
        val packs by SpeechPacks.state.collectAsState()
        val pack = SpeechPacks.route(m.language)?.takeIf { m.languageSupported == false }
        if (m.status == MeetingStatus.FAILED && pack != null) {
            // A talk in a language Voz doesn't speak: one download away from its notes.
            val name = languageName(m.language).substringBefore(" (")
            val ready = packs[pack.code] == SpeechPacks.State.Ready || SpeechPacks.isReady(context, pack)
            Body(if (ready) "The $name speech pack is on the phone now." else "This talk is in $name. Burrow needs the speech pack for it, once, to write the notes.")
            if (ready) InkButton("write the notes", onRetry, Modifier.fillMaxWidth()) else PackRow(pack)
            HandLink("not $name? choose the language", onChooseLanguage)
        } else if (m.status == MeetingStatus.FAILED) {
            Warning(m.error ?: "The notes couldn't be written.")
            InkButton("try again", onRetry, Modifier.fillMaxWidth())
            HandLink("wrong language? choose it", onChooseLanguage)
        } else {
            Caption(m.progress ?: "Waiting to be written up")
            HandProgress(null)
            Body("Cleaning the audio, finding the language, writing down the words, working out who spoke when, then the topics and follow-ups. It all runs on this phone, and you'll get a quiet notification.")
        }
    }
}

private enum class TalkView { SUMMARY, TODOS, TRANSCRIPT }

@Composable
private fun Notes(
    m: MeetingEntity,
    speakers: List<MeetingSpeakerEntity>,
    profiles: List<ProfileEntity>,
    onAssign: (MeetingSpeakerEntity) -> Unit,
    open: (String) -> Unit,
) {
    val utterances = remember(m.utterancesJson) { decodeUtterances(m.utterancesJson) }
    val topics = remember(m.topicsJson) { decodeTopics(m.topicsJson) }
    val highlights = remember(m.highlightsJson) { decodeMoments(m.highlightsJson) }
    val followUps = remember(m.followUpsJson) { decodeMoments(m.followUpsJson) }
    val summary = remember(m.summaryJson) { TalkSummaries.decode(m.summaryJson) }
    val audio = remember(m.audioPath) { File(m.audioPath).exists() }
    val player = rememberMeetingPlayer(m.audioPath)
    var filter by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable(m.id) { mutableStateOf(TalkView.SUMMARY) }
    val shown = remember(utterances, filter) { if (filter.isBlank()) utterances else utterances.filter { it.text.contains(filter, true) } }
    val active = utterances.indexOfLast { player.positionSec >= it.start - 0.05 }.takeIf { player.playing || player.positionSec > 0 }
    val wide = LocalWideLayout.current
    val context = LocalContext.current
    val todoCount = summary?.todos?.size ?: followUps.size
    val seek: (Double) -> Unit = { player.seek(it, andPlay = true) }

    val pad = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 40.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = if (wide) Alignment.TopStart else Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = ReadingColumn + Gutter * 2).fillMaxSize().padding(start = if (wide) 16.dp else 0.dp),
            contentPadding = pad,
        ) {
            item {
                Text(m.title, style = Type.contentTitle, color = burrow.ink)
                Caption(
                    listOfNotNull(
                        talkDate(context, m.createdAt),
                        m.durationSec.takeIf { it > 0 }?.let { "${(it / 60).toInt().coerceAtLeast(1)} min" },
                        m.language?.let { languageName(it).substringBefore(" (") },
                    ).joinToString(" · "),
                    Modifier.padding(top = 2.dp),
                )
                if (topics.isNotEmpty()) Caption(topics.take(4).joinToString(" · ") { it.name.lowercase() }, Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoicePill("summary", view == TalkView.SUMMARY, { view = TalkView.SUMMARY })
                    ChoicePill(if (todoCount > 0) "to-dos $todoCount" else "to-dos", view == TalkView.TODOS, { view = TalkView.TODOS })
                    ChoicePill("transcript", view == TalkView.TRANSCRIPT, { view = TalkView.TRANSCRIPT })
                }
                Spacer(Modifier.height(20.dp))
                // The player shows once something is playing, and always with the transcript.
                if (audio && player.available && (view == TalkView.TRANSCRIPT || player.playing || player.positionSec > 0)) {
                    PlayerLine(player, m.durationSec)
                    Spacer(Modifier.height(16.dp))
                }
            }
            when (view) {
                TalkView.SUMMARY -> {
                    item { SummarySection(m, onSeek = seek, open = open, highlights = highlights) }
                    item { WhoWasThere(speakers, profiles, onAssign, open) }
                }
                TalkView.TODOS -> item {
                    TodosSection(m, followUps, speakerName = { lookFor(it, speakers, profiles).name }, onSeek = seek)
                }
                TalkView.TRANSCRIPT -> {
                    item {
                        if (wide && speakers.isNotEmpty()) {
                            Lanes(utterances, speakers, profiles, m.durationSec, player)
                            Spacer(Modifier.height(12.dp))
                        }
                        HandField(filter, { filter = it }, placeholder = "search what was said", singleLine = true, leading = Doodle.SEARCH)
                        Spacer(Modifier.height(8.dp))
                        if (utterances.isEmpty()) Body("No speech was found in this recording.", color = burrow.pencil)
                    }
                    itemsIndexed(shown, key = { i, u -> "${u.start}-$i" }) { _, u ->
                        Line(u, lookFor(u.speaker, speakers, profiles), isActive = active != null && utterances.getOrNull(active) == u) { player.seek(u.start, andPlay = true) }
                    }
                }
            }
        }
    }
}

/** "Sat 31 Oct", with the year only when it isn't this year. */
fun talkDate(context: android.content.Context, millis: Long): String {
    val d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val pattern = if (d.year == java.time.LocalDate.now().year) "EEE d MMM" else "EEE d MMM yyyy"
    return d.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()))
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
        Body("No distinct voices found.", color = burrow.pencil)
        return
    }
    val named = speakers.filter { lookFor(it.index, speakers, profiles).profile != null }
    if (named.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            named.forEach { s ->
                val look = lookFor(s.index, speakers, profiles)
                HandTag("${look.name} · ${"%.0f".format(s.talkSec / total * 100)}%", dot = themed(look.color), onClick = { open("person/${look.profile!!.id}") })
            }
        }
    }
    speakers.filter { it !in named }.forEach { s ->
        val look = lookFor(s.index, speakers, profiles)
        val suggestion = s.suggestedProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
        ItemRow(
            look.name,
            meta = "${formatClock(s.talkSec)} of talking · ${s.words} words",
            leading = { Box(Modifier.size(10.dp).clip(CircleShape).background(themed(look.color))) },
            trailing = {
                if (suggestion != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (suggestion.isMe) "you?" else "${suggestion.name.substringBefore(' ')}?", style = Type.tag, color = burrow.ink)
                    DoodleButton(Doodle.TICK, "Yes, it's ${suggestion.name}", { scope.launch { people.assign(s.id, suggestion.id) } }, size = 22.dp)
                    DoodleButton(Doodle.CROSS, "No", { scope.launch { people.dismissSuggestion(s.id) } }, size = 20.dp, color = burrow.pencil)
                } else HandLink("name them", { onAssign(s) })
            },
        )
    }
    Caption("Name a voice once and Burrow suggests that person in later talks.", Modifier.padding(top = 4.dp))
}

@Composable
private fun Line(u: Utterance, look: SpeakerLook, isActive: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (isActive) Modifier.background(burrow.smudge) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = if (isActive) 8.dp else 0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(themed(look.color)))
            Spacer(Modifier.width(8.dp))
            Text(look.name, Modifier.weight(1f), style = Type.rowTitle, color = burrow.ink, maxLines = 1)
            Caption(formatClock(u.start))
        }
        Text(u.text, Modifier.padding(top = 2.dp), style = Type.reading, color = burrow.ink)
    }
    HandDivider(seed = u.start.toInt())
}

@Composable
private fun RenameSheet(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    HandSheet(onDismiss) {
        SheetTitle("rename this talk")
        HandField(text, { text = it }, singleLine = true)
        InkButton("save", { if (text.isNotBlank()) onSave(text.trim()) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
    }
}
