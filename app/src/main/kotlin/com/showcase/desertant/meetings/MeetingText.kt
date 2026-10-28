package com.showcase.desertant.meetings

import ai.desertant.voz.Word
import ai.desertant.who.Turn
import com.showcase.desertant.data.Moment
import com.showcase.desertant.data.Utterance
import kotlin.math.abs

/** Speaker of the turn that contains [t], else of the nearest turn; null without turns. */
fun speakerAt(turns: List<Turn>, t: Double): Int? {
    if (turns.isEmpty()) return null
    turns.firstOrNull { t >= it.start && t <= it.end }?.let { return it.speaker }
    return turns.minBy { minOf(abs(it.start - t), abs(it.end - t)) }.speaker
}

/** Group words into utterances: a new one starts on a speaker change or a pause over [gap] seconds. */
fun utterances(words: List<Word>, turns: List<Turn>, gap: Double = 1.6): List<Utterance> {
    if (words.isEmpty()) return emptyList()
    val out = mutableListOf<Utterance>()
    var speaker = speakerAt(turns, (words[0].start + words[0].end) / 2)
    var start = words[0].start
    var end = words[0].end
    val text = StringBuilder()
    for (w in words) {
        val s = speakerAt(turns, (w.start + w.end) / 2)
        val pause = w.start - end
        val endsSentence = text.trimEnd().lastOrNull()?.let { it in ".?!" } == true
        if (text.isNotEmpty() && (s != speaker || (pause > gap && endsSentence) || pause > gap * 2)) {
            out += Utterance(speaker, start, end, text.toString().trim())
            text.clear()
            start = w.start
            speaker = s
        }
        text.append(w.text).append(' ')
        end = w.end
    }
    if (text.isNotEmpty()) out += Utterance(speaker, start, end, text.toString().trim())
    return out
}

/** Sentences with an estimated start time, interpolated across their utterance. */
fun sentences(utterances: List<Utterance>): List<Moment> = utterances.flatMap { u ->
    val parts = SENTENCE_END.split(u.text).map { it.trim() }.filter { it.isNotEmpty() }
    var offset = 0
    parts.map { part ->
        val at = u.start + (u.end - u.start) * offset / u.text.length.coerceAtLeast(1)
        offset += part.length + 1
        Moment(u.speaker, at, part)
    }
}

private val SENTENCE_END = Regex("(?<=[.?!])\\s+")

/** Commitments and next steps, by phrasing (English). */
private val FOLLOW_UP = Regex(
    "\\b(i'll|i will|we'll|we will|you'll|let's|let us|need to|needs to|have to|has to|going to|should|must|" +
        "follow up|action item|next step|deadline|by (monday|tuesday|wednesday|thursday|friday|tomorrow|tonight|next week|end of)|" +
        "remind|send (you|me|it|the)|schedule|book|todo|to-do)\\b",
    RegexOption.IGNORE_CASE,
)

fun followUps(sentences: List<Moment>, limit: Int = 8): List<Moment> =
    sentences.filter { m ->
        val words = m.text.split(' ').size
        words in 4..40 && FOLLOW_UP.containsMatchIn(m.text) && !m.text.trimEnd().endsWith("?")
    }.take(limit)

/** Pick [count] key sentences, spread across the conversation, scored by [score] (higher is better). */
suspend fun highlights(
    sentences: List<Moment>,
    count: Int = 5,
    score: suspend (String) -> Double,
): List<Moment> {
    val candidates = sentences.filter { it.text.split(' ').size in 7..45 }
    if (candidates.isEmpty()) return emptyList()
    // Score at most ~120 candidates, evenly sampled, to bound the work on long meetings.
    val stride = (candidates.size / 120).coerceAtLeast(1)
    val sampled = candidates.filterIndexed { i, _ -> i % stride == 0 }
    val scored = sampled.map { it to score(it.text) }
    // Split the meeting into [count] stretches and take the best sentence from each,
    // so highlights cover the whole conversation rather than one busy minute.
    val buckets = scored.groupBy { (m, _) -> (sampled.indexOf(m) * count / sampled.size).coerceIn(0, count - 1) }
    return buckets.values.mapNotNull { b -> b.maxByOrNull { it.second }?.first }.sortedBy { it.start }
}

/**
 * Split [samples] into pieces of about [targetSec], cutting at the quietest
 * 100 ms within ±[searchSec] of each boundary so a cut rarely lands mid-word.
 * Returns (startSample, endSample) pairs covering the whole clip.
 */
fun quietChunks(samples: FloatArray, rate: Int, targetSec: Double = 600.0, searchSec: Double = 8.0): List<Pair<Int, Int>> {
    val target = (targetSec * rate).toInt()
    if (samples.size <= target * 1.25) return listOf(0 to samples.size)
    val window = rate / 10
    val search = (searchSec * rate).toInt()
    val cuts = mutableListOf(0)
    var next = target
    while (next < samples.size - target / 4) {
        var best = next
        var bestEnergy = Double.MAX_VALUE
        var at = (next - search).coerceAtLeast(cuts.last() + window)
        val end = (next + search).coerceAtMost(samples.size - window)
        while (at < end) {
            var e = 0.0
            for (i in at until at + window) e += samples[i] * samples[i]
            if (e < bestEnergy) { bestEnergy = e; best = at + window / 2 }
            at += window / 2
        }
        cuts += best
        next = best + target
    }
    cuts += samples.size
    return cuts.zipWithNext()
}
