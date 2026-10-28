package com.showcase.desertant.ui.models

import ai.desertant.who.Diarization
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.showcase.desertant.audio.AudioClip
import com.showcase.desertant.audio.formatSeconds
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.ui.components.AudioClipPlaceholder
import com.showcase.desertant.ui.components.AudioInputCard
import com.showcase.desertant.ui.components.ClipView
import com.showcase.desertant.ui.components.LabeledSlider
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.Span
import com.showcase.desertant.ui.components.Stat
import com.showcase.desertant.ui.components.rememberClipPlayer
import com.showcase.desertant.ui.components.rememberRunner
import com.showcase.desertant.ui.theme.seriesColor

@Composable
fun WhoScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val runner = rememberRunner(ModelKind.WHO)
    val player = rememberClipPlayer()
    var clip by remember { mutableStateOf<AudioClip?>(null) }
    var raw by remember { mutableStateOf<Diarization?>(null) }
    var minShare by rememberSaveable { mutableFloatStateOf(0f) }
    val playing by player.playing.collectAsState()

    ModelScreen(ModelKind.WHO, onBack, runner) {
        AudioInputCard(clip, { clip = it; raw = null }, player, enabled = !runner.busy)
        SectionCard("Diarize") {
            val current = clip
            if (current == null) AudioClipPlaceholder()
            Button(
                enabled = current != null && !runner.busy,
                onClick = { if (current != null) runner.run { raw = hub.who.diarize(current.samples, current.sampleRate.toDouble()) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Who spoke when?") }
        }
        val c = clip
        raw?.let { full ->
            val d = if (minShare > 0f) full.keepingSpeakers(minShare.toDouble()) else full
            SectionCard("${d.speakerCount} speaker${if (d.speakerCount == 1) "" else "s"} · ${d.turns.size} turns") {
                LabeledSlider(
                    "Drop speakers below this share of the audio", minShare, { minShare = it }, 0f..0.3f,
                    "%.0f%%".format(minShare * 100),
                )
                if (c != null) ClipView(c, player, "full", spans = d.turns.map { Span(it.start, it.end, seriesColor(it.speaker)) })
                SpeakerTimeline(d)
                (0 until d.speakerCount).forEach { s ->
                    val talk = d.turns(s).sumOf { it.duration }
                    Stat("Speaker ${s + 1}", "${formatSeconds(talk)} · %.0f%%".format(talk / d.duration.coerceAtLeast(1e-6) * 100))
                }
            }
            SectionCard("Turns — tap to play") {
                d.turns.forEachIndexed { i, turn ->
                    val id = "turn$i"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(seriesColor(turn.speaker), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Speaker ${turn.speaker + 1}  ${formatSeconds(turn.start)} – ${formatSeconds(turn.end)}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (c != null) {
                            IconButton(onClick = { player.toggle(id, c.samples, c.sampleRate, turn.start, turn.end) }) {
                                Icon(if (playing == id) Icons.Filled.Stop else Icons.Filled.PlayArrow, "Play turn")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One lane per speaker, turns drawn as bars along the clip's duration. */
@Composable
fun SpeakerTimeline(d: Diarization) {
    val lane = 18.dp
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(lane * d.speakerCount.coerceAtLeast(1) + 4.dp * d.speakerCount),
    ) {
        val laneH = lane.toPx()
        val gap = 4.dp.toPx()
        val duration = d.duration.coerceAtLeast(1e-6)
        for (s in 0 until d.speakerCount) {
            val y = s * (laneH + gap)
            drawRoundRect(track, Offset(0f, y), Size(size.width, laneH), CornerRadius(laneH / 2))
            d.turns(s).forEach { t ->
                val x0 = (t.start / duration * size.width).toFloat()
                val x1 = (t.end / duration * size.width).toFloat()
                drawRoundRect(seriesColor(s), Offset(x0, y), Size((x1 - x0).coerceAtLeast(2f), laneH), CornerRadius(laneH / 3))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("0s", style = MaterialTheme.typography.labelSmall)
        Text(formatSeconds(d.duration), style = MaterialTheme.typography.labelSmall)
    }
}
