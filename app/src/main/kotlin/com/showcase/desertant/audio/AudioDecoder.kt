package com.showcase.desertant.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes the audio track of any file Android's media stack can read (WAV, MP3,
 * AAC/M4A, OGG/Opus, FLAC, and the audio of MP4/WebM video) to mono floats at
 * the file's own sample rate. The models resample internally, so no
 * conversion happens here.
 */
object AudioDecoder {

    /** Ten minutes keeps a 48 kHz clip near 110 MB of floats. */
    const val MAX_SECONDS = 600.0

    suspend fun decode(context: Context, uri: Uri, maxSeconds: Double = MAX_SECONDS): AudioClip =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: throw IllegalArgumentException("This file has no audio track.")
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME)!!

                var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var encoding = AudioFormat.ENCODING_PCM_16BIT

                val codec = MediaCodec.createDecoderByType(mime)
                val out = FloatArrayBuilder()
                var truncated = false
                try {
                    codec.configure(format, null, null, 0)
                    codec.start()
                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false
                    while (!outputDone) {
                        ensureActive()
                        if (!inputDone) {
                            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                            if (inIndex >= 0) {
                                val buffer = codec.getInputBuffer(inIndex)!!
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val f = codec.outputFormat
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        } else if (outIndex >= 0) {
                            val buffer = codec.getOutputBuffer(outIndex)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                appendMono(buffer.slice().order(ByteOrder.nativeOrder()), encoding, channels, out)
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            if (out.size >= maxSeconds * sampleRate) {
                                truncated = true
                                outputDone = true
                            }
                        }
                    }
                } finally {
                    runCatching { codec.stop() }
                    codec.release()
                }
                val samples = out.toArray()
                if (samples.isEmpty()) throw IllegalArgumentException("The audio track decoded to silence.")
                AudioClip(samples, sampleRate, displayName(context, uri), truncated)
            } finally {
                extractor.release()
            }
        }

    /** Average every frame's channels into one sample. */
    private fun appendMono(buffer: ByteBuffer, encoding: Int, channels: Int, out: FloatArrayBuilder) {
        val ch = channels.coerceAtLeast(1)
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buffer.asFloatBuffer()
                while (floats.remaining() >= ch) {
                    var sum = 0f
                    repeat(ch) { sum += floats.get() }
                    out.add(sum / ch)
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                while (buffer.remaining() >= ch) {
                    var sum = 0f
                    repeat(ch) { sum += ((buffer.get().toInt() and 0xFF) - 128) / 128f }
                    out.add(sum / ch)
                }
            }
            else -> {
                val shorts = buffer.asShortBuffer()
                while (shorts.remaining() >= ch) {
                    var sum = 0f
                    repeat(ch) { sum += shorts.get() / 32768f }
                    out.add(sum / ch)
                }
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Audio file"

    private const val TIMEOUT_US = 10_000L
}
