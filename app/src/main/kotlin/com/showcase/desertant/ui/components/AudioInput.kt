package com.showcase.desertant.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.showcase.desertant.audio.AudioClip
import com.showcase.desertant.audio.AudioDecoder
import com.showcase.desertant.audio.AudioRecorder
import com.showcase.desertant.audio.ClipPlayer
import com.showcase.desertant.audio.formatSeconds
import kotlinx.coroutines.launch

@Composable
fun rememberClipPlayer(): ClipPlayer {
    val player = remember { ClipPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/** A colored time span drawn over a waveform (a word, a speaker turn). */
data class Span(val start: Double, val end: Double, val color: Color)

/**
 * Record from the microphone or pick an audio/video file, then show the clip's
 * waveform with a play button. [onClip] receives every new clip.
 */
@Composable
fun AudioInputCard(
    clip: AudioClip?,
    onClip: (AudioClip) -> Unit,
    player: ClipPlayer,
    title: String = "Audio input",
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { AudioRecorder() }
    var recording by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var elapsed by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun startRecording() {
        player.stop()
        recording = true
        error = null
        scope.launch {
            try {
                val take = recorder.record { l, s -> level = l; elapsed = s }
                if (take.samples.isNotEmpty()) onClip(take)
            } catch (e: Exception) {
                error = e.message
            } finally {
                recording = false
                level = 0f
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else error = "Microphone permission is needed to record."
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            player.stop()
            loading = true
            error = null
            scope.launch {
                try {
                    onClip(AudioDecoder.decode(context, uri))
                } catch (e: Exception) {
                    error = e.message ?: "This file could not be decoded."
                } finally {
                    loading = false
                }
            }
        }
    }

    SectionCard(title) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recording) {
                Button(
                    onClick = { recorder.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop · ${formatSeconds(elapsed)}")
                }
            } else {
                Button(
                    enabled = enabled && !loading,
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) startRecording() else permission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) {
                    Icon(Icons.Filled.Mic, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Record")
                }
            }
            OutlinedButton(
                enabled = enabled && !recording && !loading,
                onClick = { picker.launch(arrayOf("audio/*", "video/*")) },
            ) {
                Icon(Icons.Outlined.AudioFile, null)
                Spacer(Modifier.width(6.dp))
                Text("Pick file")
            }
        }
        if (recording) {
            LinearProgressIndicator(progress = { (level * 2).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        if (loading) {
            Text("Decoding…", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (clip != null && !recording) {
            ClipView(clip, player, id = "input")
            if (clip.truncated) {
                Text(
                    "Long file: only the first ${formatSeconds(AudioDecoder.MAX_SECONDS)} were decoded.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Waveform + play/stop for a clip, with optional highlighted [spans]. */
@Composable
fun ClipView(
    clip: AudioClip,
    player: ClipPlayer,
    id: String,
    spans: List<Span> = emptyList(),
    label: String = clip.label,
) {
    val playing by player.playing.collectAsState()
    val position by player.position.collectAsState()
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedButton(onClick = { player.toggle(id, clip.samples, clip.sampleRate) }) {
            Icon(if (playing == id) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$label · ${formatSeconds(clip.durationSec)} · ${clip.sampleRate / 1000.0} kHz",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Waveform(
        clip = clip,
        spans = spans,
        position = if (playing != null) position else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    )
}

@Composable
fun Waveform(
    clip: AudioClip,
    modifier: Modifier = Modifier,
    spans: List<Span> = emptyList(),
    position: Double? = null,
) {
    val envelope = remember(clip) { clip.envelope(400) }
    val color = MaterialTheme.colorScheme.primary
    val cursor = MaterialTheme.colorScheme.error
    Canvas(modifier) {
        val duration = clip.durationSec.coerceAtLeast(1e-6)
        spans.forEach { span ->
            val x0 = (span.start / duration * size.width).toFloat()
            val x1 = (span.end / duration * size.width).toFloat()
            drawRect(span.color.copy(alpha = 0.35f), Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(1f), size.height))
        }
        if (envelope.isEmpty()) return@Canvas
        val step = size.width / envelope.size
        val mid = size.height / 2
        val peak = (envelope.maxOrNull() ?: 1f).coerceAtLeast(1e-4f)
        envelope.forEachIndexed { i, v ->
            val h = (v / peak) * mid
            drawLine(color, Offset(i * step, mid - h), Offset(i * step, mid + h), strokeWidth = (step * 0.7f).coerceAtLeast(1f))
        }
        position?.let {
            val x = (it / duration * size.width).toFloat()
            drawLine(cursor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
fun AudioClipPlaceholder() {
    Text(
        "Record a few seconds of speech or pick an audio or video file to get started.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

