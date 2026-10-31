package com.showcase.desertant.chats

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads a chat the user exported themselves. WhatsApp's "Export chat" writes
 * one line per message, in the phone's date format:
 *
 *     30/10/2024, 18:12 - Priya Shah: see you at 7       (Android, 24 h)
 *     10/30/24, 6:12 PM - Priya Shah: see you at 7       (Android, US)
 *     [30/10/2024, 18:12:05] Priya Shah: see you at 7    (iPhone)
 *
 * Lines without that header continue the message above. Lines with a date but
 * no "name:" are WhatsApp's own notices (encryption, "X added Y") and are skipped.
 */
object ChatParser {
    data class Message(val at: Long, val sender: String, val text: String)

    data class Chat(val title: String?, val messages: List<Message>) {
        val senders: List<Pair<String, Int>> get() = messages.groupingBy { it.sender }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    private val HEADER = Regex(
        """^\[?(\d{1,4})[./-](\d{1,2})[./-](\d{1,4}),?\s+(\d{1,2})[:.](\d{2})(?:[:.](\d{2}))?\s*([AaPp]\.?\s?[Mm]\.?)?\]?\s*(?:-\s+)?(.*)$""",
    )

    private val INVISIBLE = Regex("[‎‏‪-‮⁦-⁩﻿]")

    private const val MEDIA = "(a photo, video or file — not included)"

    fun parse(text: String, fileName: String? = null, zone: ZoneId = ZoneId.systemDefault()): Chat {
        data class Raw(val a: Int, val b: Int, val c: Int, val h: Int, val m: Int, val s: Int, val ampm: String?, val sender: String?, val body: StringBuilder)

        val raws = ArrayList<Raw>()
        text.replace("\r\n", "\n").split('\n').forEach { line0 ->
            val line = line0.replace(INVISIBLE, "").replace(' ', ' ').replace(' ', ' ')
            val h = HEADER.matchEntire(line)
            if (h != null) {
                val rest = h.groupValues[8]
                val colon = rest.indexOf(": ")
                // A sender name is short and comes before the first ": ".
                val sender = if (colon in 1..60) rest.substring(0, colon).trim() else null
                val body = if (sender != null) rest.substring(colon + 2) else rest
                raws += Raw(
                    h.groupValues[1].toInt(), h.groupValues[2].toInt(), h.groupValues[3].toInt(),
                    h.groupValues[4].toInt(), h.groupValues[5].toInt(), h.groupValues[6].toIntOrNull() ?: 0,
                    h.groupValues[7].takeIf { it.isNotBlank() }?.lowercase()?.filter { it == 'a' || it == 'p' },
                    sender, StringBuilder(body),
                )
            } else if (raws.isNotEmpty()) {
                raws.last().body.append('\n').append(line)
            }
        }

        // Day-first or month-first? Decide once for the whole file.
        val yearFirst = raws.any { it.a > 31 }
        val monthFirst = !yearFirst && (raws.any { it.b > 12 } || (raws.none { it.a > 12 } && raws.any { it.ampm != null }))

        val messages = raws.mapNotNull { r ->
            val sender = r.sender ?: return@mapNotNull null
            val (y, mo, d) = when {
                yearFirst -> Triple(r.a, r.b, r.c)
                monthFirst -> Triple(r.c, r.a, r.b)
                else -> Triple(r.c, r.b, r.a)
            }
            val year = if (y < 100) 2000 + y else y
            var hour = r.h
            if (r.ampm == "p" && hour < 12) hour += 12
            if (r.ampm == "a" && hour == 12) hour = 0
            val at = runCatching { LocalDateTime.of(year, mo, d, hour, r.m, r.s).atZone(zone).toInstant().toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            Message(at, sender, clean(r.body.toString()))
        }.filter { it.text.isNotBlank() }

        return Chat(titleFrom(fileName), messages)
    }

    private fun clean(body: String): String {
        val t = body.trim()
        return when {
            t == "<Media omitted>" || t.endsWith(" omitted") || t.startsWith("<attached:") -> MEDIA
            else -> t
        }
    }

    /** "WhatsApp Chat with Priya Shah.txt" → "Priya Shah". */
    fun titleFrom(fileName: String?): String? {
        val base = fileName?.substringBeforeLast('.')?.trim() ?: return null
        val with = Regex("(?i)^whatsapp chat (?:with|-)\\s*(.+)$").find(base)?.groupValues?.get(1)
        return (with ?: base).trim().takeIf { it.isNotEmpty() && !it.equals("_chat", ignoreCase = true) }
    }

    /** Whether text looks like an exported chat at all (so plain shared text goes elsewhere). */
    fun looksLikeChat(text: String) = text.lineSequence().take(60).count { HEADER.matches(it.replace(INVISIBLE, "")) && it.contains(": ") } >= 2
}
