package com.showcase.desertant.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavFile {

    /** Write 16-bit PCM WAV, interleaving [channels]. */
    suspend fun write(file: File, channels: List<FloatArray>, sampleRate: Int): File = withContext(Dispatchers.IO) {
        require(channels.isNotEmpty())
        val frames = channels.minOf { it.size }
        val channelCount = channels.size
        val dataBytes = frames * channelCount * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channelCount.toShort())
            .putInt(sampleRate).putInt(sampleRate * channelCount * 2)
            .putShort((channelCount * 2).toShort()).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataBytes)
        for (i in 0 until frames) {
            for (c in channels) {
                val v = (c[i].coerceIn(-1f, 1f) * 32767f).toInt()
                buffer.putShort(v.toShort())
            }
        }
        file.parentFile?.mkdirs()
        file.writeBytes(buffer.array())
        file
    }

    /** Export into the shareable cache folder and open the system share sheet. */
    suspend fun share(context: Context, name: String, channels: List<FloatArray>, sampleRate: Int) {
        val file = write(File(context.cacheDir, "exports/$name.wav"), channels, sampleRate)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("audio/wav")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
