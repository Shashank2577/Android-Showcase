package com.showcase.desertant.ask

import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.SummaryPoint
import com.showcase.desertant.data.SummaryTodo
import com.showcase.desertant.data.TalkSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One prompt in, one answer out, no tools: what summaries need from a model. */
fun interface TextModel {
    fun complete(system: String, prompt: String): String
}

/**
 * Writes a talk's notes with the on-device model: a short gist, key points,
 * decisions and to-dos (who, what, by when), each tied to the moment it was said.
 *
 * Long talks don't fit the model's window, so they're summarised in parts
 * first and the parts are then merged (map, then reduce). Every line the model
 * returns is checked: times must fall inside the talk, dates must be real.
 */
class TalkSummarizer(private val model: TextModel, private val modelName: String, private val partChars: Int = 16_000) {

    /** One line of transcript: when, who, what. */
    data class Line(val at: Double, val who: String, val text: String)

    data class Talk(
        val title: String,
        val date: LocalDate,
        val durationSec: Double,
        /** The talk's own language, in English ("Hindi"), if known. */
        val language: String?,
        /** Everyone named, with "(the user)" after the user. */
        val people: List<String>,
        val lines: List<Line>,
    )

    fun summarize(talk: Talk, writeIn: String, progress: (String) -> Unit = {}): TalkSummary {
        require(talk.lines.isNotEmpty()) { "there's no transcript to summarise" }
        // Scripts like Devanagari take more of the model's window per character.
        val budget = if (talk.lines.sumOf { l -> l.text.count { it.code > 0x2FF } } > talk.lines.sumOf { it.text.length } / 3) partChars * 2 / 3 else partChars
        val parts = split(talk.lines, budget)
        val notes = if (parts.size == 1) {
            progress("Writing the summary…")
            ask(prompt(talk, parts[0], writeIn, null), talk)
        } else {
            val partial = parts.mapIndexed { i, part ->
                progress("Summarising part ${i + 1} of ${parts.size}…")
                ask(prompt(talk, part, writeIn, i to parts.size), talk)
            }
            progress("Putting the parts together…")
            ask(mergePrompt(talk, partial, writeIn), talk)
        }
        return tidy(notes).copy(language = writeIn, model = modelName, writtenAt = System.currentTimeMillis())
    }

