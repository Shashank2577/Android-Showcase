@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.models

import ai.desertant.redact.Labels
import ai.desertant.redact.Options
import ai.desertant.redact.Redaction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.ui.components.LabeledSlider
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.MultiChips
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.Stat
import com.showcase.desertant.ui.components.prettify
import com.showcase.desertant.ui.components.rememberRunner
import com.showcase.desertant.ui.theme.seriesColor

private val REDACT_SAMPLES = listOf(
    "Email Anna at anna@example.hu or call 555-0100.",
    "Hi, I'm John Smith from Acme Corp. Ship it to 42 Baker Street, London NW1 6XE. Card 4111 1111 1111 1111.",
    "Mein Name ist Lena Fischer, meine IBAN ist DE89 3704 0044 0532 0130 00.",
    "Please wire €500 to Marie Dubois, +33 6 12 34 56 78, marie.dubois@mail.fr",
    "Server 192.168.1.20 was accessed from https://intranet.example.com by user jdoe",
)

/** Stable color per PII label, shared by chips, highlights and the item list. */
fun labelColor(label: String): Color = seriesColor(Labels.ALL.indexOf(label).coerceAtLeast(0))

/** The source text with every detected entity highlighted in its label's color. */
fun highlighted(source: String, redaction: Redaction): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    redaction.items.sortedBy { it.start }.forEach { item ->
        if (item.start < cursor || item.end > source.length) return@forEach
        append(source.substring(cursor, item.start))
        withStyle(SpanStyle(background = labelColor(item.label).copy(alpha = 0.3f), fontWeight = FontWeight.SemiBold)) {
            append(source.substring(item.start, item.end))
        }
        cursor = item.end
    }
    append(source.substring(cursor))
}

@Composable
fun RedactScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val runner = rememberRunner(ModelKind.REDACT)
    var text by rememberSaveable { mutableStateOf(REDACT_SAMPLES[1]) }
    var confidence by rememberSaveable { mutableFloatStateOf(0.6f) }
    var labels by remember { mutableStateOf(Labels.DEFAULT) }
    var result by remember { mutableStateOf<Pair<String, Redaction>?>(null) }
    var processed by remember { mutableStateOf("") }

    fun redactNow() = runner.run {
        val source = text
        val r = hub.redact.redaction(source, Options(minimumConfidence = confidence.toDouble(), labels = labels))
        result = source to r
        processed = "Summary from the cloud LLM: ${r.redactedText}"
    }

    ModelScreen(ModelKind.REDACT, onBack, runner) {
        SectionCard("Text with personal data") {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REDACT_SAMPLES.forEachIndexed { i, s -> SuggestionChip(onClick = { text = s }, label = { Text("Sample ${i + 1}") }) }
            }
            Button(onClick = ::redactNow, enabled = !runner.busy && text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Redact") }
        }
        SectionCard("Options") {
            LabeledSlider("Minimum confidence", confidence, { confidence = it }, 0.1f..0.95f, "%.2f".format(confidence))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Categories (${labels.size}/${Labels.ALL.size})", style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = { labels = Labels.ALL }) { Text("All") }
                    TextButton(onClick = { labels = Labels.DEFAULT }) { Text("Default") }
                }
            }
            MultiChips(
                options = Labels.ALL.toList(),
                selected = labels,
                onToggle = { l -> labels = if (l in labels) labels - l else labels + l },
                label = { prettify(it.lowercase()) },
                color = ::labelColor,
            )
        }
        result?.let { (source, r) ->
            SectionCard("Detected (${r.items.size})") {
                SelectionContainer { Text(highlighted(source, r), style = MaterialTheme.typography.bodyLarge) }
                r.items.forEach { item ->
                    Stat("${item.placeholder}  ←  ${item.original}", "%.0f%%".format(item.confidence * 100))
                }
            }
            SectionCard("Safe to send") {
                SelectionContainer {
                    Text(r.redactedText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
            }
            SectionCard("Round trip: restore after processing") {
                Text(
                    "Edit this as if a cloud model had answered using the placeholders — Restore fills the originals back in, on the device.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = processed, onValueChange = { processed = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text(r.restore(processed), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
