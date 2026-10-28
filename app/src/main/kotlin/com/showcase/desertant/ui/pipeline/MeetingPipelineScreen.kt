package com.showcase.desertant.ui.pipeline

import ai.desertant.clear.Options as ClearOptions
import ai.desertant.voz.Transcription
import ai.desertant.voz.Word
import ai.desertant.who.Diarization
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.showcase.desertant.audio.AudioClip
import com.showcase.desertant.core.ModelHub
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.Pipeline
import com.showcase.desertant.core.VOZ_LANGUAGES
import com.showcase.desertant.core.languageName
import com.showcase.desertant.ui.components.AudioClipPlaceholder
import com.showcase.desertant.ui.components.AudioInputCard
import com.showcase.desertant.ui.components.ClipView
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.ShowcaseScaffold
import com.showcase.desertant.ui.components.Span
import com.showcase.desertant.ui.components.Stat
import com.showcase.desertant.ui.components.rememberClipPlayer
import com.showcase.desertant.ui.components.shareText
import com.showcase.desertant.ui.theme.seriesColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class Step(val model: ModelKind, val label: String, val optional: Boolean) {
    CLEAN(ModelKind.CLEAR, "Clean up the audio (Clear)", true),
    LANGUAGE(ModelKind.EAR, "Identify the language (Ear)", true),
    TRANSCRIBE(ModelKind.VOZ, "Transcribe (Voz)", false),
    SPEAKERS(ModelKind.WHO, "Label speakers (Who)", true),
    REDACT(ModelKind.REDACT, "Redact personal data (Redact)", true),
    TOPICS(ModelKind.GIST, "Tag topics (Gist)", true),
}

private sealed interface StepState {
    data object Waiting : StepState
    data class Working(val what: String) : StepState
    data class Done(val millis: Long, val summary: String) : StepState
    data class Failed(val message: String) : StepState
}

/** One speaker's continuous stretch of transcript. */
data class Utterance(val speaker: Int?, val start: Double, val text: String)

/** Assign each word to the speaker turn its midpoint falls in (or the nearest one). */
fun attribute(words: List<Word>, diarization: Diarization?): List<Utterance> {
    if (words.isEmpty()) return emptyList()
    val turns = diarization?.turns.orEmpty()
    fun speakerAt(t: Double): Int? {
        if (turns.isEmpty()) return null
        turns.firstOrNull { t >= it.start && t <= it.end }?.let { return it.speaker }
        return turns.minBy { minOf(kotlin.math.abs(it.start - t), kotlin.math.abs(it.end - t)) }.speaker
    }
    val out = mutableListOf<Utterance>()
    var speaker = speakerAt((words[0].start + words[0].end) / 2)
    var start = words[0].start
    val buffer = StringBuilder()
    for (w in words) {
        val s = speakerAt((w.start + w.end) / 2)
        if (s != speaker && buffer.isNotEmpty()) {
            out += Utterance(speaker, start, buffer.toString().trim())
            buffer.clear()
            start = w.start
            speaker = s
        }
        buffer.append(w.text).append(' ')
    }
    if (buffer.isNotEmpty()) out += Utterance(speaker, start, buffer.toString().trim())
    return out
}

private class MeetingResult(
    val audio: AudioClip,
    val language: String?,
    val languageSupported: Boolean?,
    val transcript: Transcription,
    val diarization: Diarization?,
    val utterances: List<Utterance>,
    val redactedCount: Int,
    val topics: List<String>,
)

@Composable
fun MeetingPipelineScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = rememberClipPlayer()
    var clip by remember { mutableStateOf<AudioClip?>(null) }
    val enabled = remember { mutableStateMapOf<Step, Boolean>().apply { Step.entries.forEach { put(it, true) } } }
    val states = remember { mutableStateMapOf<Step, StepState>() }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MeetingResult?>(null) }

    fun start(input: AudioClip) {
        running = true
        result = null
        states.clear()
        Step.entries.filter { enabled[it] == true }.forEach { states[it] = StepState.Waiting }
        scope.launch {
            try {
                result = runMeeting(hub, input, enabled.filterValues { it }.keys) { step, state -> states[step] = state }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The failing step already shows its message.
            } finally {
                running = false
            }
        }
    }

    ShowcaseScaffold(Pipeline.MEETING.title, onBack) {
        Text(Pipeline.MEETING.description, style = MaterialTheme.typography.bodyMedium)
        AudioInputCard(clip, { clip = it; result = null; states.clear() }, player, title = "Conversation", enabled = !running)
        SectionCard("Steps") {
            Step.entries.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = enabled[step] == true,
                        onCheckedChange = { enabled[step] = it },
                        enabled = step.optional && !running,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(step.label, style = MaterialTheme.typography.bodyMedium)
                        when (val s = states[step]) {
                            is StepState.Working -> Text(s.what, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            is StepState.Done -> Text("${s.summary} · ${s.millis} ms", style = MaterialTheme.typography.labelSmall)
                            is StepState.Failed -> Text(s.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            else -> {}
                        }
                    }
                    when (states[step]) {
                        is StepState.Working -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        is StepState.Done -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                        is StepState.Failed -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        StepState.Waiting -> Icon(Icons.Outlined.RadioButtonUnchecked, null)
                        null -> {}
                    }
                }
            }
            val current = clip
            if (current == null) AudioClipPlaceholder()
            Button(enabled = current != null && !running, onClick = { current?.let(::start) }, modifier = Modifier.fillMaxWidth()) {
                Text("Run the pipeline on device")
            }
        }
        val primary = MaterialTheme.colorScheme.primary
        result?.let { r ->
            SectionCard("Meeting notes") {
                r.language?.let { Stat("Language", languageName(it)) }
                if (r.languageSupported == false) {
                    Text("Voz isn't trained on this language; the transcript is likely wrong.", color = MaterialTheme.colorScheme.error)
                }
                r.diarization?.let { Stat("Speakers", it.speakerCount.toString()) }
                if (r.topics.isNotEmpty()) Stat("Topics", r.topics.joinToString(", "))
                if (enabled[Step.REDACT] == true) Stat("Personal details removed", r.redactedCount.toString())
                ClipView(
                    r.audio, player, "meeting",
                    spans = r.diarization?.turns?.map { Span(it.start, it.end, seriesColor(it.speaker)) }.orEmpty(),
                    label = if (enabled[Step.CLEAN] == true) "Cleaned audio" else r.audio.label,
                )
                SelectionContainer {
                    Column {
                        r.utterances.forEach { u ->
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = u.speaker?.let(::seriesColor) ?: primary, fontWeight = FontWeight.Bold)) {
                                        append(u.speaker?.let { "Speaker ${it + 1}" } ?: "Transcript")
                                        append("  ")
                                    }
                                    append(u.text)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.size(6.dp))
                        }
                    }
                }
                OutlinedButton(onClick = { shareText(context, "Meeting notes", notesText(r)) }) {
                    Icon(Icons.Outlined.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share notes")
                }
            }
        }
    }
}

