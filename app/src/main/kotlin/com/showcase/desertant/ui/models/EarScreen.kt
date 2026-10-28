package com.showcase.desertant.ui.models

import ai.desertant.ear.Detection
import ai.desertant.ear.Options
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.showcase.desertant.audio.AudioClip
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.VOZ_LANGUAGES
import com.showcase.desertant.core.languageName
import com.showcase.desertant.ui.components.AudioClipPlaceholder
import com.showcase.desertant.ui.components.AudioInputCard
import com.showcase.desertant.ui.components.LabeledSlider
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.ScoreBar
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.Stat
import com.showcase.desertant.ui.components.rememberClipPlayer
import com.showcase.desertant.ui.components.rememberRunner

@Composable
fun EarScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val runner = rememberRunner(ModelKind.EAR)
    val player = rememberClipPlayer()
    var clip by remember { mutableStateOf<AudioClip?>(null) }
    var windows by rememberSaveable { mutableFloatStateOf(3f) }
    var result by remember { mutableStateOf<Detection?>(null) }

    ModelScreen(ModelKind.EAR, onBack, runner) {
        AudioInputCard(clip, { clip = it; result = null }, player, enabled = !runner.busy)
        SectionCard("Identify") {
            LabeledSlider("30 s windows to analyze", windows, { windows = it }, 1f..5f, windows.toInt().toString(), steps = 3)
            val current = clip
            if (current == null) AudioClipPlaceholder()
            Button(
                enabled = current != null && !runner.busy,
                onClick = {
                    if (current != null) runner.run {
                        result = hub.ear.identify(current.samples, current.sampleRate.toDouble(), Options(windows.toInt()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Identify language") }
        }
        result?.let { d ->
            SectionCard("Result") {
                Text(languageName(d.language), style = MaterialTheme.typography.headlineSmall)
                Stat("Reliable", if (d.isReliable) "Yes" else "No — try a longer or cleaner clip")
                Stat("Windows analyzed", d.windows.toString())
                Stat("Voz can transcribe it", if (d.language in VOZ_LANGUAGES) "Yes" else "No")
                d.candidates.take(8).forEach { ScoreBar(languageName(it.language), it.probability) }
            }
        }
    }
}
