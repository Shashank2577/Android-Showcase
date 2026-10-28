@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.people

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ai.desertant.gist.PostTopics
import ai.desertant.gist.RollupOptions
import ai.desertant.gist.channelTopics
import com.showcase.desertant.app
import com.showcase.desertant.audio.AudioRecorder
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.audio.resample
import com.showcase.desertant.core.languageName
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.people.People
import com.showcase.desertant.ui.common.Avatar
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.components.ScoreBar
import com.showcase.desertant.ui.meetings.decodeTopics
import com.showcase.desertant.ui.theme.seriesColor
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (embedded) "" else p.name) },
                navigationIcon = { if (!embedded) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete person") }, onClick = { menu = false; deleting = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.name, seriesColor(p.colorIndex), 72.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(if (p.isMe) "${p.name} (you)" else p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${mine.map { it.meetingId }.distinct().size} conversations · ${formatClock(totalTalk)} talking",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (languages.isNotEmpty()) Text("Speaks ${languages.joinToString(", ") { languageName(it) }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { VoiceCard(p.signatureCount) { samples -> people.enroll(id, samples) } }
                if (topics.isNotEmpty()) item {
                    ElevatedCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("What they talk about", style = MaterialTheme.typography.titleMedium)
                            topics.forEach { t -> ScoreBar(topicNames[t.slug] ?: t.slug, t.share, color = seriesColor(p.colorIndex)) }
                        }
                    }
                }
                val quotes = mine.mapNotNull { s -> s.sampleQuote?.let { it to byId[s.meetingId] } }.take(4)
                if (quotes.isNotEmpty()) item {
                    ElevatedCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("In their words", style = MaterialTheme.typography.titleMedium)
                            quotes.forEach { (q, m) ->
                                Column {
                                    Text("“$q”", style = MaterialTheme.typography.bodyLarge)
                                    m?.let { Text(it.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
                item { Text("Conversations", style = MaterialTheme.typography.titleMedium) }
                if (mine.isEmpty()) item { Text("Not linked to any meeting yet. Name them as a speaker in a meeting's notes.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(mine, key = { it.id }) { s ->
                    val m = byId[s.meetingId] ?: return@items
                    val share = s.talkSec / (m.durationSec.coerceAtLeast(1.0))
                    ElevatedCard(onClick = { open("meeting/${m.id}") }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${dateTime(context, m.createdAt)} · spoke ${formatClock(s.talkSec)} (${"%.0f".format(share * 100)}%)",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val t = decodeTopics(s.topicsJson)
                            if (t.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                t.forEach { Text(it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { scope.launch { people.rename(id, name) }; renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Their voice profile is removed. Meetings stay, with their turns shown as an unnamed speaker.") },
            confirmButton = { TextButton(onClick = { scope.launch { people.delete(id); deleting = false; if (!embedded) onBack() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

/** Shows how well the voice is known and records a dedicated sample to improve it. */
@Composable
private fun VoiceCard(samples: Int, onSample: suspend (FloatArray) -> Boolean) {
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
            message = if (ok) "Voice sample learned." else "Too little speech heard — try again and keep talking for 15 seconds."
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) record() }

    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voice", style = MaterialTheme.typography.titleMedium)
            Text(
                when (samples) {
                    0 -> "Not learned yet. Confirm them in a meeting, or record a sample here."
                    in 1..2 -> "Learned from $samples recording${if (samples > 1) "s" else ""} — suggestions get better with each one."
                    else -> "Learned from $samples recordings."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(progress = { (samples / 5f).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
            if (recording) {
                Text("Have them talk naturally… ${"%.0f".format(seconds)}s / 15s", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { (seconds / 15).toFloat().coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { recorder.stop() }) {
                    Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text(if (seconds >= 15) "Done" else "Stop")
                }
            } else {
                TextButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) record()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(6.dp)); Text("Record a voice sample") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
