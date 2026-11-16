package com.showcase.desertant.ui.theme

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.showcase.desertant.app
import com.showcase.desertant.privacy.findActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How Burrow looks: light or dark, and how it is lettered (handwritten or
 * formal, and the voice within that). Chosen in Settings, not taken from the phone.
 */
class Appearance(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(prefs.getString(MODE, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    init {
        val version = prefs.getInt(TYPE_VERSION, 1)
        if (version < TYPE_NOW) {
            val stored = prefs.getString(VOICE, null)
            val kept = voiceAfterUpgrade(version, stored)
            prefs.edit()
                .apply { if (kept == null) remove(VOICE) else if (kept != stored) putString(VOICE, kept) }
                .remove(TYPE_NOTE)
                .putInt(TYPE_VERSION, TYPE_NOW)
                .apply()
        }
    }

    private val _voice = MutableStateFlow(Voice.fromStored(prefs.getString(VOICE, null)) ?: Voice.DEFAULT)
    val voice: StateFlow<Voice> = _voice.asStateFlow()

    fun setMode(m: ThemeMode) {
        prefs.edit().putString(MODE, m.name).apply()
        _mode.value = m
    }

    /** Picks a voice, and with it the style it belongs to. Stored, so it sticks. */
    fun setVoice(v: Voice) {
        prefs.edit().putString(VOICE, v.name).putString(LAST + v.style.name, v.name).apply()
        _voice.value = v
    }

    /** Switches between handwritten and formal, back to the voice last used in that style. */
    fun setStyle(style: TypeStyle) {
        if (_voice.value.style == style) return
        val last = Voice.fromStored(prefs.getString(LAST + style.name, null))?.takeIf { it.style == style }
        setVoice(last ?: style.defaultVoice)
    }

    companion object {
        private const val MODE = "mode"
        private const val VOICE = "voice"
        private const val LAST = "voice."
        private const val TYPE_VERSION = "typeVersion"
        private const val TYPE_NOTE = "typeNote"

        /** 1: up to 3.9 (nothing stored). 2: 4.0, which moved everyone to the plain font. 3: 4.1. */
        private const val TYPE_NOW = 3

        /**
         * The stored voice to keep when an install reaches 4.1 from type [version];
         * null means "nothing stored", which shows the handwritten default.
         *
         * 4.0 (version 2) overwrote every install's voice with the plain "FRIENDLY"
         * and a deliberate pick of it can't be told from that, so those go back to
         * the default once. Any other voice at version 2 was picked by hand in 4.0
         * and stays. Installs straight from 3.9 or earlier (version 1) keep what
         * they had, including the two voices 4.0 had removed.
         */
        fun voiceAfterUpgrade(version: Int, stored: String?): String? = when {
            Voice.fromStored(stored) == null -> null
            version == 2 && stored == Voice.FRIENDLY.name -> null
            else -> stored
        }
    }
}

/**
 * The theme as the user set it, for every activity. Also points the status and
 * navigation bar icons the right way, since a dark Burrow on a light phone (or
 * the reverse) would otherwise leave them invisible.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val appearance = LocalContext.current.app.appearance
    val mode by appearance.mode.collectAsState()
    val voice by appearance.voice.collectAsState()
    BurrowTheme(mode, voice) {
        val dark = isBurrowDark
        val paper = burrow.paper.toArgb()
        val activity = LocalContext.current.findActivity() as? ComponentActivity
        LaunchedEffect(dark, activity) {
            val style = if (dark) SystemBarStyle.dark(paper) else SystemBarStyle.light(paper, paper)
            activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        content()
    }
}
