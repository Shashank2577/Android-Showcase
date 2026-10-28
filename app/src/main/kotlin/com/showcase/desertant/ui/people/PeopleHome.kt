@file:OptIn(ExperimentalMaterial3Api::class)

package com.showcase.desertant.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.people.People
import com.showcase.desertant.ui.common.Avatar
import com.showcase.desertant.ui.common.EmptyState
import com.showcase.desertant.ui.common.relativeDay
import com.showcase.desertant.ui.meetings.AssignSpeakerDialog
import com.showcase.desertant.ui.meetings.decodeTopics
import com.showcase.desertant.ui.shell.LocalWideLayout
import com.showcase.desertant.ui.theme.seriesColor
import kotlinx.coroutines.launch

@Composable
fun PeopleHome(open: (String) -> Unit) {
    val context = LocalContext.current
    val db = context.app.db
    val profiles by remember { db.profiles().all() }.collectAsState(initial = null)
    val speakers by remember { db.speakers().all() }.collectAsState(initial = emptyList())
    val meetings by remember { db.meetings().all() }.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf<MeetingSpeakerEntity?>(null) }
    val wide = LocalWideLayout.current
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    val ready = meetings.filter { it.status == MeetingStatus.READY }.associateBy { it.id }
    val unnamed = speakers.filter { it.profileId == null && it.meetingId in ready && it.words >= 5 }
        .sortedByDescending { ready[it.meetingId]?.createdAt ?: 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People") },
                actions = {
                    IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.PersonAdd, "Add person") }
                    IconButton(onClick = { open("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            val list = @Composable { modifier: Modifier ->
                LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (unnamed.isNotEmpty()) {
                        item {
                            Text("Voices to name", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tell Burrow who these are once — it learns their voice and suggests them next time.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(unnamed.take(6), key = { "u${it.id}" }) { s ->
                            UnnamedVoice(s, ready[s.meetingId], profiles.orEmpty().firstOrNull { it.id == s.suggestedProfileId }) { assigning = s }
                        }
                    }
                    item { Text("Your people", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    val p = profiles
                    if (p != null && p.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Outlined.Groups, "Know who said what",
                                "Name the speakers in a meeting and Burrow builds a profile for each person: their voice, what they talk about, and every conversation you've had — kept only on this phone.",
                            )
                        }
                    }
                    items(p.orEmpty(), key = { it.id }) { person ->
                        PersonRow(person, speakers.filter { it.profileId == person.id }, ready, selected = wide && selected == person.id) {
                            if (wide) selected = person.id else open("person/${person.id}")
                        }
                    }
                }
            }
            if (wide) {
                list(Modifier.weight(0.42f).fillMaxHeight())
                VerticalDivider()
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    val id = selected ?: profiles?.firstOrNull()?.id
                    if (id != null) PersonContent(id, open = open, embedded = true)
                    else EmptyState(Icons.Outlined.Groups, "No one yet", "People you name in meetings show up here.")
                }
            } else list(Modifier.fillMaxSize())
        }
    }
    if (adding) AddPersonDialog { adding = false }
    assigning?.let { s -> AssignSpeakerDialog(s, profiles.orEmpty()) { assigning = null } }
}

@Composable
private fun UnnamedVoice(s: MeetingSpeakerEntity, meeting: MeetingEntity?, suggestion: ProfileEntity?, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.RecordVoiceOver, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    suggestion?.let { "Speaker ${s.index + 1} — sounds like ${it.name}" } ?: "Speaker ${s.index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${meeting?.title ?: "Meeting"} · ${formatClock(s.talkSec)} of talking",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                s.sampleQuote?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            TextButton(onClick = onClick) { Text("Name") }
        }
    }
}

@Composable
private fun PersonRow(p: ProfileEntity, appearances: List<MeetingSpeakerEntity>, meetings: Map<Long, MeetingEntity>, selected: Boolean, onClick: () -> Unit) {
    val talk = appearances.sumOf { it.talkSec }
    val last = appearances.mapNotNull { meetings[it.meetingId]?.createdAt }.maxOrNull()
    val topics = appearances.flatMap { decodeTopics(it.topicsJson) }.groupBy { it.name }
        .entries.sortedByDescending { e -> e.value.sumOf { it.score } }.take(3).map { it.key }
    ElevatedCard(
        onClick = onClick,
        colors = if (selected) CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.elevatedCardColors(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, seriesColor(p.colorIndex), 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (p.isMe) "${p.name} (you)" else p.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${appearances.map { it.meetingId }.distinct().size} conversations · ${formatClock(talk)} talking" +
                        (last?.let { " · ${relativeDay(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (topics.isNotEmpty()) Text(topics.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AddPersonDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var me by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a person") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(me, { me = it })
                    Text("This is me")
                }
                Text("Open their page afterwards to teach Burrow their voice with a short sample.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { scope.launch { People(context).create(name, me); onDismiss() } }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
