package com.showcase.desertant.chats

import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.people.CallLinks
import com.showcase.desertant.people.Contacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Chat exports, call recording names and matching them to people. Plain JVM, no Android. */
class ChatParserTest {
    private val utc = ZoneOffset.UTC
    private fun t(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) = LocalDateTime.of(y, mo, d, h, mi, s).toInstant(utc).toEpochMilli()

    @Test fun androidDayFirst24h() {
        val chat = ChatParser.parse(
            """
            30/10/2024, 18:12 - Messages and calls are end-to-end encrypted. No one outside of this chat can read them.
            30/10/2024, 18:12 - Priya Shah: see you at 7
            30/10/2024, 18:13 - Sam: ok!
            and bring the deck
            31/10/2024, 09:01 - Priya Shah: <Media omitted>
            """.trimIndent(),
            "WhatsApp Chat with Priya Shah.txt", utc,
        )
        assertEquals("Priya Shah", chat.title)
        assertEquals(3, chat.messages.size)
        assertEquals(t(2024, 10, 30, 18, 12), chat.messages[0].at)
        assertEquals("ok!\nand bring the deck", chat.messages[1].text)
        assertTrue(chat.messages[2].text.contains("not included"))
        assertEquals(listOf("Priya Shah" to 2, "Sam" to 1), chat.senders)
    }

    @Test fun androidUs12hWithNarrowSpaces() {
        val chat = ChatParser.parse("10/30/24, 6:12\u202fPM - Priya: hi\n10/31/24, 12:05\u202fAM - Sam: late", null, utc)
        assertEquals(t(2024, 10, 30, 18, 12), chat.messages[0].at)
        assertEquals(t(2024, 10, 31, 0, 5), chat.messages[1].at)
    }

    @Test fun iphoneBracketsAndInvisibleMarks() {
        val chat = ChatParser.parse("\u200e[30/10/2024, 18:12:05] Priya Shah: hi\n[30/10/2024, 18:13:00] Sam: \u200eimage omitted", "_chat.txt", utc)
        assertNull(chat.title)
        assertEquals(t(2024, 10, 30, 18, 12, 5), chat.messages[0].at)
        assertEquals("Priya Shah", chat.messages[0].sender)
        assertTrue(chat.messages[1].text.contains("not included"))
    }

    @Test fun groupNoticesAreSkipped() {
        val chat = ChatParser.parse("01.02.25, 10:00 - Sam added Priya\n01.02.25, 10:01 - Priya: thanks: really", null, utc)
        assertEquals(1, chat.messages.size)
        assertEquals("thanks: really", chat.messages[0].text)
        assertEquals(t(2025, 2, 1, 10, 1), chat.messages[0].at)
    }

    @Test fun plainTextIsNotAChat() {
        assertFalse(ChatParser.looksLikeChat("Hey, can you mask this? My number is 555 1234."))
        assertTrue(ChatParser.looksLikeChat("30/10/2024, 18:12 - A: x\n30/10/2024, 18:13 - B: y"))
    }

    @Test fun callRecordingNames() {
        assertEquals("Priya Shah", CallLinks.whoFromFileName("Call recording Priya Shah_241030_181205.m4a"))
        assertEquals("+44 7700 900123", CallLinks.whoFromFileName("Call recording +44 7700 900123_241030_181205.m4a"))
        assertEquals("Mum", CallLinks.whoFromFileName("Mum_20241030181205.mp3"))
        assertEquals("Priya Shah(+447700900123)", CallLinks.whoFromFileName("Priya Shah(+447700900123)_20241030181205.mp3"))
        assertNull(CallLinks.whoFromFileName("Call recording _241030_181205.m4a"))
    }

