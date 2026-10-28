@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.models

import ai.desertant.emo.EmoSuggestion
import ai.desertant.emo.EmojiSkinTone
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.unit.sp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.ui.components.ChoiceChips
import com.showcase.desertant.ui.components.LabeledSlider
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.prettify
import com.showcase.desertant.ui.components.rememberRunner
import kotlinx.coroutines.delay

private val EMO_SAMPLES = listOf(
    "Pay my bills",
    "Happy birthday!!",
    "going for a run before work",
    "Je suis tellement fatigué",
    "Vamos a la playa",
    "Pizza night with friends",
)

@Composable
fun EmoScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val runner = rememberRunner(ModelKind.EMO)
    var text by rememberSaveable { mutableStateOf("Pay my bills") }
    var limit by rememberSaveable { mutableFloatStateOf(5f) }
    var tone by rememberSaveable { mutableStateOf(EmojiSkinTone.DEFAULT) }
    var suggestions by remember { mutableStateOf<List<EmoSuggestion>>(emptyList()) }

    // Live suggestions while typing, debounced so each keystroke doesn't run the model.
    LaunchedEffect(text, limit, tone) {
        delay(200)
        runner.runLatest { suggestions = hub.emo.suggestions(text, limit.toInt(), tone) }
    }

    ModelScreen(ModelKind.EMO, onBack, runner) {
        SectionCard("Type a message") {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                minLines = 2,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EMO_SAMPLES.forEach { SuggestionChip(onClick = { text = it }, label = { Text(it) }) }
            }
        }
        SectionCard("Options") {
            LabeledSlider("Suggestions", limit, { limit = it }, 1f..10f, limit.toInt().toString(), steps = 8)
            Text("Skin tone", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(EmojiSkinTone.entries, tone, { tone = it }) { prettify(it.name.lowercase()) }
        }
        SectionCard("Suggestions — tap to add") {
            if (suggestions.isEmpty()) {
                Text("No suggestions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s ->
                    ElevatedCard(onClick = { text = text.trimEnd() + " " + s.emoji }) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(s.emoji, fontSize = 32.sp)
                            Text("%.0f%%".format(s.confidence * 100), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
