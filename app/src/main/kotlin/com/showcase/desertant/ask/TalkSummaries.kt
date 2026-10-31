package com.showcase.desertant.ask

import android.content.Context
import com.showcase.desertant.app
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.data.AppDatabase
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.TalkSummary
import com.showcase.desertant.privacy.LedgerEntry
import com.showcase.desertant.speech.SpeechPacks
import com.showcase.desertant.ui.meetings.decodeUtterances
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** A language summaries can be written in: its English name (what the model is told) and its own. */
data class SummaryLanguage(val english: String, val native: String)

/** Where summaries go and in which language. Stored on the phone. */
class SummarySettings(context: Context) {
    private val prefs = context.getSharedPreferences("summaries", Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(prefs.getString(LANGUAGE, null) ?: phoneLanguage())
    private val _auto = MutableStateFlow(prefs.getBoolean(AUTO, true))
    private val _done = MutableStateFlow(prefs.getStringSet(DONE, emptySet()).orEmpty().toSet())

    /** To-dos ticked off, as "talkId/index". */
    val done: StateFlow<Set<String>> = _done.asStateFlow()

    fun toggleDone(talkId: Long, index: Int) {
        val key = "$talkId/$index"
        val next = if (key in _done.value) _done.value - key else _done.value + key
        prefs.edit().putStringSet(DONE, next).apply()
        _done.value = next
    }

    /** An English language name, or [SAME_AS_TALK]. */
    val language: StateFlow<String> = _language.asStateFlow()

    /** Summarise each talk as soon as its notes are ready (when the model is on the phone). */
    val auto: StateFlow<Boolean> = _auto.asStateFlow()

    fun setLanguage(v: String) { prefs.edit().putString(LANGUAGE, v).apply(); _language.value = v }
    fun setAuto(v: Boolean) { prefs.edit().putBoolean(AUTO, v).apply(); _auto.value = v }
    fun clear() { prefs.edit().clear().apply(); _language.value = phoneLanguage(); _auto.value = true; _done.value = emptySet() }

    /** The language to write [m]'s summary in. */
    fun languageFor(m: MeetingEntity): String = language.value.takeUnless { it == SAME_AS_TALK } ?: talkLanguage(m) ?: phoneLanguage()

    companion object {
        private const val LANGUAGE = "language"
        private const val AUTO = "auto"
        private const val DONE = "done"
        const val SAME_AS_TALK = "same as the talk"

        fun phoneLanguage(): String = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

        fun talkLanguage(m: MeetingEntity): String? = m.language?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).takeIf { n -> n.isNotBlank() && !n.equals(it, true) } }

        /** Languages Gemma writes well, most useful first; the list on the settings page. */
        val LANGUAGES = listOf(
            SummaryLanguage("English", "English"), SummaryLanguage("Hindi", "हिन्दी"), SummaryLanguage("Bengali", "বাংলা"),
            SummaryLanguage("Marathi", "मराठी"), SummaryLanguage("Telugu", "తెలుగు"), SummaryLanguage("Tamil", "தமிழ்"),
            SummaryLanguage("Gujarati", "ગુજરાતી"), SummaryLanguage("Urdu", "اردو"), SummaryLanguage("Kannada", "ಕನ್ನಡ"),
            SummaryLanguage("Malayalam", "മലയാളം"), SummaryLanguage("Punjabi", "ਪੰਜਾਬੀ"), SummaryLanguage("Odia", "ଓଡ଼ିଆ"),
            SummaryLanguage("Spanish", "Español"), SummaryLanguage("French", "Français"), SummaryLanguage("German", "Deutsch"),
            SummaryLanguage("Italian", "Italiano"), SummaryLanguage("Portuguese", "Português"), SummaryLanguage("Dutch", "Nederlands"),
            SummaryLanguage("Arabic", "العربية"), SummaryLanguage("Indonesian", "Bahasa Indonesia"), SummaryLanguage("Japanese", "日本語"),
            SummaryLanguage("Chinese", "中文"), SummaryLanguage("Korean", "한국어"), SummaryLanguage("Russian", "Русский"), SummaryLanguage("Turkish", "Türkçe"),
        )
    }
}

/**
 * Summaries for talks and calls, written on the phone by the ask model after
 * the transcript is ready (or when the user asks). Progress shows on the talk.
 */
object TalkSummaries {
    private val _running = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** Talks being summarised right now (or waiting to be), with what's happening. */
    val running: StateFlow<Map<Long, String>> = _running.asStateFlow()

    private val _failures = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** Why the last try for a talk didn't work, until the next try. */
    val failures: StateFlow<Map<Long, String>> = _failures.asStateFlow()

    fun queued(id: Long) {
        _failures.update { it - id }
        _running.update { it + (id to "Waiting for the phone to be free…") }
    }

    fun failed(id: Long, message: String) = _failures.update { it + (id to message) }

    fun decode(json: String?): TalkSummary? = json?.let { runCatching { StoreJson.decodeFromString(TalkSummary.serializer(), it) }.getOrNull() }

    /** The transcript, people and date of [m], ready for the summarizer. */
    suspend fun talkOf(db: AppDatabase, m: MeetingEntity, zone: ZoneId = ZoneId.systemDefault()): TalkSummarizer.Talk {
        val speakers = db.speakers().forMeetingNow(m.id)
        val profiles = db.profiles().allNow().associateBy { it.id }
        fun name(index: Int?): String {
            val s = speakers.firstOrNull { it.index == index } ?: return "Someone"
            val p = s.profileId?.let(profiles::get) ?: return "Speaker ${s.index + 1}"
            return p.name
        }
        val people = speakers.map { s ->
            val p = s.profileId?.let(profiles::get)
            when {
                p == null -> "Speaker ${s.index + 1} (not named)"
                p.isMe -> "${p.name} (the user)"
                else -> p.name
            }
        }.distinct()
        return TalkSummarizer.Talk(
            title = m.title,
            date = Instant.ofEpochMilli(m.createdAt).atZone(zone).toLocalDate(),
            durationSec = m.durationSec,
            language = SummarySettings.talkLanguage(m),
            people = people,
            lines = decodeUtterances(m.utterancesJson).map { TalkSummarizer.Line(it.start, name(it.speaker), it.text) },
        )
    }

    /** Summarise talk [id] in [language] (default: the setting). Throws if it can't. */
    suspend fun summarize(context: Context, id: Long, language: String? = null) {
        val app = context.app
        val m = app.db.meetings().get(id) ?: return
        if (m.status != MeetingStatus.READY) return
        val writeIn = language ?: app.summaries.languageFor(m)
        _running.update { it + (id to "Waking the model up…") }
        AskModel.keep()
        try {
            // The speech models are done with; free their memory for this one.
            listOf(ModelKind.VOZ, ModelKind.WHO, ModelKind.EAR, ModelKind.CLEAR).forEach { runCatching { app.hub.unload(it) } }
            SpeechPacks.closeRecognizer()
            val engine = AskModel.engine(context)
            val talk = talkOf(app.db, m)
            val summary = withContext(Dispatchers.Default) {
                TalkSummarizer(LiteRtText(engine), AskModel.NAME).summarize(talk, writeIn) { p -> _running.update { it + (id to p) } }
            }
            app.db.meetings().setSummary(id, StoreJson.encodeToString(TalkSummary.serializer(), summary))
            app.ledger.add(LedgerEntry.TALKS_SUMMARISED)
        } finally {
            _running.update { it - id }
            AskModel.releaseSoon(app.scope)
        }
    }
}
