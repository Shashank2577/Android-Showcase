package com.showcase.desertant.ui.today

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.showcase.desertant.app
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.ask.TalkSummaries
import com.showcase.desertant.core.languageName
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSource
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.meetings.RecorderService
import com.showcase.desertant.meetings.RecorderState
import com.showcase.desertant.photos.PhotoScanWorker
import com.showcase.desertant.speech.SpeechPacks
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.ItemRow
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.meetings.TodoLine
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The home page. Its job: show what needs you, and let you start a recording.
 * To-dos that are due, talks that need you, private photos in plain sight and
 * voices to name are counted at the top; then the new notes; then the tools
 * (mask and photos). The one ink pill records.
 */
@Composable
fun TodayScreen(open: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val insights by app.photoInsights.collectAsStateWithLifecycle()
    val meetings by remember { app.db.meetings().all() }.collectAsState(initial = emptyList())
    val summarized by remember { app.db.meetings().summarized(80) }.collectAsState(initial = emptyList())
    val speakers by remember { app.db.speakers().all() }.collectAsState(initial = emptyList())
    val profiles by remember { app.db.profiles().all() }.collectAsState(initial = emptyList())
    val done by app.summaries.done.collectAsState()
    val recorder by RecorderService.state.collectAsState()
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PhotoScanWorker.UNIQUE) }.collectAsState(initial = emptyList())
    val scan = work.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    val now = LocalDateTime.now()
    val greeting = when (now.hour) {
        in 5..11 -> "good morning"
        in 12..17 -> "good afternoon"
        in 18..22 -> "good evening"
        else -> "hello, night owl"
    }
    val meProfile = profiles.firstOrNull { it.isMe }
    val me = meProfile?.name?.substringBefore(' ')
    val brief = remember(summarized, meetings, done, meProfile) {
        BriefData.build((summarized + meetings.filter { it.status == MeetingStatus.READY }).distinctBy { it.id }, done, meProfile?.name, LocalDate.now())
    }
    val todos = (brief.dueNow + brief.thisWeek)
    val shownTodos = todos.take(5)
    val failed = meetings.filter { it.status == MeetingStatus.FAILED && it.createdAt > System.currentTimeMillis() - 7 * 86_400_000L }.take(3)
    val privateCount = insights?.privateCount ?: 0
    val unnamed = speakers.count { it.profileId == null && !it.dismissed && it.words > 20 }
    val needs = todos.size + failed.size + (if (privateCount > 0) 1 else 0) + (if (unnamed > 0) 1 else 0)

    val since = LocalDate.now().minusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val writing = meetings.filter { it.status == MeetingStatus.QUEUED || it.status == MeetingStatus.PROCESSING }
    val newNotes = (writing + meetings.filter { it.status == MeetingStatus.READY && it.createdAt >= since }).distinctBy { it.id }.take(4)
    val recording = recorder as? RecorderState.Recording

    HandPage(
        wordmark = true,
        topEnd = { DoodleButton(Doodle.GEAR, "Settings", { open("settings") }); DoodleButton(Doodle.LOCK, "Privacy", { open("privacy") }) },
        bottom = {
            InkButton(
                if (recording != null) "recording · ${formatClock(recording.elapsedMs / 1000.0)}" else "record",
                { open("record") },
                Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                trailing = if (recording != null) Doodle.WAVE else Doodle.MIC,
            )
        },
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (me != null) "$greeting, $me" else greeting, Modifier.weight(1f, fill = false), style = Hand.headline, color = burrow.ink)
            DoodleIcon(if (now.hour in 6..17) Doodle.SUN else Doodle.MOON, null, Modifier.padding(start = 10.dp, bottom = 6.dp).rotate(-10f), size = 32.dp, color = burrow.pencil)
        }
        Caption(now.format(DateTimeFormatter.ofPattern("EEE d MMM")))

        if (needs == 0) {
            Spacer(Modifier.height(24.dp))
            MarkedText("all quiet.", mark = "quiet", style = Hand.title)
            Body("Nothing needs you right now.", Modifier.padding(top = 4.dp))
        } else {
            val count = if (needs == 1) "1 thing" else "$needs things"
            SectionHead("$count ${if (needs == 1) "needs" else "need"} you", mark = count, first = true)
            shownTodos.forEach { b ->
                TodoLine(b.todo, ticked = false, onTick = { app.summaries.toggleDone(b.talk.id, b.index) }, onOpen = { open("meeting/${b.talk.id}") }, showTime = false)
            }
            if (todos.size > shownTodos.size) Caption("and ${todos.size - shownTodos.size} more due this week, in your talks' notes", Modifier.padding(bottom = 4.dp))
            failed.forEach { m ->
                ItemRow(
                    m.title,
                    meta = SpeechPacks.route(m.language)?.takeIf { m.languageSupported == false }
                        ?.let { languageName(m.language).substringBefore(" (").let { n -> "It's in $n. Get $n to write its notes." } }
                        ?: "The notes couldn't be written. Open it to try again.",
                    onClick = { open("meeting/${m.id}") },
                    leading = { DoodleIcon(Doodle.EXCLAIM, "needs you", size = 24.dp, color = burrow.redPen, stroke = 2.6.dp) },
                )
            }
            if (privateCount > 0) {
                ItemRow(
                    if (privateCount == 1) "1 private photo in plain sight" else "$privateCount private photos in plain sight",
                    meta = "IDs, cards and more. Move them to the vault.",
                    onClick = { open("sweep") },
                    leading = { DoodleIcon(Doodle.EXCLAIM, "needs you", size = 24.dp, color = burrow.redPen, stroke = 2.6.dp) },
                )
            }
            if (unnamed > 0) {
                ItemRow(
                    if (unnamed == 1) "1 voice to name" else "$unnamed voices to name",
                    meta = "Name each once and Burrow knows them next time.",
                    onClick = { open("people") },
                    leading = { DoodleIcon(Doodle.PEOPLE, null, size = 24.dp) },
                )
            }
        }

        if (newNotes.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HandDivider()
            SectionHead("new notes", first = true)
            newNotes.forEach { m -> NoteRow(m) { open("meeting/${m.id}") } }
        }

        Spacer(Modifier.height(16.dp))
        HandDivider(seed = 4)
        SectionHead("tools", first = true)
        Door("mask a message", { open("mask") }, meta = "Swap names and numbers out before you paste text into an AI.", leading = { DoodleIcon(Doodle.MASK, null, size = 24.dp) })
        val i = insights
        val tidy = (i?.duplicateBytes ?: 0) + (i?.junkBytes ?: 0)
        Door(
            "photos", { open("photos") },
            meta = when {
                scan != null -> {
                    val d = scan.progress.getInt(PhotoScanWorker.KEY_DONE, 0)
                    val t = scan.progress.getInt(PhotoScanWorker.KEY_TOTAL, 0)
                    if (t > 0) "Looking through them: $d of $t." else "Getting ready to look through them."
                }
                tidy > 0 -> "${formatBytes(context, tidy)} of extra shots to tidy, and the vault."
                else -> "Tidy up, a private sweep, the vault. To check a photo before sending it, share it to Burrow."
            },
            leading = { DoodleIcon(Doodle.CAMERA, null, size = 24.dp) },
        )
    }
}