private fun notesText(r: MeetingResult): String = buildString {
    r.language?.let { appendLine("Language: ${languageName(it)}") }
    if (r.topics.isNotEmpty()) appendLine("Topics: ${r.topics.joinToString(", ")}")
    appendLine()
    r.utterances.forEach { u -> appendLine("${u.speaker?.let { "Speaker ${it + 1}" } ?: "Transcript"}: ${u.text}") }
}

private suspend fun runMeeting(
    hub: ModelHub,
    input: AudioClip,
    steps: Set<Step>,
    report: (Step, StepState) -> Unit,
): MeetingResult {
    suspend fun <T> step(step: Step, summary: (T) -> String, block: suspend () -> T): T {
        try {
            report(step, StepState.Working("Getting the model ready…"))
            hub.ensureReady(step.model)
            report(step, StepState.Working("Running on device…"))
            val started = SystemClock.elapsedRealtime()
            val value = block()
            report(step, StepState.Done(SystemClock.elapsedRealtime() - started, summary(value)))
            return value
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(step, StepState.Failed(e.message ?: e.javaClass.simpleName))
            throw e
        }
    }

    // Clear's 16 kHz output is exactly what the speech models run at.
    val audio = if (Step.CLEAN in steps) {
        step(Step.CLEAN, { r: ai.desertant.clear.Result -> "%.1f LUFS".format(r.measuredLufs ?: Double.NaN) }) {
            hub.clear.enhance(input.samples, input.sampleRate.toDouble(), ClearOptions(sampleRate = 16_000.0))
        }.let { AudioClip(it.samples, it.sampleRate.toInt(), "Cleaned audio") }
    } else input
    val rate = audio.sampleRate.toDouble()

    val language = if (Step.LANGUAGE in steps) {
        step(Step.LANGUAGE, { d: ai.desertant.ear.Detection -> languageName(d.language) }) { hub.ear.identify(audio.samples, rate) }
    } else null

    val transcript = step(Step.TRANSCRIBE, { t: Transcription -> "${t.words.size} words" }) { hub.voz.transcribe(audio.samples, rate) }

    val diarization = if (Step.SPEAKERS in steps) {
        step(Step.SPEAKERS, { d: Diarization -> "${d.speakerCount} speakers" }) { hub.who.diarize(audio.samples, rate) }
    } else null

    var utterances = attribute(transcript.words, diarization)
    if (utterances.isEmpty() && transcript.text.isNotBlank()) utterances = listOf(Utterance(null, 0.0, transcript.text))

    var redacted = 0
    if (Step.REDACT in steps && utterances.isNotEmpty()) {
        utterances = step(Step.REDACT, { u: List<Utterance> -> "$redacted removed" }) {
            utterances.map { u ->
                val r = hub.redact.redaction(u.text)
                redacted += r.items.size
                u.copy(text = r.redactedText)
            }
        }
    }

    val topics = if (Step.TOPICS in steps && transcript.text.isNotBlank()) {
        step(Step.TOPICS, { t: List<String> -> t.joinToString(", ").ifBlank { "none" } }) {
            hub.gist.classify(utterances.joinToString(" ") { it.text }, topK = 3).map { it.name }
        }
    } else emptyList()

    return MeetingResult(
        audio = audio,
        language = language?.language,
        languageSupported = language?.language?.let { it in VOZ_LANGUAGES },
        transcript = transcript,
        diarization = diarization,
        utterances = utterances,
        redactedCount = redacted,
        topics = topics,
    )
}
