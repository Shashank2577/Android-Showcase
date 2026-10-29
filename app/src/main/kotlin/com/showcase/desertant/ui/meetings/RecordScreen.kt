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
import com.showcase.desertant.ui.hand.Doodle
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

@Composable
fun RecordScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val state by RecorderService.state.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var wasRecording by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) RecorderService.start(context, title) else denied = true
    }
    fun start() {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notify = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (mic && notify) RecorderService.start(context, title)
        else permissions.launch(
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.RECORD_AUDIO),
        )
    }

    // Leave once a recording we were showing has been saved.
    LaunchedEffect(state) {
        if (state is RecorderState.Recording) wasRecording = true
        else if (wasRecording) onSaved()
    }

    val recording = state as? RecorderState.Recording
    HandPage(onBack = onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (recording == null) {
                HandField(title, { title = it }, placeholder = "a name, if you like — or Burrow names it from the topics", singleLine = true)
            } else if (title.isNotBlank()) {
                Text(title, style = Hand.title, color = burrow.ink, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                VoiceRing(recording?.levels.orEmpty(), active = recording != null && !recording.paused, Modifier.matchParentSize())
                Text(formatClock((recording?.elapsedMs ?: 0) / 1000.0), style = Hand.display.copy(fontSize = 76.sp, lineHeight = 76.sp), color = burrow.ink)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    denied -> "Burrow needs the microphone to record."
                    recording?.paused == true -> "paused."
                    recording != null -> "listening… stays on this phone"
                    else -> "ready when you are."
                },
                style = Hand.door,
                color = if (denied) burrow.redPen else burrow.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
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
            Spacer(Modifier.height(28.dp))
            Meta(
                if (recording != null) "Keeps going with the screen off. Notes are written on the phone after you stop."
                else "Tell people you're recording. Put the phone where everyone can be heard.",
                textAlign = TextAlign.Center,
            )
            if (recording == null) {
                Spacer(Modifier.height(10.dp))
                Meta(
                    "Calls: Android doesn't let apps record calls. Turn on call recording in Samsung's Phone app, then bring calls in from talks.",
                    textAlign = TextAlign.Center,
                )
            }
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
