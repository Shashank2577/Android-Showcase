package com.showcase.desertant.ui.meetings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.people.People
import com.showcase.desertant.people.ProfileMatcher
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.VoiceSignature
import com.showcase.desertant.ui.common.Avatar
import com.showcase.desertant.ui.theme.seriesColor
import kotlinx.coroutines.launch

/** Link a meeting voice to a person: someone known (ranked by voice match), you, or someone new. */
@Composable
fun AssignSpeakerDialog(speaker: MeetingSpeakerEntity, profiles: List<ProfileEntity>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val people = remember { People(context) }
    val scores = remember(speaker.id, profiles) {
        speaker.signatureJson?.let { json ->
            val sig = runCatching { StoreJson.decodeFromString(VoiceSignature.serializer(), json) }.getOrNull()
            sig?.let { ProfileMatcher(profiles).scores(it).toMap() }
        }.orEmpty()
    }
    val ranked = profiles.sortedByDescending { scores[it.id] ?: -1.0 }
    var choice by remember { mutableStateOf<Long?>(speaker.profileId ?: speaker.suggestedProfileId) }
    var newName by remember { mutableStateOf("") }
    var isMe by remember { mutableStateOf(profiles.none { it.isMe }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who is Speaker ${speaker.index + 1}?") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                speaker.sampleQuote?.let {
                    Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ranked.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { choice = p.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choice == p.id, onClick = { choice = p.id })
                        Avatar(p.name, seriesColor(p.colorIndex), 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (p.isMe) "${p.name} (you)" else p.name, modifier = Modifier.weight(1f))
                        scores[p.id]?.let { Text("${"%.0f".format(it * 100)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (ranked.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = choice == NEW, onClick = { choice = NEW })
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it; choice = NEW },
                        label = { Text("New person") }, singleLine = true,
                    )
                }
                if (choice == NEW) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isMe = !isMe }) {
                        Checkbox(checked = isMe, onCheckedChange = { isMe = it })
                        Text("This is me")
                    }
                }
                Text(
                    "Each person you confirm teaches Burrow their voice, so it can suggest them in future meetings.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = (choice == NEW && newName.isNotBlank()) || (choice != null && choice != NEW),
                onClick = {
                    val c = choice
                    scope.launch {
                        if (c == NEW) people.createFromSpeaker(speaker.id, newName, isMe) else if (c != null) people.assign(speaker.id, c)
                        onDismiss()
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (speaker.profileId != null) TextButton(onClick = { scope.launch { people.unassign(speaker.id); onDismiss() } }) { Text("Unlink") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private const val NEW = -1L