/** A new talk: its name, what the notes hold, or how far along they are. */
@Composable
private fun NoteRow(m: MeetingEntity, onClick: () -> Unit) {
    val s = remember(m.summaryJson) { TalkSummaries.decode(m.summaryJson) }
    val writing = m.status == MeetingStatus.QUEUED || m.status == MeetingStatus.PROCESSING
    val length = m.durationSec.takeIf { it > 0 }?.let { "${(it / 60).toInt().coerceAtLeast(1)} min" }
    ItemRow(
        m.title,
        meta = when {
            writing -> (m.progress ?: "Waiting to be written up")
            s != null -> listOfNotNull(
                s.decisions.size.takeIf { it > 0 }?.let { if (it == 1) "1 decision" else "$it decisions" },
                s.todos.size.takeIf { it > 0 }?.let { if (it == 1) "1 to-do" else "$it to-dos" },
                length,
            ).joinToString(" · ").ifEmpty { "Summary ready" }
            else -> listOfNotNull("No summary yet", length).joinToString(" · ")
        },
        onClick = onClick,
        leading = if (m.source == MeetingSource.CALL) ({ DoodleIcon(Doodle.PHONE, "Call", size = 24.dp, color = burrow.pencil) }) else null,
        below = if (writing) ({ HandProgress(null, Modifier.padding(top = 6.dp)) }) else null,
    )
}
