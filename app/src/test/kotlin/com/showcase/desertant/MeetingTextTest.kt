package com.showcase.desertant

import ai.desertant.voz.Word
import ai.desertant.who.Turn
import com.showcase.desertant.audio.resample
import com.showcase.desertant.data.Moment
import com.showcase.desertant.data.Utterance
import com.showcase.desertant.meetings.followUps
import com.showcase.desertant.meetings.highlights
import com.showcase.desertant.meetings.sentences
import com.showcase.desertant.meetings.utterances
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingTextTest {
    private fun words(vararg spec: Pair<String, Double>) = spec.map { (w, t) -> Word(w, t, t + 0.3) }

    @Test fun wordsSplitIntoUtterancesBySpeaker() {
        val w = words("Hi" to 0.0, "there." to 0.4, "Hello" to 2.0, "back." to 2.4, "Great." to 4.0)
        val turns = listOf(Turn(0, 0.0, 1.0), Turn(1, 1.8, 3.0), Turn(0, 3.8, 5.0))
        val u = utterances(w, turns)
        assertEquals(listOf(0, 1, 0), u.map { it.speaker })
        assertEquals("Hi there.", u[0].text)
        assertEquals("Hello back.", u[1].text)
    }

    @Test fun wordsOutsideTurnsGoToNearestSpeaker() {
        val w = words("Okay" to 1.5)
        val turns = listOf(Turn(0, 0.0, 1.0), Turn(1, 3.0, 4.0))
        assertEquals(0, utterances(w, turns).single().speaker)
    }

    @Test fun noTurnsMeansNoSpeaker() {
        val u = utterances(words("Solo" to 0.0, "talk." to 0.4), emptyList())
        assertEquals(null, u.single().speaker)
    }

    @Test fun sentencesGetInterpolatedTimes() {
        val s = sentences(listOf(Utterance(0, 10.0, 20.0, "First part here. Second part here.")))
        assertEquals(2, s.size)
        assertEquals(10.0, s[0].start, 0.01)
        assertTrue(s[1].start > 14 && s[1].start < 16)
    }

    @Test fun followUpsFindCommitments() {
        val s = listOf(
            Moment(0, 0.0, "I'll send you the revised budget by Friday."),
            Moment(1, 5.0, "The weather was lovely last week."),
            Moment(0, 9.0, "Should we meet again?"),
            Moment(1, 12.0, "We need to book the venue for the launch event."),
        )
        val f = followUps(s).map { it.text }
        assertEquals(listOf("I'll send you the revised budget by Friday.", "We need to book the venue for the launch event."), f)
    }

    @Test fun highlightsSpreadAcrossTheMeeting() = runTest {
        val s = (0 until 50).map { Moment(it % 2, it * 10.0, "This is sentence number $it about the quarterly plan and goals.") }
        val h = highlights(s, count = 5) { text -> if ("number 4" in text || "number 45" in text) 1.0 else 0.1 }
        assertEquals(5, h.size)
        assertTrue(h.zipWithNext().all { (a, b) -> a.start < b.start })
        assertTrue(h.first().start < 100 && h.last().start >= 400)
    }

    @Test fun resampleKeepsDuration() {
        val x = FloatArray(48_000) { (it % 100) / 100f }
        val y = resample(x, 48_000, 16_000)
        assertEquals(16_000, y.size)
    }
}

class QuietChunksTest {
    @Test fun shortAudioIsOneChunk() {
        val c = com.showcase.desertant.meetings.quietChunks(FloatArray(16_000 * 60), 16_000)
        assertEquals(listOf(0 to 16_000 * 60), c)
    }

    @Test fun longAudioCutsAtQuietSpots() {
        val rate = 16_000
        val n = rate * 25 * 60
        // Loud everywhere except a short pause near 10:03 and 20:02.
        val quiet = setOf(rate * 603, rate * 1202)
        val x = FloatArray(n) { i -> if (quiet.any { q -> i in q until q + rate / 5 }) 0f else 0.5f }
        val c = com.showcase.desertant.meetings.quietChunks(x, rate)
        assertEquals(3, c.size)
        assertEquals(0, c.first().first)
        assertEquals(n, c.last().second)
        c.zipWithNext().forEach { (a, b) -> assertEquals(a.second, b.first) }
        assertTrue(kotlin.math.abs(c[0].second - (rate * 603 + rate / 10)) < rate / 5)
    }
}