    /** File names as different phone makers' recorders write them. */
    @Test fun callRecordingNamesAcrossBrands() {
        val cases = mapOf(
            "Call recording Priya Shah_241030_181205.m4a" to "Priya Shah", // Samsung
            "Priya Shah(+447700900123)_20241030181205.mp3" to "Priya Shah(+447700900123)", // Xiaomi
            "Priya Shah 2024-10-30 18-12-05.m4a" to "Priya Shah", // OnePlus, Oppo, Realme
            "+447700900123_20241030181205.amr" to "+447700900123", // a number, no contact
            "Priya Shah@+447700900123_20241030181205.m4a" to "Priya Shah@+447700900123",
            "Call with Priya Shah.m4a" to "Priya Shah", // iPhone audio saved from Notes
            "Call_Priya Shah_20241030_181205.wav" to "Priya Shah",
            "Priya Shah_20241030_181205(1).mp3" to "Priya Shah",
            "Anruf mit Priya Shah 30.10.2024 18-12.m4a" to "Priya Shah",
            "Mum 18.12.m4a" to "Mum",
        )
        cases.forEach { (file, who) -> assertEquals(file, who, CallLinks.whoFromFileName(file)) }
        assertNull(CallLinks.whoFromFileName("20241030_181205.m4a"))
        assertNull(CallLinks.whoFromFileName("Recording 3.m4a"))
    }

    @Test fun callsVersusVoiceMemos() {
        assertTrue(CallLinks.isCallRecording("Recordings/Call/", "Priya Shah_241030_181205.m4a"))
        assertTrue(CallLinks.isCallRecording("MIUI/sound_recorder/call_rec/", "Priya Shah(+447700900123)_20241030181205.mp3"))
        assertTrue(CallLinks.isCallRecording("Music/Recordings/Call Recordings/", "x.m4a"))
        assertTrue("a bare number is a call", CallLinks.isCallRecording("Recordings/", "+447700900123_20241030181205.amr"))
        assertTrue(CallLinks.isCallRecording("Download/", "Call with Priya Shah.m4a"))
        assertTrue("a name with its number is a call", CallLinks.isCallRecording("", "Priya Shah(+447700900123)_20241030181205.mp3"))
        assertFalse(CallLinks.isCallRecording("Recordings/Voice Recorder/", "Lecture 3_241030_181205.m4a"))
        assertFalse(CallLinks.isCallRecording("Recordings/", "Voice 001.m4a"))
    }

    private val priya = ProfileEntity(2, "Priya Shah", 1, phones = "447700900123", aliases = "Priya 🌸")
    private val people = listOf(ProfileEntity(1, "Sam Carter", 0, isMe = true), priya, ProfileEntity(3, "Marco Rossi", 2))

    @Test fun matchingPeople() {
        assertEquals(2L, Contacts.match("+44 7700 900123", people)?.id)
        assertEquals(2L, Contacts.match("07700 900123", people)?.id)
        assertEquals(2L, Contacts.match("Priya Shah(+447700900123)", people)?.id)
        assertEquals(2L, Contacts.match("priya 🌸", people)?.id)
        assertEquals(3L, Contacts.match("Marco", people)?.id)
        assertNull(Contacts.match("Mum", people))
        assertNull(Contacts.match("Priya Patel", people))
    }

    @Test fun guessesWhoIsWho() {
        val chat = ChatParser.parse("30/10/2024, 18:12 - Priya 🌸: hi\n30/10/2024, 18:13 - Sammy: hey", "WhatsApp Chat with Priya.txt", utc)
        val who = ChatImporter.guess(chat, people)
        assertEquals(ChatImporter.Who.Person(2), who["Priya 🌸"])
        assertEquals(ChatImporter.Who.Me, who["Sammy"])
        val stranger = ChatParser.parse("30/10/2024, 18:12 - Ana: hi\n30/10/2024, 18:13 - Sammy: hey", "WhatsApp Chat with Ana.txt", utc)
        val w2 = ChatImporter.guess(stranger, people)
        assertEquals(ChatImporter.Who.New, w2["Ana"])
        assertEquals(ChatImporter.Who.Me, w2["Sammy"])
    }
}
