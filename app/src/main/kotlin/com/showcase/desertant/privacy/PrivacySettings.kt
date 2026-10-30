package com.showcase.desertant.privacy

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * The user's privacy choices. Each is a plain switch on the privacy page, and
 * each is enforced in code, not just displayed:
 *  - [neverOnline]: the model hub refuses to touch the network.
 *  - [lockApp]: the app asks for a fingerprint when it comes back.
 *  - [autoDeleteAudio]: talk recordings older than 30 days are deleted; notes stay.
 *  - [learnVoices]: naming a speaker also teaches Burrow their voice.
 *  - [secureScreens]: no screenshots, and a blank card in recent apps.
 */
class PrivacySettings(context: Context) {
    private val prefs = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)

    private fun flag(key: String, default: Boolean) = MutableStateFlow(prefs.getBoolean(key, default))

    private val _onboarded = flag(ONBOARDED, false)
    private val _neverOnline = flag(NEVER_ONLINE, false)
    private val _lockApp = flag(LOCK_APP, false)
    private val _autoDeleteAudio = flag(AUTO_DELETE_AUDIO, false)
    private val _learnVoices = flag(LEARN_VOICES, true)
    private val _secureScreens = flag(SECURE_SCREENS, false)

    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()
    val neverOnline: StateFlow<Boolean> = _neverOnline.asStateFlow()
    val lockApp: StateFlow<Boolean> = _lockApp.asStateFlow()
    val autoDeleteAudio: StateFlow<Boolean> = _autoDeleteAudio.asStateFlow()
    val learnVoices: StateFlow<Boolean> = _learnVoices.asStateFlow()
    val secureScreens: StateFlow<Boolean> = _secureScreens.asStateFlow()

    fun setOnboarded(v: Boolean) = put(ONBOARDED, _onboarded, v)
    fun setNeverOnline(v: Boolean) = put(NEVER_ONLINE, _neverOnline, v)
    fun setLockApp(v: Boolean) = put(LOCK_APP, _lockApp, v)
    fun setAutoDeleteAudio(v: Boolean) = put(AUTO_DELETE_AUDIO, _autoDeleteAudio, v)
    fun setLearnVoices(v: Boolean) = put(LEARN_VOICES, _learnVoices, v)
    fun setSecureScreens(v: Boolean) = put(SECURE_SCREENS, _secureScreens, v)

    private fun put(key: String, flow: MutableStateFlow<Boolean>, v: Boolean) {
        prefs.edit().putBoolean(key, v).apply()
        flow.value = v
    }

    fun clear() {
        prefs.edit().clear().apply()
        _onboarded.value = false
        _neverOnline.value = false
        _lockApp.value = false
        _autoDeleteAudio.value = false
        _learnVoices.value = true
        _secureScreens.value = false
    }

    private companion object {
        const val ONBOARDED = "onboarded"
        const val NEVER_ONLINE = "never_online"
        const val LOCK_APP = "lock_app"
        const val AUTO_DELETE_AUDIO = "auto_delete_audio"
        const val LEARN_VOICES = "learn_voices"
        const val SECURE_SCREENS = "secure_screens"
    }
}

/** What Burrow did, counted on the phone. Nothing here is ever sent anywhere. */
enum class LedgerEntry(val one: String, val many: String) {
    PHOTOS_CHECKED("checked 1 photo", "checked %d photos"),
    TALKS_TRANSCRIBED("transcribed 1 talk", "transcribed %d talks"),
    TEXTS_MASKED("masked 1 text", "masked %d texts"),
    PHOTOS_CHECKED_BEFORE_SHARING("checked 1 photo before sharing", "checked %d photos before sharing"),
    VOICES_LEARNED("learned 1 voice, with your OK", "learned %d voices, with your OK"),
    PHOTOS_VAULTED("locked 1 photo in the vault", "locked %d photos in the vault"),
    ;

    fun line(n: Int) = if (n == 1) one else many.format(n)
}

class Ledger(context: Context) {
    private val prefs = context.getSharedPreferences("ledger", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)

    /** Bumps on every change, so screens can recompose. */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Synchronized
    fun add(entry: LedgerEntry, n: Int = 1) {
        if (n <= 0) return
        val day = dayKey(entry, LocalDate.now())
        val total = totalKey(entry)
        prefs.edit().putInt(day, prefs.getInt(day, 0) + n).putInt(total, prefs.getInt(total, 0) + n).apply()
        _version.value++
    }

    fun today(entry: LedgerEntry): Int = prefs.getInt(dayKey(entry, LocalDate.now()), 0)

    fun total(entry: LedgerEntry): Int = prefs.getInt(totalKey(entry), 0)

    /** When Burrow was set up, for "since you set Burrow up". */
    val since: Long
        get() = prefs.getLong(SINCE, 0L).takeIf { it > 0 } ?: System.currentTimeMillis().also { prefs.edit().putLong(SINCE, it).apply() }

    fun clear() {
        prefs.edit().clear().apply()
        _version.value++
    }

    private fun dayKey(e: LedgerEntry, d: LocalDate) = "${e.name}@$d"
    private fun totalKey(e: LedgerEntry) = "${e.name}@total"

    companion object {
        private const val SINCE = "since"

        /**
         * Bytes this app has sent over the network since the phone started, as
         * Android counts them. Burrow has no upload code; this is the honest
         * number behind "nothing left this phone": only model-download requests.
         */
        fun bytesSent(): Long = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0L)
    }
}
