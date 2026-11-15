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

/** How Burrow looks: light or dark, and how handwritten its quiet text is. Chosen in Settings, not taken from the phone. */
class Appearance(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(prefs.getString(MODE, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    init {
        // 4.0 made reading text a plain font. Every earlier install stored "neat hand"
        // (the old default), so changing only the default would fix nothing: move
        // everyone to the new default once, and say so once on the settings page.
        if (prefs.getInt(TYPE_VERSION, 1) < 2) {
            val upgraded = prefs.contains(VOICE)
            prefs.edit()
                .putString(VOICE, Voice.DEFAULT.name)
                .putInt(TYPE_VERSION, 2)
                .putBoolean(TYPE_NOTE, upgraded)
                .apply()
        }
    }

    private val _voice = MutableStateFlow(Voice.fromStored(prefs.getString(VOICE, null)) ?: Voice.DEFAULT)
    val voice: StateFlow<Voice> = _voice.asStateFlow()

    private val _typeNote = MutableStateFlow(prefs.getBoolean(TYPE_NOTE, false))

    /** Whether to say once, on the settings page, that reading text changed to a plain font. */
    val typeNote: StateFlow<Boolean> = _typeNote.asStateFlow()

    fun dismissTypeNote() {
        prefs.edit().putBoolean(TYPE_NOTE, false).apply()
        _typeNote.value = false
    }

    fun setMode(m: ThemeMode) {
        prefs.edit().putString(MODE, m.name).apply()
        _mode.value = m
    }

    fun setVoice(v: Voice) {
        prefs.edit().putString(VOICE, v.name).apply()
        _voice.value = v
    }

    private companion object {
        const val MODE = "mode"
        const val VOICE = "voice"
        const val TYPE_VERSION = "typeVersion"
        const val TYPE_NOTE = "typeNote"
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
