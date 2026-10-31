package com.showcase.desertant.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What Burrow did, step by step: model installs (claimed, copied from another
 * app, downloading, checksum ok), speech packs, failures and crashes. Shown
 * on the logs screen, where the user copies it to report a problem. Kept on
 * the phone only (`filesDir/logs/app.log`), newest last, at most [LIMIT] lines.
 */
class AppLog(context: Context) {
    private val file = File(context.filesDir, "logs/app.log").apply { parentFile?.mkdirs() }
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow(runCatching { file.readLines().takeLast(LIMIT) }.getOrDefault(emptyList()))
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    /** Adds `2026-10-31 16:59:22 [models] desertant.voz: ready (sibling)`. Safe from any thread. */
    fun add(area: String, message: String) {
        val line = "${synchronized(stamp) { stamp.format(Date()) }} [$area] $message"
        synchronized(this) {
            _lines.value = (_lines.value + line).takeLast(LIMIT)
            runCatching {
                file.appendText(line + "\n")
                // Keep the file short too: rewrite it now and then.
                if (file.length() > 256_000) file.writeText(_lines.value.joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** Everything, with a header saying which app and phone it came from. */
    fun text(header: String): String = header + "\n" + _lines.value.joinToString("\n") + "\n"

    fun clear() = synchronized(this) {
        _lines.value = emptyList()
        file.delete()
    }

    /** Crashes are logged before the app dies, so the next start can show them. */
    fun catchCrashes() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { add("crash", "${e.javaClass.simpleName}: ${e.message} at ${e.stackTrace.take(3).joinToString(" < ")}") }
            previous?.uncaughtException(thread, e)
        }
    }

    companion object {
        const val LIMIT = 500
    }
}
