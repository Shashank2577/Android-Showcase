package com.showcase.desertant.ui.meetings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.meetings.RecorderService
import com.showcase.desertant.meetings.RecorderState
import androidx.compose.runtime.rememberCoroutineScope
import com.showcase.desertant.app
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.SheetTitle
import com.showcase.desertant.ui.hand.Steps
import com.showcase.desertant.ui.hand.Warning
import kotlinx.coroutines.launch
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Record: the timer, one sentence, pause and stop. Naming comes after you
 * stop (or Burrow names it from the topics). Calls are explained in a sheet.
 */
@Composable
fun RecordScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val db = context.app.db
    val scope = rememberCoroutineScope()
    val state by RecorderService.state.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var savedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastId by rememberSaveable { mutableStateOf<Long?>(null) }
    var denied by remember { mutableStateOf(false) }
    var calls by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) RecorderService.start(context, null) else denied = true
    }
    fun start() {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notify = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        // The microphone (and the notification that keeps a long recording going) are asked for here, the first time.
        if (mic && notify) RecorderService.start(context, null)
        else permissions.launch(
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.RECORD_AUDIO),
        )
    }

    // Once a recording we were showing has been saved, ask for a name.
    LaunchedEffect(state) {
        val r = state as? RecorderState.Recording
        if (r != null) lastId = r.meetingId
        else if (lastId != null && savedId == null) savedId = lastId
    }

    val recording = state as? RecorderState.Recording
    val saved = savedId
    if (saved != null && recording == null) {
        HandPage(
            onBack = onSaved,
            bottom = {
                InkButton(
                    "save",
                    { scope.launch { if (title.isNotBlank()) db.meetings().rename(saved, title.trim()); onSaved() } },
                    Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                )
                HandLink("skip", onSaved)
            },
        ) {
            PageIntro("name it", "Or skip, and Burrow names it from what was said.")
            HandField(title, { title = it }, placeholder = "Q3 roadmap sync", singleLine = true)
            Spacer(Modifier.height(16.dp))
            Body("The notes are being written on this phone. You'll find them in talks.", color = burrow.pencil)
        }
        return
    }

    HandPage(onBack = onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                VoiceRing(recording?.levels.orEmpty(), active = recording != null && !recording.paused, Modifier.matchParentSize())
                Text(formatClock((recording?.elapsedMs ?: 0) / 1000.0), style = Hand.display, color = burrow.ink)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    recording?.paused == true -> "paused"
                    recording != null -> "listening"
                    else -> "ready when you are"
                },
                style = Hand.door,
                color = burrow.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (recording != null) {
                    DoodleButton(
                        if (recording.paused) Doodle.PLAY else Doodle.PAUSE,
                        if (recording.paused) "Resume" else "Pause",
                        { if (recording.paused) RecorderService.resume(context) else RecorderService.pause(context) },
                        size = 30.dp,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(if (recording != null) burrow.ink else burrow.redPen)
                            .clickable(role = Role.Button) { if (recording != null) RecorderService.stop(context) else start() },
                        contentAlignment = Alignment.Center,
                    ) {
                        DoodleIcon(
                            if (recording != null) Doodle.STOP else Doodle.MIC,
                            if (recording != null) "Stop and keep" else "Start recording",
                            size = 40.dp, color = burrow.paper, stroke = 2.6.dp,
                        )
                    }
                    Text(if (recording != null) "stop & keep" else "record", style = Hand.label, color = burrow.ink, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        when {
            denied -> Warning("Burrow needs the microphone to record. Allow it in Android's settings for Burrow.")
            recording != null -> Body("It keeps going with the screen off. The notes are written on this phone after you stop.")
            else -> Body("Tell people you're recording.")
        }
        if (recording == null) HandLink("recording a phone call?", { calls = true })
    }
    if (calls) {
        HandSheet({ calls = false }) {
            SheetTitle("recording a call")
            Body("Android doesn't let apps record calls. Many phones' own Phone apps can: Samsung, Xiaomi, OnePlus, Oppo, Realme, Vivo, Honor, Huawei and Motorola.")
            Steps(
                listOf(
                    "On a Samsung: Phone → ⋮ → Settings → Record calls, and turn it on.",
                    "Make the call. Your phone saves the recording.",
                    "In Burrow: talks → import → a phone call.",
                ),
            )
            Body("Or share any recording to Burrow from another app.", color = burrow.pencil)
            InkButton("got it", { calls = false }, Modifier.fillMaxWidth())
        }
    }
}

/** A loose hand-drawn ring that swells with the voice. */
@Composable
private fun VoiceRing(levels: List<Float>, active: Boolean, modifier: Modifier) {
    val ink = burrow.ink
    val faint = burrow.rule
    val recent = levels.takeLast(48)
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val base = size.minDimension * 0.38f
        for (ring in 0..1) {
            val p = Path()
            val steps = 72
            for (i in 0..steps) {
                val a = (2 * PI * i / steps).toFloat() - 1.4f + ring * 0.6f
                val v = if (active && recent.isNotEmpty()) sqrt(recent[(i * recent.size / (steps + 1)).coerceIn(0, recent.lastIndex)]) else 0f
                val r = base * (1f + ring * 0.06f) + v * size.minDimension * 0.07f + sin(i * 0.9f + ring) * 1.5f
                val x = c.x + cos(a) * r
                val y = c.y + sin(a) * r
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, if (ring == 0) ink else faint, style = Stroke(width = if (ring == 0) 2.4.dp.toPx() else 1.4.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
