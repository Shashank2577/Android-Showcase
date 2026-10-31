package com.showcase.desertant.people

import com.showcase.desertant.data.AppDatabase

/**
 * Call recordings, linked to people by the name the recording app gave the
 * file. Phone apps that record calls (Samsung, Xiaomi, OnePlus, Oppo, Realme,
 * Vivo, Honor, Huawei, Motorola and others) name the file after the contact,
 * or the number when there isn't one, plus a date — each in its own pattern:
 *
 *     Call recording Priya Shah_241030_181205.m4a
 *     Priya Shah(+447700900123)_20241030181205.mp3
 *     Priya Shah 2024-10-30 18-12-05.m4a
 *     +447700900123_20241030181205.amr
 *     Call with Priya Shah.m4a                 (e.g. iPhone call audio saved from Notes)
 *
 * Rather than one pattern per brand, Burrow peels off the parts that aren't a
 * name (prefix words, dates, times, counters) and keeps what's left.
 */
object CallLinks {
    /** Words a recording app puts before the name, in a few languages. */
    private val PREFIX = Regex(
        "(?i)^(call\\s*recording|call\\s*rec|recorded\\s*call|phone\\s*call|call\\s*(with|from|to)|call|record(ing)?|anruf(\\s*(mit|von))?|appel(\\s*(avec|de))?|llamada(\\s*(con|de))?|chiamata(\\s*(con|da))?|chamada(\\s*(com|de))?|gesprek(\\s*met)?)([\\s_:@-]+|$)",
    )

    /** A date and/or time at the end: 2024-10-30 18-12-05, 30.10.2024 18.12, 18:12, (1). */
    private val TRAILING = listOf(
        Regex("[\\s@-]*\\(\\d{1,3}\\)$"),
        Regex("[\\s@-]+\\d{12,14}$"),
        Regex("[\\s@-]+\\d{1,4}[-.]?\\d{1,2}[-.]?\\d{2,4}([\\sT-]+\\d{1,2}[-.:h]?\\d{2}([-.:m]?\\d{2})?s?)?$"),
        Regex("[\\s@-]+\\d{1,2}[-.:h]\\d{2}([-.:m]\\d{2})?$"),
    )

    /** An underscore-separated part that's a date, time or counter: 241030, 20241030181205, 2024-10-30, 181205(1). */
    private val DATE_PART = Regex("\\d{4,14}(\\(\\d{1,3}\\))?|\\d{1,4}[-.]\\d{1,2}[-.]\\d{1,4}|\\d{1,2}[-.:]\\d{2}([-.:]\\d{2})?|\\(\\d{1,3}\\)")

    /** Path or name words that mark a call recording rather than a voice memo. */
    private val CALL_WORDS = listOf(
        "call", "phonerecord", "phone_record", "phone record", "anruf", "appel", "llamada", "chiamat", "chamada", "ligaç", "gesprek",
        "звон", "通话", "通話", "통화", "बातचीत", "कॉल",
    )

    /** Whether a recording looks like a phone call, from its folder or its name. */
    fun isCallRecording(path: String, name: String): Boolean {
        val p = path.lowercase()
        val n = name.lowercase()
        // A phone number in what's left of the name ("+447700900123", "Priya Shah(+447700900123)") means a call.
        return CALL_WORDS.any { it in p || it in n } || (whoFromFileName(name)?.let { PHONE_IN_NAME.containsMatchIn(it) } == true)
    }

    private val PHONE_IN_NAME = Regex("(^|[(@\\s])\\+?\\d[\\d ()-]{6,}\\d")

    private fun looksLikeNumber(s: String) = s.count { it.isDigit() } >= 6 && s.all { it.isDigit() || it in "+ ()-" }

    /** The name or number in a call recording's file name, or null if there isn't one. */
    fun whoFromFileName(fileName: String): String? {
        // Most recorders join name, date and time with underscores: drop the date and time parts first.
        val parts = fileName.substringBeforeLast('.').trim().split('_').toMutableList()
        while (parts.size > 1 && DATE_PART.matches(parts.last().trim())) parts.removeAt(parts.lastIndex)
        var s = parts.joinToString(" ").trim()
        // Some apps prefix twice ("Call recording - Call with Priya").
        repeat(2) { s = s.replace(PREFIX, "").trim().trimStart('-', ':', '@').trim() }
        // A number on its own ("+44 7700 900123") is kept whole; its digit groups aren't a date.
        if (!looksLikeNumber(s)) {
            var changed = true
            while (changed) {
                val before = s
                TRAILING.forEach { r -> s = s.replace(r, "").trim() }
                changed = s != before && s.isNotEmpty()
            }
        }
        s = s.trim('-', '@', ' ')
        if (s.isEmpty() || !s.any { it.isLetterOrDigit() }) return null
        // Only a date, a time or a counter left (an unnamed recording): no one to link.
        if (s.all { it.isDigit() || it in " -.:()" } && !looksLikeNumber(s)) return null
        if (DATE_PART.matches(s) && s.count { it.isDigit() } < 9) return null
        return s
    }

    /** Link every call recording Burrow can now place; links the user made by hand stay. */
    suspend fun relinkAll(db: AppDatabase) {
        val people = db.profiles().allNow()
        db.meetings().calls().forEach { m ->
            if (m.callProfileId != null && people.any { it.id == m.callProfileId }) return@forEach
            val who = m.callWith ?: return@forEach
            val p = Contacts.match(who, people)
            if (p?.id != m.callProfileId) db.meetings().linkCall(m.id, p?.id)
        }
    }
}
