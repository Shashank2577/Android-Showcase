package com.showcase.desertant.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Plays float PCM straight from memory, optionally just a time range of it. */
class ClipPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** The id passed to [play] while it is playing, else null. */
    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()

    /** Playback position in seconds from the start of the full clip. */
    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position.asStateFlow()

    fun toggle(id: String, samples: FloatArray, sampleRate: Int, fromSec: Double = 0.0, toSec: Double? = null) {
        if (_playing.value == id) stop() else play(id, samples, sampleRate, fromSec, toSec)
    }

    fun play(id: String, samples: FloatArray, sampleRate: Int, fromSec: Double = 0.0, toSec: Double? = null) {
        stop()
        val from = (fromSec * sampleRate).toInt().coerceIn(0, samples.size)
        val to = ((toSec ?: Double.MAX_VALUE / 2) * sampleRate).toLong().coerceIn(from.toLong(), samples.size.toLong()).toInt()
        if (to <= from) return
        _playing.value = id
        job = scope.launch {
            val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuffer, sampleRate))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                track.play()
                var i = from
                val chunk = sampleRate / 10
                while (isActive && i < to) {
                    val n = track.write(samples, i, min(chunk, to - i), AudioTrack.WRITE_BLOCKING)
                    if (n <= 0) break
                    i += n
                    _position.value = (from + track.playbackHeadPosition).toDouble() / sampleRate
                }
                // Let the buffered tail play out.
                val total = to - from
                while (isActive && track.playbackHeadPosition < total) {
                    _position.value = (from + track.playbackHeadPosition).toDouble() / sampleRate
                    delay(30)
                }
            } finally {
                runCatching { track.stop() }
                track.release()
                if (_playing.value == id) _playing.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _playing.value = null
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
