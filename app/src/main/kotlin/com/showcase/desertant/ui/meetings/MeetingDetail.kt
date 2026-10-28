@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.meetings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.showcase.desertant.ui.common.Avatar
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.shell.LocalWideLayout
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MeetingDetailScreen(id: Long, onBack: () -> Unit, open: (String) -> Unit) {
    MeetingDetailContent(id, open = open, embedded = false, onBack = onBack)
}

/** A meeting's notes. [embedded] drops the back button for the Fold's two-pane layout. */
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (embedded) "" else "Meeting notes") },
                navigationIcon = { if (!embedded) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (m.status == MeetingStatus.READY) IconButton(onClick = { sharing = true }) { Icon(Icons.Outlined.Share, "Share notes") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Process again") }, onClick = {
                                menu = false
                                scope.launch {
                                    db.meetings().setStatus(id, MeetingStatus.QUEUED, "Waiting to be processed")
                                    MeetingWorker.enqueue(context, id)
                                }
                            })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (m.status != MeetingStatus.READY) {
                ProcessingState(m) {
                    scope.launch {
                        db.meetings().setStatus(id, MeetingStatus.QUEUED, "Waiting to be processed")
                        MeetingWorker.enqueue(context, id)
                    }
                }
            } else {
                MeetingNotes(m, speakers, profiles, onRename = { renaming = true }, onAssign = { assigning = it }, open = open)
            }
        }
    }

    if (renaming) RenameDialog(m.title, onDismiss = { renaming = false }) { t -> scope.launch { db.meetings().rename(id, t) }; renaming = false }
    if (sharing) ShareDialog(m, speakers, profiles) { sharing = false }
    assigning?.let { s -> AssignSpeakerDialog(s, profiles, onDismiss = { assigning = null }) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this meeting?") },
            text = { Text("The recording, transcript and notes are removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        File(m.audioPath).delete()
                        db.meetings().delete(id)
                        if (!embedded) onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProcessingState(m: MeetingEntity, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(m.title, style = MaterialTheme.typography.headlineSmall)
        if (m.status == MeetingStatus.FAILED) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
            Text(m.error ?: "Processing failed.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("Try again") }
        } else {
            CircularProgressIndicator()
            Text(m.progress ?: "Waiting to be processed", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Language → transcript → speakers → topics → people. It runs in the background on this phone; you'll get a notification when the notes are ready.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MeetingNotes(
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
    val player = rememberMeetingPlayer(m.audioPath)
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = remember(utterances, filter) { if (filter.isBlank()) utterances else utterances.filter { it.text.contains(filter, true) } }
    val active = utterances.indexOfLast { player.positionSec >= it.start - 0.05 }.takeIf { player.playing || player.positionSec > 0 }
    val wide = LocalWideLayout.current

    val overview: LazyListScope.() -> Unit = {
        item { Header(m, topics, onRename) }
        item { PlayerBar(player, m.durationSec) }
        item { SpeakersCard(m, speakers, profiles, player, onAssign, open) }
        if (highlights.isNotEmpty()) item { MomentsCard("Highlights", highlights, speakers, profiles) { player.seek(it.start) } }
        if (followUps.isNotEmpty()) item { MomentsCard("Possible follow-ups", followUps, speakers, profiles, checklist = true) { player.seek(it.start) } }
    }
    val transcript: LazyListScope.() -> Unit = {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Transcript", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${utterances.size} turns", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(filter, { filter = it }, placeholder = { Text("Find in transcript") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        itemsIndexed(shown, key = { i, u -> "${u.start}-$i" }) { _, u ->
            Bubble(u, lookFor(u.speaker, speakers, profiles), isActive = active != null && utterances.getOrNull(active) == u) { player.seek(u.start) }
        }
        if (utterances.isEmpty()) item { Text("No speech was found in this recording.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = overview)
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = transcript)
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                overview()
                transcript()
            }
        }
    }
}

@Composable
private fun Header(m: MeetingEntity, topics: List<com.showcase.desertant.data.TopicScore>, onRename: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(m.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, "Rename") }
        }
        Text(
            "${dateTime(context, m.createdAt)} · ${formatClock(m.durationSec)}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        m.language?.let { lang ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Translate, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(languageName(lang), style = MaterialTheme.typography.bodyMedium)
                if (m.languageSupported == false) {
                    Text("  · transcription isn't trained on this language", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (topics.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            topics.forEach { TopicPill(it.name) }
        }
    }
}

@Composable
private fun PlayerBar(player: MeetingPlayer, duration: Double) {
    if (!player.available) return
    val total = player.durationSec.takeIf { it > 0 } ?: duration
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = player::toggle) {
                Icon(if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (player.playing) "Pause" else "Play")
            }
            Spacer(Modifier.width(8.dp))
            Slider(
                value = player.positionSec.toFloat().coerceIn(0f, total.toFloat().coerceAtLeast(0.1f)),
                onValueChange = { player.seek(it.toDouble(), andPlay = player.playing) },
                valueRange = 0f..total.toFloat().coerceAtLeast(0.1f),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("${formatClock(player.positionSec)} / ${formatClock(total)}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SpeakersCard(
    m: MeetingEntity,
    speakers: List<MeetingSpeakerEntity>,
    profiles: List<ProfileEntity>,
    player: MeetingPlayer,
    onAssign: (MeetingSpeakerEntity) -> Unit,
    open: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val people = remember { People(context) }
    val utterances = remember(m.utterancesJson) { decodeUtterances(m.utterancesJson) }
    val totalTalk = speakers.sumOf { it.talkSec }.coerceAtLeast(1e-6)
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Who was there", style = MaterialTheme.typography.titleMedium)
            if (speakers.isEmpty()) Text("No distinct speakers detected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            speakers.forEach { s ->
                val look = lookFor(s.index, speakers, profiles)
                val suggestion = s.suggestedProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        if (look.profile != null) open("person/${look.profile.id}") else onAssign(s)
                    }) {
                        Avatar(look.name, look.color, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(look.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${formatClock(s.talkSec)} · ${"%.0f".format(s.talkSec / totalTalk * 100)}% of the talking · ${s.words} words",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onAssign(s) }) { Text(if (look.profile == null) "Who is this?" else "Change") }
                    }
                    LinearProgressIndicator(
                        progress = { (s.talkSec / totalTalk).toFloat() },
                        color = look.color,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    if (look.profile == null && suggestion != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Sounds like ${suggestion.name} · ${"%.0f".format((s.suggestionScore ?: 0.0) * 100)}% voice match",
                                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { scope.launch { people.assign(s.id, suggestion.id) } }) { Icon(Icons.Outlined.Check, "Yes, it's ${suggestion.name}") }
                                IconButton(onClick = { scope.launch { people.dismissSuggestion(s.id) } }) { Icon(Icons.Outlined.Close, "No") }
                            }
                        }
                    }
                }
            }
            if (speakers.isNotEmpty()) {
                HorizontalDivider()
                Timeline(utterances, speakers, profiles, m.durationSec, player)
            }
        }
    }
}

/** One lane per speaker; tap anywhere to jump there. */
@Composable
private fun Timeline(utterances: List<Utterance>, speakers: List<MeetingSpeakerEntity>, profiles: List<ProfileEntity>, duration: Double, player: MeetingPlayer) {
    val lanes = speakers.map { it.index }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val cursor = MaterialTheme.colorScheme.onSurface
    val colors = lanes.associateWith { lookFor(it, speakers, profiles).color }
    val d = duration.coerceAtLeast(1.0)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height((14 * lanes.size + 6 * (lanes.size - 1)).coerceAtLeast(14).dp)
            .pointerInput(d) { detectTapGestures { o -> player.seek(o.x / size.width * d) } },
    ) {
        val laneH = 14.dp.toPx()
        val gap = 6.dp.toPx()
        lanes.forEachIndexed { row, speaker ->
            val y = row * (laneH + gap)
            drawRoundRect(track, Offset(0f, y), Size(size.width, laneH), CornerRadius(laneH / 2))
            utterances.filter { it.speaker == speaker }.forEach { u ->
                val x0 = (u.start / d * size.width).toFloat()
                val x1 = (u.end / d * size.width).toFloat()
                drawRoundRect(colors.getValue(speaker), Offset(x0, y), Size((x1 - x0).coerceAtLeast(3f), laneH), CornerRadius(laneH / 3))
            }
        }
        val x = (player.positionSec / d * size.width).toFloat()
        drawLine(cursor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun MomentsCard(
    title: String,
    moments: List<Moment>,
    speakers: List<MeetingSpeakerEntity>,
    profiles: List<ProfileEntity>,
    checklist: Boolean = false,
    onSeek: (Moment) -> Unit,
) {
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            moments.forEach { mo ->
                val look = lookFor(mo.speaker, speakers, profiles)
                Row(Modifier.fillMaxWidth().clickable { onSeek(mo) }, verticalAlignment = Alignment.Top) {
                    if (checklist) Icon(Icons.Outlined.TaskAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    else Box(Modifier.padding(top = 6.dp).size(8.dp).background(look.color, RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mo.text, style = MaterialTheme.typography.bodyMedium)
                        Text("${look.name} · ${formatClock(mo.start)}", style = MaterialTheme.typography.labelSmall, color = look.color)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(u: Utterance, look: SpeakerLook, isActive: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.Top) {
        Avatar(look.name, look.color, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .background(
                    if (isActive) look.color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row {
                Text(look.name, style = MaterialTheme.typography.labelLarge, color = look.color, modifier = Modifier.weight(1f))
                Text(formatClock(u.start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(u.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename meeting") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

