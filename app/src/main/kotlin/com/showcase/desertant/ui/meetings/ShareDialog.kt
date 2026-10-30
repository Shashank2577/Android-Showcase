package com.showcase.desertant.ui.meetings

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.showcase.desertant.app
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
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

    HandSheet(onDismiss) {
        Text("share these notes", style = Hand.headline.copy(fontSize = 34.sp), color = burrow.ink)
        Body("The notes go as text to the app you pick next. Burrow can first swap names, emails, phone numbers, addresses and IDs for placeholders — on this phone, before anything leaves it.", color = burrow.pencil)
        error?.let { Body(it, color = burrow.redPen) }
        InkButton("share without personal details", { share(true) }, Modifier.fillMaxWidth(), busy = working, trailing = Doodle.SHARE)
        PillButton("share as it is", { share(false) }, Modifier.fillMaxWidth(), enabled = !working)
        HandLink("cancel", onDismiss, Modifier.align(Alignment.CenterHorizontally))
    }
}
