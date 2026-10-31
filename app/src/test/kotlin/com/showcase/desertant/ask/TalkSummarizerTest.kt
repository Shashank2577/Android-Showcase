package com.showcase.desertant.ask

import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.SummaryTodo
import com.showcase.desertant.data.TalkSummary
import com.showcase.desertant.ui.today.BriefData
import com.showcase.desertant.ui.today.firstSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Summaries with a scripted model, and the brief built from them. Plain JVM. */
class TalkSummarizerTest {
    private val friday = LocalDate.of(2026, 10, 30)
    private val lines = (0 until 40).map { TalkSummarizer.Line(it * 30.0, if (it % 2 == 0) "Sam" else "Priya", "line $it about the launch venue and budget") }
    private val talk = TalkSummarizer.Talk("launch", friday, 1_250.0, "English", listOf("Sam (the user)", "Priya"), lines)

    private class Scripted(vararg val answers: String) : TextModel {
        val prompts = mutableListOf<String>()
        override fun complete(system: String, prompt: String): String { prompts += prompt; return answers[prompts.size - 1] }
    }

    @Test fun readsMessyModelOutput() {
        val answer = """
            Sure! Here are the notes:
            ```json
            {"gist":"They picked a venue.","points":["The library fits 120 people."],
             "decisions":[{"text":"Book the library","at":"5:30"},{"text":"Too late","at":"99:00"}],
             "todos":[{"who":"Priya","what":"Book the library","when":"by Friday","at":"6:00"},{"who":"Sam","what":"Ask Lena","when":"soon"}]}
            ```
        """.trimIndent()
        val model = Scripted(answer)
        val s = TalkSummarizer(model, "test").summarize(talk, "Hindi")
        assertEquals("They picked a venue.", s.gist)
        assertEquals("The library fits 120 people.", s.points.single().text)
        assertEquals(330.0, s.decisions[0].at!!, 0.0)
        assertNull("a time past the end of the talk is dropped", s.decisions[1].at)
        assertEquals("2026-11-06", s.todos[0].due)
        assertNull(s.todos[1].due)
        assertEquals("soon", s.todos[1].dueWords)
        assertEquals("Hindi", s.language)
        assertTrue(model.prompts.single().contains("Write every text value in Hindi"))
    }

    @Test fun asksAgainWhenTheAnswerIsntJson() {
        val model = Scripted("I can't format that.", """{"gist":"ok","points":[],"decisions":[],"todos":[]}""")
        assertEquals("ok", TalkSummarizer(model, "test").summarize(talk, "English").gist)
        assertEquals(2, model.prompts.size)
    }

    @Test fun longTalksAreSummarisedInPartsThenMerged() {
        val part = """{"gist":"part","points":[{"text":"The library fits 120 people","at":"1:00"}],"decisions":[],"todos":[]}"""
        val merged = """{"gist":"whole","points":[{"text":"The library fits 120 people","at":"1:00"},{"text":"the library fits 120 people.","at":"9:00"}],
            "decisions":[{"text":"Book the library","at":"2:00"}],"todos":[]}"""
        val model = Scripted(part, part, part, merged)
        val steps = mutableListOf<String>()
        val s = TalkSummarizer(model, "test", partChars = 900).summarize(talk, "English") { steps += it }
        assertEquals(4, model.prompts.size)
        assertTrue(model.prompts[0].contains("part 1 of 3"))
        assertTrue(model.prompts[3].contains("Combine them"))
        assertEquals(listOf("Summarising part 1 of 3…", "Summarising part 2 of 3…", "Summarising part 3 of 3…", "Putting the parts together…"), steps)
        assertEquals("whole", s.gist)
        assertEquals("repeats are dropped", 1, s.points.size)
    }

    @Test fun briefShowsRecentTalksAndWhatsDue() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 10, 31)
        fun talkOn(id: Long, day: LocalDate, todos: List<SummaryTodo>) = MeetingEntity(
            id = id, title = "talk $id", createdAt = day.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000, audioPath = "",
            summaryJson = StoreJson.encodeToString(TalkSummary.serializer(), TalkSummary("Gist $id. More.", todos = todos, language = "English", model = "m", writtenAt = 0)),
        )
        val talks = listOf(
            talkOn(1, today, listOf(SummaryTodo("send budget", "Marco Rossi", "2026-11-01"), SummaryTodo("book venue", "Sam Carter", "2026-10-31"))),
            talkOn(2, today.minusDays(5), listOf(SummaryTodo("draft FAQ", "Priya", "2026-10-30"), SummaryTodo("update pricing", "Sam", "2026-11-05"), SummaryTodo("vague", "Sam"))),
        )
        val b = BriefData.build(talks, done = setOf("2/0"), me = "Sam Carter", today = today, zone = zone)
        assertEquals(listOf(1L), b.recent.map { it.id })
        assertEquals(listOf("book venue", "send budget"), b.dueNow.map { it.todo.what })
        assertEquals("you", b.dueNow[0].todo.who)
        assertEquals(listOf("update pricing"), b.thisWeek.map { it.todo.what })
    }

    @Test fun firstSentences() {
        assertEquals("They met.", firstSentence("They met. Then left."))
        assertEquals("कॉल हुई।", firstSentence("कॉल हुई। फिर बात हुई।"))
        assertEquals("no stop", firstSentence("no stop"))
    }
}
