package com.showcase.desertant.ask

import androidx.test.core.app.ApplicationProvider
import com.showcase.desertant.ShowcaseApp
import com.showcase.desertant.seedSampleData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tool loop with a scripted model, and Burrow's tools over the sample data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AskAgentTest {
    private val app: ShowcaseApp get() = ApplicationProvider.getApplicationContext()
    private lateinit var tools: BurrowTools

    @Before
    fun seed() {
        seedSampleData(app)
        tools = BurrowTools(app.db)
    }

    /** Plays back replies in order and records what Burrow sent. */
    private class Script(vararg val replies: ModelReply) : ToolChat {
        val sent = mutableListOf<List<Pair<String, String>>>()
        private var i = 0
        override fun send(question: String) = replies[i++]
        override fun sendResults(results: List<Pair<String, String>>): ModelReply { sent += results; return replies[i++] }
        override fun close() {}
    }

    private fun call(name: String, vararg args: Pair<String, Any?>) = ModelReply("", listOf(ToolCallRequest(name, args.toMap())))

    @Test
    fun runsToolsAndCitesWhatTheAnswerNames() = runBlocking {
        val chat = Script(
            call("follow_ups", "person" to "Marco"),
            ModelReply("Marco said he'll send it Thursday, in the Q3 roadmap sync.", emptyList()),
        )
        var shown = emptyList<AskStep>()
        val out = AskAgent(tools).run(chat, "when is Marco sending the budget?", { shown = it }, { error("nothing to confirm") })
        val result = Json.parseToJsonElement(chat.sent.single().single().second).jsonObject
        val item = result["follow_ups"]!!.jsonArray.single().jsonObject
        assertEquals("Marco Rossi", item["who"]!!.jsonPrimitive.content)
        assertEquals("28:20", item["at"]!!.jsonPrimitive.content)
        assertEquals(AskStep.Status.DONE, shown.single().status)
        val talk = out.sources.single() as AskSource.Talk
        assertEquals(1700.0, talk.atSec!!, 0.0)
    }

    @Test
    fun changesWaitForYesAndNoIsNeverReportedAsDone() = runBlocking {
        val chat = Script(
            call("rename_talk", "talk_id" to 1.0, "title" to "Q3 planning"),
            ModelReply("I have renamed the talk.", emptyList()),
        )
        var asked: Confirmation? = null
        val out = AskAgent(tools).run(chat, "rename it", {}, { asked = it; false })
        assertEquals("rename “Q3 roadmap sync” to “Q3 planning”", asked!!.sentence)
        assertEquals("Q3 roadmap sync", app.db.meetings().get(1)!!.title)
        assertTrue(chat.sent.single().single().second.contains("declined"))
        // The model claimed it renamed; Burrow says what really happened.
        assertTrue(out.answer.startsWith("Okay, I left it as it was"))
    }

    @Test
    fun yesRenames() = runBlocking {
        val chat = Script(call("rename_talk", "talk_id" to "1", "title" to "Q3 planning"), ModelReply("Done.", emptyList()))
        AskAgent(tools).run(chat, "rename it", {}, { true })
        assertEquals("Q3 planning", app.db.meetings().get(1)!!.title)
    }

    @Test
    fun unknownToolsAndBadArgumentsGoBackToTheModel() = runBlocking {
        val chat = Script(
            ModelReply("", listOf(ToolCallRequest("delete_everything", emptyMap()), ToolCallRequest("talk_details", emptyMap()))),
            ModelReply("Sorry.", emptyList()),
        )
        var shown = emptyList<AskStep>()
        AskAgent(tools).run(chat, "?", { shown = it }, { true })
        assertTrue(chat.sent.single().all { it.second.contains("error") })
        assertTrue(shown.all { it.status == AskStep.Status.FAILED })
    }

    @Test
    fun stopsAfterTooManyRounds() = runBlocking {
        val loop = Array(10) { call("find_person", "name" to "Priya") }
        val out = AskAgent(tools, maxRounds = 3).run(Script(*loop), "?", {}, { true })
        assertTrue(out.answer.startsWith("I couldn't work that out"))
    }

    @Test
    fun toolsFindPeopleTalksAndNoOne() = runBlocking {
        val priya = tools.run("find_person", mapOf("name" to "priya"))
        assertTrue(priya.json, priya.json.contains("Priya Shah") && priya.json.contains("roadmap"))
        val me = tools.run("find_person", mapOf("name" to "me"))
        assertTrue(me.json.contains("Sam Carter"))
        val nobody = tools.run("search_talks", mapOf("person" to "Arjun"))
        assertTrue(nobody.json.contains("No one called Arjun"))
        val cloud = tools.run("search_talks", mapOf("query" to "cloud tier"))
        assertTrue(cloud.json, cloud.json.contains("No cloud tier"))
        assertFalse("talks still processing aren't searched", cloud.json.contains("design crit"))
    }

    @Test
    fun citesOnlyTheLinesAnAnswerUses() {
        val all = listOf(
            AskSource.Talk(1, "Q3 roadmap sync", 580.0, "And the no-cloud promise holds for every plan?"),
            AskSource.Talk(1, "Q3 roadmap sync", 2260.0, "Then we book the launch venue next week."),
            AskSource.Person(2, "Priya Shah"),
        )
        // Gemma's real answer from AskLiveTest.
        val cited = AskAgent.cited("Priya promised to book the launch venue next week, according to the \"Q3 roadmap sync\" talk.", all)
        assertEquals(listOf("talk/1/2260.0", "person/2"), cited.map { it.key })
    }

    @Test
    fun searchesSharedChatsByPersonAndWords() = runBlocking {
        val r = tools.run("search_messages", mapOf("person" to "Priya", "query" to "book"))
        assertTrue(r.json, r.json.contains("I'll book it next week") && r.json.contains("Priya Shah"))
        assertFalse("the user's own messages aren't Priya's", r.json.contains("Looks good"))
        val p = tools.run("find_person", mapOf("name" to "Priya"))
        assertTrue(p.json, p.json.contains("\"recorded_calls\":1") && p.json.contains("\"chat_messages_from_them\":4"))
        val calls = tools.run("search_talks", mapOf("person" to "Priya"))
        assertTrue("calls linked by number count as talks with her", calls.json.contains("call with Priya"))
    }

    @Test
    fun specsAreValidJson() {
        tools.specs.forEach { s ->
            val o = Json.parseToJsonElement(s.json).jsonObject
            assertEquals(s.name, o["name"]!!.jsonPrimitive.content)
        }
        assertEquals(setOf("rename_talk", "start_recording"), tools.specs.filter { it.changesThings }.map { it.name }.toSet())
    }
}
