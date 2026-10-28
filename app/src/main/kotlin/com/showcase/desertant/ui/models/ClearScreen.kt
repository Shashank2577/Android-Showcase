package com.showcase.desertant.ui.models

import ai.desertant.clear.LoudnessPreset
import ai.desertant.clear.Mastering
import ai.desertant.clear.Options
import ai.desertant.clear.Result
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.showcase.desertant.audio.AudioClip
import com.showcase.desertant.audio.WavFile
import com.showcase.desertant.audio.formatSeconds
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.ui.components.AudioClipPlaceholder
import com.showcase.desertant.ui.components.AudioInputCard
import com.showcase.desertant.ui.components.ChoiceChips
import com.showcase.desertant.ui.components.ClipView
import com.showcase.desertant.ui.components.LabeledSlider
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.Stat
import com.showcase.desertant.ui.components.prettify
import com.showcase.desertant.ui.components.rememberClipPlayer
import com.showcase.desertant.ui.components.rememberRunner
import kotlinx.coroutines.launch

/** Loudness targets offered in the UI; null turns mastering off. */
private val PRESETS: List<LoudnessPreset?> = LoudnessPreset.entries + null
private val OUTPUT_RATES = listOf(16_000, 24_000, 44_100, 48_000)

@Composable
fun ClearScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = rememberRunner(ModelKind.CLEAR)
    val player = rememberClipPlayer()
    var clip by remember { mutableStateOf<AudioClip?>(null) }
    var strength by rememberSaveable { mutableFloatStateOf(1f) }
    var preset by remember { mutableStateOf<LoudnessPreset?>(LoudnessPreset.APPLE_PODCASTS) }
    var outputRate by rememberSaveable { mutableStateOf(48_000) }
    var result by remember { mutableStateOf<Result?>(null) }

    ModelScreen(ModelKind.CLEAR, onBack, runner) {
        AudioInputCard(clip, { clip = it; result = null }, player, enabled = !runner.busy)
        SectionCard("Enhancement") {
            LabeledSlider("Strength", strength, { strength = it }, 0f..1f, "%.0f%%".format(strength * 100))
            Text("Loudness target", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(PRESETS, preset, { preset = it }) { p ->
                p?.let { "${prettify(it.name.lowercase())} (${it.integratedLufs.toInt()} LUFS)" } ?: "Off"
            }
            Text("Output sample rate", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(OUTPUT_RATES, outputRate, { outputRate = it }) { "${it / 1000.0} kHz" }
            val current = clip
            if (current == null) AudioClipPlaceholder()
            Button(
                enabled = current != null && !runner.busy,
                onClick = {
                    if (current != null) runner.run {
                        val mastering = preset?.let { Mastering.of(it) } ?: Mastering.BYPASS
                        result = hub.clear.enhance(
                            current.samples,
                            current.sampleRate.toDouble(),
                            Options(strength = strength.toDouble(), mastering = mastering, sampleRate = outputRate.toDouble()),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Clean up audio") }
        }
        val c = clip
        result?.let { r ->
            val enhanced = remember(r) { AudioClip(r.samples, r.sampleRate.toInt(), "Enhanced") }
            SectionCard("Before / after") {
                if (c != null) ClipView(c, player, "before", label = "Original")
                ClipView(enhanced, player, "after", label = "Enhanced")
                Stat("Duration", formatSeconds(r.durationSec))
                Stat("Processing", "%.2f s (%.1f× real time)".format(r.processingSec, r.realtimeFactor))
                r.measuredLufs?.let { Stat("Measured loudness", "%.1f LUFS".format(it)) }
                r.measuredTruePeakDbfs?.let { Stat("True peak", "%.1f dBFS".format(it)) }
                OutlinedButton(
                    onClick = { scope.launch { WavFile.share(context, "enhanced", r.channels, r.sampleRate.toInt()) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Share, null)
                    Text("  Export WAV")
                }
            }
        }
    }
}
