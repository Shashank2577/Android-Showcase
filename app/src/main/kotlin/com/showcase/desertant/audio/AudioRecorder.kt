package com.showcase.desertant.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Records mono float PCM from the microphone. Call [record] from a coroutine and
 * [stop] to finish; [record] then returns the whole take.
 */
class AudioRecorder {
    private val stopRequested = AtomicBoolean(false)

    fun stop() = stopRequested.set(true)

    /** Requires RECORD_AUDIO to be granted. [onProgress] gets the peak level (0..1) and elapsed seconds. */
    @SuppressLint("MissingPermission")
    suspend fun record(
        maxSeconds: Double = AudioDecoder.MAX_SECONDS,
        onProgress: (level: Float, seconds: Double) -> Unit,
    ): AudioClip = withContext(Dispatchers.IO) {
        stopRequested.set(false)
        // 48 kHz suits Clear's output rate; fall back for devices that refuse it.
        val rate = RATES.first {
            AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT) > 0
        }
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            max(minBuffer, rate * 4 / 5),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "The microphone could not be opened." }
        val out = FloatArrayBuilder(rate * 30)
        val chunk = FloatArray(rate / 20)
        try {
            record.startRecording()
            while (isActive && !stopRequested.get() && out.size < maxSeconds * rate) {
                val n = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (n < 0) error("Recording failed (error $n).")
                var peak = 0f
                for (i in 0 until n) {
                    out.add(chunk[i])
                    peak = max(peak, abs(chunk[i]))
                }
                onProgress(peak, out.size.toDouble() / rate)
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        AudioClip(out.toArray(), rate, "Recording")
    }

    private companion object {
        val RATES = listOf(48_000, 44_100, 16_000)
    }
}
