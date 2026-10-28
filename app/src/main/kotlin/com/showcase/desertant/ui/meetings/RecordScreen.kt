@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.meetings.RecorderService
import com.showcase.desertant.meetings.RecorderState

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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recording != null) "Recording" else "New recording") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    placeholder = { Text("Named from its topics if left empty") },
                    singleLine = true,
                    enabled = recording == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    formatClock((recording?.elapsedMs ?: 0) / 1000.0),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                )
                LevelMeter(recording?.levels.orEmpty(), Modifier.fillMaxWidth().height(72.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (recording != null) {
                        FilledTonalIconButton(
                            onClick = { if (recording.paused) RecorderService.resume(context) else RecorderService.pause(context) },
                            modifier = Modifier.size(64.dp),
                        ) { Icon(if (recording.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (recording.paused) "Resume" else "Pause") }
                    }
                    Box(
                        Modifier
                            .size(96.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .clickable { if (recording != null) RecorderService.stop(context) else start() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (recording != null) Icons.Filled.Stop else Icons.Filled.Mic,
                            if (recording != null) "Stop and save" else "Start recording",
                            tint = Color.White, modifier = Modifier.size(44.dp),
                        )
                    }
                }
                Text(
                    when {
                        denied -> "Burrow needs the microphone to record. You can allow it in Settings."
                        recording?.paused == true -> "Paused. Tap the square to stop and save."
                        recording != null -> "Keeps recording with the screen off. Tap the square to stop — notes are prepared on the phone afterwards."
                        else -> "Put the phone where everyone can be heard. Audio and notes never leave this device."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (recording == null) {
                    Text(
                        "Phone calls: Android doesn't let apps record calls. Turn on call recording in Samsung's Phone app (Settings → Record calls), then bring calls in from Meetings → Calls & recordings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelMeter(levels: List<Float>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.error
    val idle = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val bars = 60
        val w = size.width / bars
        val recent = levels.takeLast(bars)
        for (i in 0 until bars) {
            val v = recent.getOrNull(i - (bars - recent.size)) ?: 0f
            val h = (kotlin.math.sqrt(v) * size.height).coerceAtLeast(4f)
            drawRoundRect(
                if (v > 0f) color else idle,
                Offset(i * w + w * 0.2f, (size.height - h) / 2),
                Size(w * 0.6f, h),
                CornerRadius(w * 0.3f),
            )
        }
    }
}
