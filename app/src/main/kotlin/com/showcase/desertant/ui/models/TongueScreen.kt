@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.models

import ai.desertant.tongue.Detection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.languageName
import com.showcase.desertant.ui.components.LabeledSlider
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.ScoreBar
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.Stat
import com.showcase.desertant.ui.components.prettify
import com.showcase.desertant.ui.components.rememberRunner

private val TONGUE_SAMPLES = listOf(
    "kann ich das haben",
    "la casa",
    "Where is the station?",
    "Dziękuję bardzo",
    "Ik ben moe",
    "Merci beaucoup",
    "Hvor er toget?",
    "Kiitos paljon",
    "Это очень хорошо",
    "Γεια σου",
)

@Composable
fun TongueScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val runner = rememberRunner(ModelKind.TONGUE)
    var text by rememberSaveable { mutableStateOf("kann ich das haben") }
    var topK by rememberSaveable { mutableFloatStateOf(3f) }
    var detection by remember { mutableStateOf<Detection?>(null) }

    // Tongue is pure Kotlin arithmetic over 2 MB of bundled weights: fast enough per keystroke.
    LaunchedEffect(text, topK) {
        runner.runLatest { detection = hub.tongue.detect(text, topK.toInt()) }
    }

    ModelScreen(ModelKind.TONGUE, onBack, runner) {
        SectionCard("Type a few words") {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Text") })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TONGUE_SAMPLES.forEach { SuggestionChip(onClick = { text = it }, label = { Text(it) }) }
            }
            LabeledSlider("Candidates", topK, { topK = it }, 1f..5f, topK.toInt().toString(), steps = 3)
        }
        detection?.let { d ->
            SectionCard("Detection") {
                Text(languageName(d.language), style = MaterialTheme.typography.headlineSmall)
                if (d.isTooCloseToCall) {
                    Text(
                        "Too close to call: the top candidates are nearly tied, so both are shown rather than guessing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                d.candidates.forEach { ScoreBar(languageName(it.language), it.probability) }
                Stat("Reliability", prettify(d.reliability.name.lowercase()))
                Stat("Route", prettify(d.route.verdict.name.lowercase()))
                d.route.script?.let { Stat("Script", it) }
                if (d.route.candidates.isNotEmpty() && d.route.candidates.size <= 12) {
                    Stat("Script narrows to", d.route.candidates.joinToString(", "))
                }
                Stat("Normalized input", "\"${d.normalized}\"")
            }
        }
    }
}
