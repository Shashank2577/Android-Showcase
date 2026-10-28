@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.pipeline

import ai.desertant.emo.EmoSuggestion
import ai.desertant.gist.Topic
import ai.desertant.redact.Redaction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.Pipeline
import com.showcase.desertant.core.languageName
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.RunFooter
import com.showcase.desertant.ui.components.ScoreBar
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.ShowcaseScaffold
import com.showcase.desertant.ui.components.Stat
import com.showcase.desertant.ui.components.rememberRunner
import com.showcase.desertant.ui.models.highlighted
import kotlinx.coroutines.delay

private val MESSAGE_SAMPLES = listOf(
    "Hey Tom, dinner at Luigi's on Friday? Call me on 07700 900123 🍕",
    "Can you send the Q3 budget to sarah.lee@acme.com before the board meeting?",
    "Bonjour Camille, le match de foot commence à 20h, on se retrouve au stade ?",
    "Ich habe heute 10 km gelaufen und bin total erschöpft",
)

private class MessageAnalysis(
    val source: String,
    val language: String?,
    val tooClose: Boolean,
    val redaction: Redaction,
    val topics: List<Topic>,
    val emoji: List<EmoSuggestion>,
)

@Composable
fun MessagePipelineScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val runner = rememberRunner(ModelKind.REDACT, ModelKind.GIST, ModelKind.EMO)
    var text by rememberSaveable { mutableStateOf(MESSAGE_SAMPLES[0]) }
    var analysis by remember { mutableStateOf<MessageAnalysis?>(null) }

    LaunchedEffect(text) {
        delay(350)
        if (text.isBlank()) return@LaunchedEffect
        runner.runLatest {
            val source = text
            val language = hub.tongue.detect(source)
            val redaction = hub.redact.redaction(source)
            // Tag and decorate the redacted text, so nothing personal feeds the other models' outputs.
            val topics = hub.gist.classify(redaction.redactedText, topK = 3)
            val emoji = hub.emo.suggestions(source, limit = 5)
            analysis = MessageAnalysis(source, language.language, language.isTooCloseToCall, redaction, topics, emoji)
        }
    }

    ShowcaseScaffold(Pipeline.MESSAGE.title, onBack) {
        Text(Pipeline.MESSAGE.description, style = MaterialTheme.typography.bodyMedium)
        SectionCard("Message") {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MESSAGE_SAMPLES.forEachIndexed { i, s -> SuggestionChip(onClick = { text = s }, label = { Text("Sample ${i + 1}") }) }
            }
        }
        RunFooter(runner)
        analysis?.let { a ->
            SectionCard("Tongue · language") {
                Stat("Language", languageName(a.language) + if (a.tooClose) " (too close to call)" else "")
            }
            SectionCard("Redact · what leaves the phone") {
                Text(highlighted(a.source, a.redaction), style = MaterialTheme.typography.bodyLarge)
                Text(a.redaction.redactedText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard("Gist · topics") {
                if (a.topics.isEmpty()) Text("No clear topic.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                a.topics.forEach { ScoreBar(it.name, it.score) }
            }
            SectionCard("Emo · tap to add") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    a.emoji.forEach { e ->
                        AssistChip(onClick = { text = text.trimEnd() + " " + e.emoji }, label = { Text(e.emoji, fontSize = 22.sp) })
                    }
                }
            }
        }
    }
}
