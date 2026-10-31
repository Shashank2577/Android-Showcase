package com.showcase.desertant.people

import com.showcase.desertant.data.AppDatabase

/**
 * Call recordings, linked to people by the name the phone app gave the file.
 * Samsung's recorder saves "Call recording Priya Shah_241030_181205.m4a" (or
 * the number when the caller isn't a contact); others use similar patterns.
 */
object CallLinks {
    private val PREFIXES = listOf("call recording ", "call_recording_", "call recording_", "call_", "record_", "recording ")

    /** The name or number in a call recording's file name, or null if there isn't one. */
    fun whoFromFileName(fileName: String): String? {
        var s = fileName.substringBeforeLast('.').trim()
        PREFIXES.firstOrNull { s.lowercase().startsWith(it) }?.let { s = s.drop(it.length) }
        // Trailing date and time: _241030_181205, _20241030181205, -20241030-181205, " 2024-10-30 18-12-05".
        s = s.replace(Regex("[ _-]+\\d{2,4}[-_.]?\\d{2}[-_.]?\\d{2}([ _-]+\\d{2}[-_.]?\\d{2}([-_.]?\\d{2})?)?$"), "")
        s = s.replace(Regex("[ _-]+\\d{12,14}$"), "")
        // Xiaomi style "Priya Shah(+91 98…)": keep both; matching uses either.
        s = s.replace('_', ' ').trim()
        return s.takeIf { it.isNotEmpty() && it.any { c -> c.isLetterOrDigit() } }
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
