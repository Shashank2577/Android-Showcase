package com.showcase.desertant.ask

import androidx.test.core.app.ApplicationProvider
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.showcase.desertant.ShowcaseApp
import com.showcase.desertant.seedSampleData
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The real thing: Gemma 4 E2B (CPU, on the build machine) answering questions
 * over the sample talks with Burrow's tools. Skipped unless -Pask.model and
 * -Pask.lib are given (see app/build.gradle.kts). Writes a transcript of every
 * step to build/ask-live.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AskLiveTest {
    @Test
    fun answersWithTools() = runBlocking {
        val model = System.getProperty("ask.model")
        assumeTrue("no model given", model != null && File(model).exists())
        val app = ApplicationProvider.getApplicationContext<ShowcaseApp>()
        seedSampleData(app)
        val tools = BurrowTools(app.db)
        val agent = AskAgent(tools)
        val out = StringBuilder("# ask Burrow, live run\n\n")
        Engine(EngineConfig(modelPath = model, backend = Backend.CPU(), maxNumTokens = 4096, cacheDir = System.getProperty("java.io.tmpdir"))).use { engine ->
            engine.initialize()
            for ((q, allow) in QUESTIONS) {
                val t0 = System.currentTimeMillis()
                var steps = emptyList<AskStep>()
                val asked = mutableListOf<String>()
                val outcome = LiteRtChat(engine, AskSession.systemPrompt(), tools.specs).use { chat ->
                    agent.run(chat, q, onStep = { steps = it }, confirm = { c -> asked += c.sentence; allow })
                }
                val secs = (System.currentTimeMillis() - t0) / 1000.0
                out.append("## $q\n\n")
                steps.forEach { out.append("- `${it.tool}(${it.args})` → ${it.status.name.lowercase()}: ${it.summary}\n") }
                asked.forEach { out.append("- asked the user: $it → ${if (allow) "yes" else "no"}\n") }
                out.append("\n**Answer** (${"%.1f".format(secs)} s): ${outcome.answer}\n\n")
                out.append("Sources: ${outcome.sources.joinToString { it.key }}\n\n")
                println(out.takeLast(1500))
            }
        }
        File("build/ask-live.md").writeText(out.toString())
    }

    private companion object {
        val QUESTIONS = listOf(
            "what did Priya promise?" to true,
            "when is Marco sending the budget?" to true,
            "who talks about hiring?" to true,
            "what was the Q3 roadmap sync about?" to true,
            "do I have any photos of dogs?" to true,
            "rename the roadmap talk to Q3 planning" to false,
            "what did we decide about the cloud tier?" to true,
            "what did Arjun say yesterday?" to true,
        )
    }
}
