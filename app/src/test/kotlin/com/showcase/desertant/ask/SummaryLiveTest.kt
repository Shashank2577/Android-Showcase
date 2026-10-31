package com.showcase.desertant.ask

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.TalkSummary
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Talk summaries with the real model (Gemma 4 E2B on the build machine's CPU).
 * Skipped unless -Pask.model and -Pask.lib are given. Writes build/summary-live.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SummaryLiveTest {
    private fun load(name: String, date: LocalDate, title: String, language: String?): TalkSummarizer.Talk {
        val rows = javaClass.classLoader!!.getResource(name)!!.readText().lines().filter { it.isNotBlank() }.map { it.split('|', limit = 2) }
        val lines = rows.mapIndexed { i, (who, text) -> TalkSummarizer.Line(20.0 + i * 41.0, who, text) }
        val people = rows.map { it[0] }.distinct().map { if (it == "Sam Carter") "$it (the user)" else it }
        return TalkSummarizer.Talk(title, date, lines.last().at + 30, language, people, lines)
    }

    @Test
    fun summarises() {
        val model = System.getProperty("ask.model")
        assumeTrue("no model given", model != null && File(model).exists())
        val out = StringBuilder("# talk summaries, live run\n\n")
        Engine(EngineConfig(modelPath = model, backend = Backend.CPU(), maxNumTokens = AskModel.MAX_TOKENS, cacheDir = System.getProperty("java.io.tmpdir"))).use { engine ->
            engine.initialize()
            val raw = mutableListOf<String>()
            val real = LiteRtText(engine)
            val text = TextModel { sys, p -> real.complete(sys, p).also { raw += it } }
            val friday = LocalDate.of(2026, 10, 30)
            val runs = listOf(
                Triple(load("talk-launch-planning.txt", friday, "launch planning", "English"), "English", 9_000),
                // Same talk split into parts, to check that merging keeps what matters.
                Triple(load("talk-launch-planning.txt", friday, "launch planning", "English"), "English", 1_500),
                Triple(load("talk-hindi-call.txt", friday, "call with Mum", "Hindi"), "English", 9_000),
                Triple(load("talk-hindi-call.txt", friday, "call with Mum", "Hindi"), "Hindi", 9_000),
            )
            runs.forEach { (talk, lang, part) ->
                val t0 = System.currentTimeMillis()
                val steps = mutableListOf<String>()
                val s = TalkSummarizer(text, AskModel.NAME, partChars = part).summarize(talk, lang) { steps += it }
                val secs = (System.currentTimeMillis() - t0) / 1000.0
                out.append("## “${talk.title}” (${talk.language}, ${talk.lines.size} lines) → $lang, parts of $part chars\n\n")
                out.append("${steps.joinToString(" → ")} · ${"%.1f".format(secs)} s\n\n")
                out.append(render(s)).append("\n")
                out.append("<details><summary>raw model output</summary>\n\n```\n${raw.joinToString("\n---\n")}\n```\n</details>\n\n")
                raw.clear()
                println(out.takeLast(3000))
            }
        }
        File("build/summary-live.md").writeText(out.toString())
    }

    /** The sample "Q3 roadmap sync" through the app's own path; its JSON seeds the screenshots. */
    @Test
    fun summarisesTheSampleTalk() = kotlinx.coroutines.runBlocking {
        val model = System.getProperty("ask.model")
        assumeTrue("no model given", model != null && File(model).exists())
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<com.showcase.desertant.ShowcaseApp>()
        com.showcase.desertant.seedSampleData(app)
        val talk = TalkSummaries.talkOf(app.db, app.db.meetings().get(1)!!)
        val out = StringBuilder("# the sample talk\n\n")
        Engine(EngineConfig(modelPath = model, backend = Backend.CPU(), maxNumTokens = AskModel.MAX_TOKENS, cacheDir = System.getProperty("java.io.tmpdir"))).use { engine ->
            engine.initialize()
            for (lang in listOf("English", "Hindi")) {
                val s = TalkSummarizer(LiteRtText(engine), AskModel.NAME).summarize(talk, lang)
                out.append("## $lang\n\n").append(render(s)).append("\n```json\n")
                    .append(com.showcase.desertant.data.StoreJson.encodeToString(TalkSummary.serializer(), s)).append("\n```\n\n")
            }
        }
        File("build/summary-sample.md").writeText(out.toString())
    }

    private fun render(s: TalkSummary) = buildString {
        appendLine("**Gist:** ${s.gist}\n")
        appendLine("**Points**"); s.points.forEach { appendLine("- ${it.text} ${it.at?.let { a -> "(${formatClock(a)})" } ?: ""}") }
        appendLine("\n**Decided**"); s.decisions.forEach { appendLine("- ${it.text} ${it.at?.let { a -> "(${formatClock(a)})" } ?: ""}") }
        appendLine("\n**To do**"); s.todos.forEach { appendLine("- ${it.who ?: "?"}: ${it.what}${it.due?.let { d -> " — due $d" } ?: ""}${it.dueWords?.let { w -> " (“$w”)" } ?: ""} ${it.at?.let { a -> "(${formatClock(a)})" } ?: ""}") }
    }
}
