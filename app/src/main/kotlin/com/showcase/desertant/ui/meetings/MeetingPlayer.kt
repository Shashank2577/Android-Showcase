package com.showcase.desertant.ui.meetings

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Plays a saved recording with seeking, for jumping to any line of the transcript. */
class MeetingPlayer(path: String) {
    private val player: MediaPlayer? = runCatching {
        MediaPlayer().apply { setDataSource(path); prepare() }
    }.getOrNull()

    var playing by mutableStateOf(false)
        private set
    var positionSec by mutableDoubleStateOf(0.0)
        internal set
    val durationSec: Double = (player?.duration ?: 0) / 1000.0
    val available: Boolean get() = player != null

    init {
        player?.setOnCompletionListener { playing = false }
    }

    fun toggle() = if (playing) pause() else play()

    fun play() {
        player?.start()
        playing = player != null
    }

    fun pause() {
        player?.pause()
        playing = false
    }

    fun seek(sec: Double, andPlay: Boolean = true) {
        player?.seekTo((sec * 1000).toLong(), MediaPlayer.SEEK_CLOSEST)
        positionSec = sec
        if (andPlay) play()
    }

    internal fun poll() {
        player?.let { positionSec = it.currentPosition / 1000.0 }
    }

    fun release() = player?.release()
}

@Composable
fun rememberMeetingPlayer(path: String): MeetingPlayer {
    val player = remember(path) { MeetingPlayer(path) }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, player.playing) {
        while (player.playing) {
            player.poll()
            delay(150)
        }
    }
    return player
}
