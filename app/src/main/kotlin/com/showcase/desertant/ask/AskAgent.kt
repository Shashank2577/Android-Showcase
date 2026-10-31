package com.showcase.desertant.ask

/** One function the model wants Burrow to run. */
data class ToolCallRequest(val name: String, val args: Map<String, Any?>)

/** What the model said back: text, tool calls, or both. */
data class ModelReply(val text: String, val calls: List<ToolCallRequest>)

/**
 * A conversation with a language model that can call tools. The real one is
 * Gemma on LiteRT-LM ([LiteRtChat]); tests script one.
 */
interface ToolChat : AutoCloseable {
    fun send(question: String): ModelReply

    /** Hand the model the results of its last calls, as (tool name, JSON) pairs. */
    fun sendResults(results: List<Pair<String, String>>): ModelReply
}

/** A row in "what Burrow did". */
data class AskStep(
    val tool: String,
    val label: String,
    val args: String,
    val status: Status,
    val summary: String? = null,
) {
    enum class Status { RUNNING, DONE, DECLINED, FAILED }
}

/** Something an answer can point back to. */
sealed interface AskSource {
    val key: String

    data class Talk(val id: Long, val title: String, val atSec: Double? = null, val quote: String? = null) : AskSource {
        override val key get() = "talk/$id/${atSec ?: ""}"
    }

    data class Person(val id: Long, val name: String) : AskSource {
        override val key get() = "person/$id"
    }

    data class Photos(val query: String, val count: Int, val uris: List<String>) : AskSource {
        override val key get() = "photos/$query"
    }
}

/** Result of running one tool. [json] goes back to the model; the rest is for the screen. */
data class ToolResult(val json: String, val summary: String, val sources: List<AskSource> = emptyList(), val openRoute: String? = null)

/** What the screen shows while it waits for "do it" on a tool that changes something. */
data class Confirmation(val tool: String, val sentence: String)

/** Burrow's side of the tools: their specs for the model, and how to run them. */
interface ToolBox {
    val specs: List<ToolSpec>
    fun spec(name: String): ToolSpec? = specs.firstOrNull { it.name == name }
    suspend fun run(name: String, args: Map<String, Any?>): ToolResult

    /** The sentence the user confirms before a [ToolSpec.changesThings] tool runs. */
    suspend fun describe(name: String, args: Map<String, Any?>): String
}

/**
 * A tool as the model sees it: an OpenAPI-style JSON description. Tools that
 * [changesThings] never run without the user saying yes on the screen.
 */
data class ToolSpec(val name: String, val label: String, val json: String, val changesThings: Boolean = false)

data class AskOutcome(val answer: String, val sources: List<AskSource>, val openRoute: String?)

/**
 * The tool loop. The model reads the question and asks for tools; Burrow runs
 * them on the phone and hands back the results, until the model answers.
 *
 * Tool calling is manual on purpose (LiteRT-LM can run tools by itself): it
 * lets every step show on screen as it happens, stops tools that change data
 * until the user confirms, and caps the number of rounds so a confused small
 * model can't loop forever.
 */
class AskAgent(private val tools: ToolBox, private val maxRounds: Int = 5) {
    suspend fun run(
        chat: ToolChat,
        question: String,
        onStep: (List<AskStep>) -> Unit,
        confirm: suspend (Confirmation) -> Boolean,
    ): AskOutcome {
        val steps = mutableListOf<AskStep>()
        val sources = LinkedHashMap<String, AskSource>()
        var route: String? = null
        val declined = mutableListOf<String>()
        var reply = chat.send(question)
        var rounds = 0
        while (reply.calls.isNotEmpty() && rounds++ < maxRounds) {
            val results = reply.calls.map { call ->
                val spec = tools.spec(call.name)
                val step = AskStep(call.name, spec?.label ?: call.name, argsText(call.args), AskStep.Status.RUNNING)
                steps += step
                onStep(steps.toList())
                val i = steps.lastIndex
                val json = when {
                    spec == null -> {
                        steps[i] = step.copy(status = AskStep.Status.FAILED, summary = "no such tool")
                        """{"error":"there is no tool called ${call.name}"}"""
                    }
                    spec.changesThings && !tools.describe(call.name, call.args).let { sentence ->
                        confirm(Confirmation(call.name, sentence)).also { yes -> if (!yes) declined += sentence }
                    } -> {
                        steps[i] = step.copy(status = AskStep.Status.DECLINED, summary = "you said no")
                        """{"declined":true,"note":"The user said no. Don't try again; tell them it wasn't done."}"""
                    }
                    else -> try {
                        val r = tools.run(call.name, call.args)
                        r.sources.forEach { sources[it.key] = it }
                        if (r.openRoute != null) route = r.openRoute
                        steps[i] = step.copy(status = AskStep.Status.DONE, summary = r.summary)
                        r.json
                    } catch (e: IllegalArgumentException) {
                        steps[i] = step.copy(status = AskStep.Status.FAILED, summary = e.message)
                        """{"error":${jsonString(e.message ?: "bad arguments")}}"""
                    }
                }
                onStep(steps.toList())
                call.name to json
            }
            reply = chat.sendResults(results)
        }
        val answer = when {
            // Small models sometimes claim they did what the user just refused, so
            // Burrow says this part itself.
            declined.isNotEmpty() -> "Okay, I left it as it was: I didn't ${declined.joinToString(" or ")}."
            reply.text.isNotBlank() -> reply.text.trim()
            reply.calls.isNotEmpty() -> "I couldn't work that out in a few steps. Try asking it more simply."
            else -> "I don't have an answer for that."
        }
        return AskOutcome(answer, cited(answer, sources.values.toList()), route)
    }

    companion object {
        fun argsText(args: Map<String, Any?>) = args.entries
            .filter { it.value != null && it.value.toString().isNotBlank() }
            .joinToString(", ") { (k, v) -> if (v is String) "$k: \"$v\"" else "$k: ${number(v)}" }

        private fun number(v: Any?) = (v as? Number)?.toDouble()?.takeIf { it % 1.0 == 0.0 }?.toLong()?.toString() ?: v.toString()

        /**
         * The sources worth showing under an answer: the ones it names (a talk's
         * title, a person's name, a quote's words), else the first few the tools
         * returned. Small models cite unreliably, so Burrow does the citing.
         */
        fun cited(answer: String, all: List<AskSource>): List<AskSource> {
            val a = answer.lowercase()
            fun mentioned(s: AskSource) = when (s) {
                // A quoted line counts when the answer uses its words; a bare talk when it names it.
                is AskSource.Talk -> if (s.quote != null) overlap(s.quote.lowercase(), a) >= 0.5 else s.title.lowercase() in a
                is AskSource.Person -> s.name.lowercase().substringBefore(' ') in a
                is AskSource.Photos -> true
            }
            val named = all.filter(::mentioned)
            // A quote that's cited makes its bare talk redundant.
            val quotedTalks = named.filterIsInstance<AskSource.Talk>().filter { it.quote != null }.map { it.id }.toSet()
            val tidy = named.filterNot { it is AskSource.Talk && it.quote == null && it.id in quotedTalks }
            return (tidy.ifEmpty { all.filterNot { it is AskSource.Person }.take(3) }).take(5)
        }

        private fun overlap(quote: String, answer: String): Double {
            val words = quote.split(Regex("\\W+")).filter { it.length > 3 }.toSet()
            if (words.isEmpty()) return 0.0
            return words.count { it in answer }.toDouble() / words.size
        }
    }
}

fun jsonString(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()