    /** Drop lines that repeat an earlier one (merging parts, and recaps at the end, make repeats). */
    private fun tidy(s: TalkSummary): TalkSummary {
        fun words(t: String) = t.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.length > 2 }.toSet()
        fun <T> unique(items: List<T>, text: (T) -> String): List<T> {
            val kept = mutableListOf<Pair<T, Set<String>>>()
            items.forEach { item ->
                val w = words(text(item))
                val repeat = kept.any { (_, k) -> w.isNotEmpty() && k.isNotEmpty() && (w intersect k).size >= 0.7 * minOf(w.size, k.size) }
                if (!repeat) kept += item to w
            }
            return kept.map { it.first }
        }
        val decisions = unique(s.decisions) { it.text }
        // A point that only restates a decision goes; the decision says it.
        val points = unique(decisions.map { SummaryPoint(it.text, it.at) } + s.points) { it.text }.drop(decisions.size)
        return s.copy(points = points, decisions = decisions, todos = unique(s.todos) { it.what })
    }

    private fun split(lines: List<Line>, budget: Int): List<List<Line>> {
        val out = mutableListOf<MutableList<Line>>(mutableListOf())
        var size = 0
        lines.forEach { l ->
            val n = l.text.length + l.who.length + 10
            if (size + n > budget && out.last().isNotEmpty()) { out += mutableListOf<Line>(); size = 0 }
            out.last() += l
            size += n
        }
        return out
    }

    private fun header(talk: Talk): String {
        val day = talk.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH))
        return buildString {
            append("Talk: \"${talk.title}\", recorded on $day (${talk.date}), ${(talk.durationSec / 60).toInt().coerceAtLeast(1)} minutes long.")
            talk.language?.let { append(" It was spoken in $it.") }
            if (talk.people.isNotEmpty()) append("\nPeople in it: ${talk.people.joinToString(", ")}.")
        }
    }

    private val shape = """{"gist":"…","points":[{"text":"…","at":"12:30"}],"decisions":[{"text":"…","at":"23:10"}],"todos":[{"who":"…","what":"…","when":"…","at":"28:20"}]}"""

    private fun rules(writeIn: String) = """
        Rules:
        - Write every text value in $writeIn, even if the talk was in another language. Keep people's names as they are.
        - "gist": two or three sentences on what the talk was about and how it ended.
        - "points": up to 5 objects, the most important facts or updates from the talk.
        - "decisions": up to 5 objects, things the people agreed or settled (a date chosen, an option picked, a plan approved).
        - "todos": up to 8 objects, things someone said they or someone else will do.
        - Every list item is an object with "text" (or "what" for todos) and "at". Fill "points" and "decisions" whenever the talk has any.
        - Only include what was actually said.
        - "at" is the [minutes:seconds] of the line it comes from.
        - "when" copies the words the talk used for when it's due ("by Friday", "the tenth", "next Tuesday"), or "" if none. Don't turn it into a date.
        - "who" is the person's name as listed above, or "" if it's unclear.
        Reply with the JSON only.
    """.trimIndent()

    private fun prompt(talk: Talk, part: List<Line>, writeIn: String, which: Pair<Int, Int>?) = buildString {
        appendLine(header(talk))
        if (which != null) {
            appendLine("This is part ${which.first + 1} of ${which.second} of the transcript (${formatClock(part.first().at)} to ${formatClock(part.last().at)}).")
        }
        appendLine("Transcript, each line starting with [minutes:seconds] and who spoke:")
        part.forEach { appendLine("[${formatClock(it.at)}] ${it.who}: ${it.text}") }
        appendLine()
        appendLine("Write notes for ${if (which != null) "this part" else "this talk"} as JSON exactly in this shape:")
        appendLine(shape)
        append(rules(writeIn))
    }

    private fun mergePrompt(talk: Talk, parts: List<TalkSummary>, writeIn: String) = buildString {
        appendLine(header(talk))
        appendLine("Here are notes for each part of the talk, in order:")
        parts.forEachIndexed { i, p -> appendLine("Part ${i + 1}: ${encode(p)}") }
        appendLine()
        appendLine("Combine them into notes for the whole talk, as JSON exactly in this shape (drop repeats, keep the times):")
        appendLine(shape)
        append(rules(writeIn))
    }

    /** Ask, and ask once more if the answer wasn't usable JSON. */
    private fun ask(prompt: String, talk: Talk): TalkSummary {
        val first = model.complete(SYSTEM, prompt)
        return parse(first, talk) ?: parse(model.complete(SYSTEM, "$prompt\n\nYour last answer wasn't valid JSON. Reply with only the JSON object."), talk)
            ?: throw IllegalStateException("the model's notes couldn't be read")
    }

    companion object {
        private const val SYSTEM = "You write short, accurate notes of recorded conversations. You only use what is in the transcript. You reply with JSON only."

        private val lenient = Json { isLenient = true; ignoreUnknownKeys = true }

        /** Read the model's JSON, tolerating code fences and chatter around it; null if it isn't usable. */
        fun parse(text: String, talk: Talk): TalkSummary? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val o = runCatching { lenient.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
            val gist = o.str("gist") ?: return null
            fun at(e: JsonObject) = e.str("at")?.let(::seconds)?.takeIf { it <= talk.durationSec + 5 }
            fun points(key: String) = o.items(key).mapNotNull { e -> (e.str("text") ?: e.str("what"))?.let { SummaryPoint(it, at(e)) } }.take(6)
            val todos = o.items("todos").mapNotNull { e ->
                val what = e.str("what") ?: e.str("text") ?: return@mapNotNull null
                val words = e.str("when") ?: e.str("due")
                SummaryTodo(what, e.str("who"), DueDates.resolve(words, talk.date)?.toString(), at(e), words)
            }.take(10)
            return TalkSummary(gist, points("points"), points("decisions"), todos, language = "", model = "", writtenAt = 0)
        }

        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

        /** A list's items as objects; a plain string becomes {"text": it} (small models do that). */
        private fun JsonObject.items(k: String): List<JsonObject> = (this[k] as? JsonArray).orEmpty().mapNotNull { e ->
            when (e) {
                is JsonObject -> e
                is JsonPrimitive -> e.contentOrNull?.let { JsonObject(mapOf("text" to JsonPrimitive(it))) }
                else -> null
            }
        }

        /** "12:30", "1:02:03", "750" → seconds. */
        fun seconds(s: String): Double? {
            val parts = s.trim().trim('[', ']').split(':').map { it.trim().toDoubleOrNull() ?: return null }
            return when (parts.size) {
                1 -> parts[0]
                2 -> parts[0] * 60 + parts[1]
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                else -> null
            }
        }

        fun encode(s: TalkSummary): String = JsonObject(
            mapOf(
                "gist" to JsonPrimitive(s.gist),
                "points" to JsonArray(s.points.map { pointJson(it.text, it.at) }),
                "decisions" to JsonArray(s.decisions.map { pointJson(it.text, it.at) }),
                "todos" to JsonArray(
                    s.todos.map {
                        JsonObject(
                            mapOf(
                                "who" to JsonPrimitive(it.who.orEmpty()), "what" to JsonPrimitive(it.what),
                                "when" to JsonPrimitive(it.dueWords ?: it.due.orEmpty()), "at" to JsonPrimitive(it.at?.let(::formatClock).orEmpty()),
                            ),
                        )
                    },
                ),
            ),
        ).toString()

        private fun pointJson(text: String, at: Double?): JsonElement =
            JsonObject(mapOf("text" to JsonPrimitive(text), "at" to JsonPrimitive(at?.let(::formatClock).orEmpty())))
    }
}
