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

    private val _voice = MutableStateFlow(prefs.getString(VOICE, null)?.let { runCatching { Voice.valueOf(it) }.getOrNull() } ?: Voice.DEFAULT)
    val voice: StateFlow<Voice> = _voice.asStateFlow()

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
