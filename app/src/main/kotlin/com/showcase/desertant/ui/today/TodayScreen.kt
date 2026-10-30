package com.showcase.desertant.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.showcase.desertant.app
import com.showcase.desertant.core.languageName
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.meetings.RecorderService
import com.showcase.desertant.meetings.RecorderState
import com.showcase.desertant.photos.PhotoScanWorker
import com.showcase.desertant.privacy.LedgerEntry
import com.showcase.desertant.speech.SpeechPacks
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.photos.ReviewKind
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The home page: only what needs you right now, each as one hand-lettered
 * door. When nothing does, it says so.
 */
@Composable
fun TodayScreen(open: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val insights by app.photoInsights.collectAsStateWithLifecycle()
    val meetings by remember { app.db.meetings().all() }.collectAsState(initial = emptyList())
    val speakers by remember { app.db.speakers().all() }.collectAsState(initial = emptyList())
    val profiles by remember { app.db.profiles().all() }.collectAsState(initial = emptyList())
    val recorder by RecorderService.state.collectAsState()
    val ledgerVersion by app.ledger.version.collectAsState()
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PhotoScanWorker.UNIQUE) }.collectAsState(initial = emptyList())
    val scan = work.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    val now = LocalDateTime.now()
    val greeting = when (now.hour) {
        in 5..11 -> "good morning"
        in 12..17 -> "good afternoon"
        in 18..22 -> "good evening"
        else -> "hello, night owl"
    }
    val me = profiles.firstOrNull { it.isMe }?.name?.substringBefore(' ')
    val checkedToday = remember(ledgerVersion) { app.ledger.today(LedgerEntry.PHOTOS_CHECKED) }

    HandPage(
        wordmark = true,
        topEnd = { DoodleButton(Doodle.GEAR, "Settings", { open("settings") }); DoodleButton(Doodle.LOCK, "Privacy", { open("privacy") }) },
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (me != null) "$greeting, $me." else "$greeting.", Modifier.weight(1f, fill = false), style = Hand.headline.copy(fontSize = 44.sp), color = burrow.ink)
            DoodleIcon(if (now.hour in 6..17) Doodle.SUN else Doodle.MOON, null, Modifier.padding(start = 10.dp, bottom = 8.dp).rotate(-10f), size = 36.dp, color = burrow.pencil)
        }
        Meta(
            now.format(DateTimeFormatter.ofPattern("EEE d MMM")) + "  ·  " +
                if (checkedToday > 0) "$checkedToday photos checked, all on this phone" else "everything stays on this phone",
        )
        Spacer(Modifier.height(8.dp))
        Body("Only what needs you. Tap a line to deal with it.", color = burrow.pencil)
        Spacer(Modifier.height(20.dp))

        var doors = 0
        scan?.let { w ->
            val done = w.progress.getInt(PhotoScanWorker.KEY_DONE, 0)
            val total = w.progress.getInt(PhotoScanWorker.KEY_TOTAL, 0)
            Door("looking through your photos…", { open("photos") }, meta = if (total > 0) "$done of $total checked — keeps going if you leave" else "getting ready", leading = { DoodleIcon(Doodle.CAMERA, null, size = 28.dp) })
            doors++
        }
        (recorder as? RecorderState.Recording)?.let {
            Door("recording now — tap to see it", { open("record") }, mark = "recording now", leading = { DoodleIcon(Doodle.MIC, null, size = 28.dp) })
            doors++
        }

        val i = insights
        val tidyBytes = (i?.duplicateBytes ?: 0) + (i?.junkBytes ?: 0)
        when {
            scan == null && (i == null || i.analyzed == 0) -> {
                Door("let Burrow look through your photos", { open("photos") }, meta = "it finds extra shots and private things, on the phone", leading = { DoodleIcon(Doodle.CAMERA, null, size = 28.dp) })
                doors++
            }
            tidyBytes > 0 -> {
                val size = formatBytes(context, tidyBytes)
                Door("$size of extra shots to tidy", { open("photos/review/${ReviewKind.SIMILAR.name}") }, mark = size, leading = { DoodleIcon(Doodle.CAMERA, null, size = 28.dp) })
                doors++
            }
        }
        if ((i?.privateCount ?: 0) > 0) {
            Door("${i!!.privateCount} private photos in plain sight", { open("sweep") }, alert = true, leading = { DoodleIcon(Doodle.EYE_OFF, null, size = 28.dp) })
            doors++
        }

        val cutoff = System.currentTimeMillis() - 3 * 86_400_000L
        meetings.filter { it.createdAt > cutoff }.take(2).forEach { m ->
            when (m.status) {
                MeetingStatus.READY -> Door("${m.title} — notes are ready", { open("meeting/${m.id}") }, leading = { DoodleIcon(Doodle.BUBBLE, null, size = 28.dp) })
                MeetingStatus.QUEUED, MeetingStatus.PROCESSING -> Door("${m.title} — writing notes…", { open("meeting/${m.id}") }, meta = m.progress, leading = { DoodleIcon(Doodle.BUBBLE, null, size = 28.dp) })
                MeetingStatus.FAILED -> Door(
                    "${m.title} — needs you", { open("meeting/${m.id}") },
                    meta = SpeechPacks.route(m.language)?.takeIf { m.languageSupported == false }
                        ?.let { languageName(m.language).substringBefore(" (").let { n -> "it's in $n: get $n to write its notes" } }
                        ?: "the notes couldn't be written; open it to try again",
                )
                MeetingStatus.RECORDING -> Unit
            }
            if (m.status != MeetingStatus.RECORDING) doors++
        }

        val unnamed = speakers.count { it.profileId == null && !it.dismissed && it.words > 20 }
        if (unnamed > 0) {
            Door(if (unnamed == 1) "1 voice to name" else "$unnamed voices to name", { open("people") }, leading = { DoodleIcon(Doodle.PEOPLE, null, size = 28.dp) })
            doors++
        }

        if (doors == 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkedText("all quiet.", mark = "quiet", style = Hand.title)
                DoodleIcon(Doodle.HEART, null, Modifier.padding(start = 8.dp).rotate(8f), size = 26.dp, color = burrow.pencil)
            }
            Body("Nothing needs you right now. Record a talk, or mask a message below.", color = burrow.pencil)
        }

        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.4.dp, burrow.rule, RoundedCornerShape(16.dp))
                .clickable { open("mask") }
                .padding(18.dp),
        ) {
            Text("mask a message before you paste it into an AI →", style = Hand.door, color = burrow.ink)
            Meta("names, numbers and addresses swapped out on the phone, put back after", Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(burrow.smudge)
                .clickable { open("record") }
                .padding(18.dp),
        ) {
            Text("record a talk →", style = Hand.door, color = burrow.ink)
            Meta("a meeting, a lecture, a voice note — notes are written on the phone", Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(24.dp))
        HandDivider()
        Meta("To check a photo before sending it, share it to Burrow from any app.", Modifier.padding(top = 8.dp))
    }
}
