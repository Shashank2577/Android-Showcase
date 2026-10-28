package com.showcase.desertant.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Mono PCM audio in `-1..1` floats, the input every Desert Ant audio model takes. */
class AudioClip(
    val samples: FloatArray,
    val sampleRate: Int,
    val label: String,
    /** True when a long file was cut to the decoder's maximum length. */
    val truncated: Boolean = false,
) {
    val durationSec: Double get() = samples.size.toDouble() / sampleRate

    /** Peak envelope in [buckets] bins, for drawing a waveform. */
    fun envelope(buckets: Int): FloatArray {
        if (samples.isEmpty() || buckets <= 0) return FloatArray(0)
        val out = FloatArray(buckets)
        val per = max(1, samples.size / buckets)
        for (b in 0 until buckets) {
            val from = b * per
            if (from >= samples.size) break
            val to = min(samples.size, from + per)
            var peak = 0f
            for (i in from until to) peak = max(peak, abs(samples[i]))
            out[b] = peak
        }
        return out
    }

    /** The samples between [startSec] and [endSec]. */
    fun slice(startSec: Double, endSec: Double): FloatArray {
        val from = (startSec * sampleRate).toInt().coerceIn(0, samples.size)
        val to = (endSec * sampleRate).toInt().coerceIn(from, samples.size)
        return samples.copyOfRange(from, to)
    }
}

/** A growable float buffer, so decoding never holds two full copies plus boxing. */
internal class FloatArrayBuilder(initial: Int = 1 shl 16) {
    private var data = FloatArray(initial)
    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

fun formatSeconds(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0)
    val m = (total / 60).toInt()
    val s = total - m * 60
    return if (m > 0) "%d:%04.1f".format(m, s) else "%.1fs".format(s)
}
