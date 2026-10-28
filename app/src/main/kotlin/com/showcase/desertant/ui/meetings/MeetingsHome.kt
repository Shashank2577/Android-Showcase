@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.meetings

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalContext
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
import com.showcase.desertant.ui.common.Avatar
import com.showcase.desertant.ui.common.EmptyState
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.shell.LocalWideLayout
import com.showcase.desertant.ui.theme.seriesColor
import kotlinx.coroutines.launch

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
                Toast.makeText(context, "Couldn't import that file: ${e.message ?: "unreadable"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meetings") },
                actions = { IconButton(onClick = { open("settings") }) { Icon(Icons.Outlined.Settings, "Settings") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { open("record") },
                icon = { Icon(if (recorder is RecorderState.Recording) Icons.Filled.FiberManualRecord else Icons.Outlined.Mic, null) },
                text = { Text(if (recorder is RecorderState.Recording) "Recording…" else "Record") },
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            val list = @Composable { modifier: Modifier ->
                LazyColumn(
                    modifier,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    (recorder as? RecorderState.Recording)?.let { r ->
                        item {
                            Card(onClick = { open("record") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(10.dp))
                                    Text(if (r.paused) "Paused · ${formatClock(r.elapsedMs / 1000.0)}" else "Recording · ${formatClock(r.elapsedMs / 1000.0)}", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            placeholder = { Text("Search every transcript") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }, label = { Text("Import a file") }, leadingIcon = { Icon(Icons.Outlined.AudioFile, null) })
                            AssistChip(onClick = { open("recordings") }, label = { Text("Calls & recordings") }, leadingIcon = { Icon(Icons.Outlined.Call, null) })
                        }
                    }
                    val m = meetings
                    if (m != null && m.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Outlined.Forum,
                                if (query.isBlank()) "Your conversations, understood" else "No matches",
                                if (query.isBlank()) "Record a meeting or import a call. Burrow transcribes it, works out who said what, pulls out the topics, highlights and follow-ups, and remembers the people — all on this phone."
                                else "Nothing in your transcripts mentions “$query”.",
                            )
                        }
                    }
                    items(m.orEmpty(), key = { it.id }) { meeting ->
                        MeetingCard(
                            meeting,
                            people = speakers.filter { it.meetingId == meeting.id }.mapNotNull { s -> profiles.firstOrNull { it.id == s.profileId } },
                            highlight = query.takeIf { it.isNotBlank() },
                            selected = wide && selected == meeting.id,
                        ) { if (wide) selected = meeting.id else open("meeting/${meeting.id}") }
                    }
                }
            }
            if (wide) {
                list(Modifier.weight(0.42f).fillMaxHeight())
                VerticalDivider()
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    val id = selected ?: meetings?.firstOrNull()?.id
                    if (id != null) MeetingDetailContent(id, open = open, embedded = true)
                    else EmptyState(Icons.Outlined.Forum, "Pick a meeting", "Its notes open here.")
                }
            } else {
                list(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun MeetingCard(meeting: MeetingEntity, people: List<ProfileEntity>, highlight: String?, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val topics = remember(meeting.topicsJson) { decodeTopics(meeting.topicsJson) }
    ElevatedCard(
        onClick = onClick,
        colors = if (selected) CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.elevatedCardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (meeting.source == MeetingSource.CALL) Icons.Outlined.Call else Icons.Outlined.Mic, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(meeting.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(
                "${dateTime(context, meeting.createdAt)} · ${formatClock(meeting.durationSec)}" +
                    (if (meeting.speakerCount > 0) " · ${meeting.speakerCount} speakers" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (meeting.status) {
                MeetingStatus.READY -> {
                    if (topics.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            topics.take(3).forEach { TopicPill(it.name) }
                        }
                    }
                    if (highlight != null) {
                        snippet(meeting.transcriptText, highlight)?.let {
                            Text("…$it…", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (people.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            people.distinctBy { it.id }.take(5).forEach { Avatar(it.name, seriesColor(it.colorIndex), 28.dp) }
                        }
                    }
                }
                MeetingStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Couldn't process — open to retry", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (meeting.status == MeetingStatus.RECORDING) "Recording…" else meeting.progress ?: "Waiting to be processed",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun snippet(text: String, query: String): String? {
    val at = text.indexOf(query, ignoreCase = true)
    if (at < 0) return null
    return text.substring((at - 40).coerceAtLeast(0), (at + query.length + 60).coerceAtMost(text.length))
}

