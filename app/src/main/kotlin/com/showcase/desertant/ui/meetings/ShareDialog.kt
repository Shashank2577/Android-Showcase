package com.showcase.desertant.ui.meetings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.showcase.desertant.app
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.ui.common.dateTime
import kotlinx.coroutines.launch

/** Share the notes as text, optionally with names, numbers and addresses redacted on the device first. */
@Composable
fun ShareDialog(m: MeetingEntity, speakers: List<MeetingSpeakerEntity>, profiles: List<ProfileEntity>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun share(redact: Boolean) = scope.launch {
        working = true
        try {
            val hub = context.app.hub
            if (redact) hub.ensureReady(ModelKind.REDACT)
            val clean: suspend (String) -> String = { t -> if (redact) hub.redact.redaction(t).redactedText else t }
            val text = buildString {
                appendLine(m.title)
                appendLine("${dateTime(context, m.createdAt)} · ${formatClock(m.durationSec)}")
                val topics = decodeTopics(m.topicsJson)
                if (topics.isNotEmpty()) appendLine("Topics: ${topics.joinToString(", ") { it.name }}")
                val names = speakers.map { lookFor(it.index, speakers, profiles).name }
                if (names.isNotEmpty()) appendLine("Speakers: ${if (redact) "${names.size} people" else names.joinToString(", ")}")
                val highlights = decodeMoments(m.highlightsJson)
                if (highlights.isNotEmpty()) {
                    appendLine(); appendLine("Highlights")
                    highlights.forEach { appendLine("• ${clean(it.text)}") }
                }
                val follow = decodeMoments(m.followUpsJson)
                if (follow.isNotEmpty()) {
                    appendLine(); appendLine("Follow-ups")
                    follow.forEach { appendLine("☐ ${clean(it.text)}") }
                }
                appendLine(); appendLine("Transcript")
                decodeUtterances(m.utterancesJson).forEach { u ->
                    val who = if (redact) "Speaker ${(u.speaker ?: 0) + 1}" else lookFor(u.speaker, speakers, profiles).name
                    appendLine("[${formatClock(u.start)}] $who: ${clean(u.text)}")
                }
            }
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, m.title).putExtra(Intent.EXTRA_TEXT, text),
                    "Share meeting notes",
                ),
            )
            onDismiss()
        } catch (e: Exception) {
            error = e.message
        } finally {
            working = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sharing sends text to the app you pick. Redacting replaces names, emails, phone numbers, addresses and IDs on this phone before anything leaves it.")
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { share(true) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Share with personal details removed") }
                OutlinedButton(onClick = { share(false) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Share as is") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
