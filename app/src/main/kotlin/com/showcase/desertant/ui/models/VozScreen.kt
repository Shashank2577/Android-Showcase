@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.models

import ai.desertant.ear.Detection
import ai.desertant.voz.Transcription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.showcase.desertant.audio.AudioClip
import com.showcase.desertant.audio.formatSeconds
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.VOZ_LANGUAGES
import com.showcase.desertant.core.languageName
import com.showcase.desertant.ui.components.AudioClipPlaceholder
import com.showcase.desertant.ui.components.AudioInputCard
import com.showcase.desertant.ui.components.ClipView
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.Span
import com.showcase.desertant.ui.components.Stat
import com.showcase.desertant.ui.components.rememberClipPlayer
import com.showcase.desertant.ui.components.rememberRunner
import com.showcase.desertant.ui.components.shareText
import com.showcase.desertant.ui.theme.seriesColor

@Composable
fun VozScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val context = LocalContext.current
    val runner = rememberRunner(ModelKind.VOZ)
    val player = rememberClipPlayer()
    var clip by remember { mutableStateOf<AudioClip?>(null) }
    var checkLanguage by rememberSaveable { mutableStateOf(true) }
    var language by remember { mutableStateOf<Detection?>(null) }
    var result by remember { mutableStateOf<Transcription?>(null) }
    val playing by player.playing.collectAsState()

    ModelScreen(ModelKind.VOZ, onBack, runner) {
        AudioInputCard(clip, { clip = it; result = null; language = null }, player, enabled = !runner.busy)
        SectionCard("Transcribe") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Check the language with Ear first", modifier = Modifier.weight(1f))
                Switch(checked = checkLanguage, onCheckedChange = { checkLanguage = it })
            }
            Text(
                "Voz doesn't detect language and produces confident nonsense on languages it wasn't trained on. Ear (+10 MB) catches that.",
                style = MaterialTheme.typography.bodySmall,
            )
            val current = clip
            if (current == null) AudioClipPlaceholder()
            Button(
                enabled = current != null && !runner.busy,
                onClick = {
                    if (current != null) runner.run {
                        language = null
                        if (checkLanguage) {
                            hub.ensureReady(ModelKind.EAR)
                            language = hub.ear.identify(current.samples, current.sampleRate.toDouble())
                        }
                        result = hub.voz.transcribe(current.samples, current.sampleRate.toDouble())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Transcribe on device") }
        }
        language?.let { d ->
            val supported = d.language in VOZ_LANGUAGES
            SectionCard("Language (Ear)") {
                Stat("Detected", "${languageName(d.language)} · %.0f%%".format(d.confidence * 100))
                Stat("Reliable", if (d.isReliable) "Yes" else "No")
                Text(
                    if (supported) "Voz supports this language."
                    else "Voz isn't trained on this language — expect a wrong transcript.",
                    color = if (supported) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val c = clip
        result?.let { t ->
            SectionCard("Transcript") {
                SelectionContainer { Text(t.text.ifBlank { "(no speech found)" }, style = MaterialTheme.typography.bodyLarge) }
                runner.lastMillis?.let { ms ->
                    Stat("Audio", formatSeconds(t.duration))
                    Stat("Processing (incl. language check)", "$ms ms")
                    if (ms > 0) Stat("Speed", "%.1f× real time".format(t.duration * 1000 / ms))
                }
                OutlinedButton(onClick = { shareText(context, "Transcript", t.text) }) {
                    Icon(Icons.Outlined.Share, null)
                    Text("  Share")
                }
            }
            if (c != null && t.words.isNotEmpty()) {
                SectionCard("Words with timestamps — tap to hear one") {
                    ClipView(
                        c, player, id = "full",
                        spans = t.words.mapIndexed { i, w -> Span(w.start, w.end, seriesColor(i % 2)) },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        t.words.forEachIndexed { i, w ->
                            val id = "word$i"
                            FilterChip(
                                selected = playing == id,
                                onClick = { player.toggle(id, c.samples, c.sampleRate, w.start, w.end + 0.08) },
                                label = { Text("${w.text}  ${"%.2f".format(w.start)}s") },
                            )
                        }
                    }
                }
            }
        }
    }
}
