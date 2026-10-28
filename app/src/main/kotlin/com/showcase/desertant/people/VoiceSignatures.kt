package com.showcase.desertant.people

import com.showcase.desertant.data.VoiceSignature
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A compact, on-device voice signature: the mean and spread of a speaker's
 * MFCCs over their voiced frames, plus their median pitch.
 *
 * Desert Ant's Who model separates speakers within a recording but doesn't
 * expose its voice embeddings, so linking a voice across meetings uses this
 * classic acoustic fingerprint instead. It is good at telling a handful of
 * regular contacts apart on the same phone; matches are offered as
 * suggestions for the user to confirm, never applied silently.
 */
object VoiceSignatures {
    private const val RATE = 16_000
    private const val FRAME = 400 // 25 ms
    private const val HOP = 160 // 10 ms
    private const val FFT = 512
    private const val MELS = 26
    private const val CEPS = 20 // c0..c19; c0 (loudness) is dropped
    private const val LIFTER = 22.0

    private val window = DoubleArray(FRAME) { 0.54 - 0.46 * cos(2 * PI * it / (FRAME - 1)) }
    private val melBank: Array<DoubleArray> = buildMelBank()
    private val dct: Array<DoubleArray> = Array(CEPS) { k -> DoubleArray(MELS) { n -> cos(PI * k * (n + 0.5) / MELS) } }
    private val lifter = DoubleArray(CEPS) { 1 + LIFTER / 2 * sin(PI * it / LIFTER) }

    /** Signature of 16 kHz mono [samples], or null when there is too little voiced audio. */
    fun compute(samples: FloatArray): VoiceSignature? {
        val frames = (samples.size - FRAME) / HOP
        if (frames < 100) return null // under ~1 s
        val energies = DoubleArray(frames)
        val ceps = Array(frames) { DoubleArray(CEPS - 1) }
        val re = DoubleArray(FFT)
        val im = DoubleArray(FFT)
        val power = DoubleArray(FFT / 2 + 1)
        for (f in 0 until frames) {
            val offset = f * HOP
            var energy = 0.0
            var prev = if (offset > 0) samples[offset - 1].toDouble() else 0.0
            for (i in 0 until FFT) {
                if (i < FRAME) {
                    val x = samples[offset + i].toDouble()
                    val y = (x - 0.97 * prev) * window[i]
                    prev = x
                    re[i] = y
                    energy += x * x
                } else re[i] = 0.0
                im[i] = 0.0
            }
            energies[f] = energy / FRAME
            fft(re, im)
            for (k in power.indices) power[k] = (re[k] * re[k] + im[k] * im[k]) / FFT
            val logMel = DoubleArray(MELS) { m ->
                var sum = 0.0
                val w = melBank[m]
                for (k in w.indices) sum += w[k] * power[k]
                ln(max(sum, 1e-10))
            }
            for (c in 1 until CEPS) {
                var sum = 0.0
                for (n in 0 until MELS) sum += dct[c][n] * logMel[n]
                ceps[f][c - 1] = sum * lifter[c]
            }
        }
        // Keep the louder 60% of frames: speech, not pauses or room noise.
        val cutoff = energies.sorted()[(frames * 0.4).toInt()]
        val voiced = (0 until frames).filter { energies[it] > cutoff && energies[it] > 1e-7 }
        if (voiced.size < 60) return null

        val dims = CEPS - 1
        val mean = DoubleArray(dims)
        voiced.forEach { f -> for (d in 0 until dims) mean[d] += ceps[f][d] }
        for (d in 0 until dims) mean[d] /= voiced.size
        val std = DoubleArray(dims)
        voiced.forEach { f -> for (d in 0 until dims) std[d] += (ceps[f][d] - mean[d]).pow(2) }
        for (d in 0 until dims) std[d] = sqrt(std[d] / voiced.size)

        val pitches = voiced.filterIndexed { i, _ -> i % 3 == 0 }.mapNotNull { pitch(samples, it * HOP) }
        val pitch = if (pitches.isEmpty()) 0f else pitches.sorted()[pitches.size / 2].toFloat()
        val values = (mean.toList() + std.toList()).map { it.toFloat() }
        return VoiceSignature(values, pitch, samples.size.toDouble() / RATE)
    }

    /**
     * How alike two signatures are, 0..1. [center] is the average signature of
     * everyone being compared; subtracting it removes what all voices on this
     * phone's microphone share, leaving what tells them apart.
     */
    fun similarity(a: VoiceSignature, b: VoiceSignature, center: List<Float>?): Double {
        val n = min(a.values.size, b.values.size)
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until n) {
            val c = center?.getOrNull(i)?.toDouble() ?: 0.0
            val x = a.values[i] - c
            val y = b.values[i] - c
            dot += x * y; na += x * x; nb += y * y
        }
        val timbre = if (na == 0.0 || nb == 0.0) 0.0 else (dot / sqrt(na * nb) + 1) / 2
        val pitch = if (a.pitchHz > 0 && b.pitchHz > 0) exp(-abs(ln(a.pitchHz.toDouble() / b.pitchHz)) / 0.12) else 0.5
        return 0.7 * timbre + 0.3 * pitch
    }

    fun center(signatures: List<VoiceSignature>): List<Float>? {
        if (signatures.size < 3) return null
        val n = signatures.minOf { it.values.size }
        return List(n) { i -> signatures.sumOf { it.values[i].toDouble() }.div(signatures.size).toFloat() }
    }

    /** Fold [new] into a running average weighted by how many signatures [old] already holds. */
    fun merge(old: VoiceSignature?, count: Int, new: VoiceSignature): VoiceSignature {
        if (old == null || count <= 0) return new
        val w = count.coerceAtMost(20).toFloat()
        val values = old.values.zip(new.values) { o, x -> (o * w + x) / (w + 1) }
        val pitch = when {
            old.pitchHz <= 0f -> new.pitchHz
            new.pitchHz <= 0f -> old.pitchHz
            else -> (old.pitchHz * w + new.pitchHz) / (w + 1)
        }
        return VoiceSignature(values, pitch, old.seconds + new.seconds)
    }

    /** Autocorrelation pitch of one 40 ms window, 70–400 Hz, or null if unvoiced. */
    private fun pitch(samples: FloatArray, offset: Int): Double? {
        val size = 640
        if (offset + size > samples.size) return null
        val minLag = RATE / 400
        val maxLag = RATE / 70
        var energy = 0.0
        for (i in 0 until size) energy += samples[offset + i] * samples[offset + i]
        if (energy <= 1e-6) return null
        var bestLag = -1
        var best = 0.0
        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (i in 0 until size - lag) sum += samples[offset + i] * samples[offset + i + lag]
            if (sum > best) { best = sum; bestLag = lag }
        }
        return if (bestLag > 0 && best / energy > 0.35) RATE.toDouble() / bestLag else null
    }

    private fun buildMelBank(): Array<DoubleArray> {
        fun mel(f: Double) = 2595 * log10(1 + f / 700)
        fun hz(m: Double) = 700 * (10.0.pow(m / 2595) - 1)
        val lo = mel(60.0)
        val hi = mel(7600.0)
        val points = DoubleArray(MELS + 2) { hz(lo + (hi - lo) * it / (MELS + 1)) }
        val bins = points.map { ((FFT + 1) * it / RATE).toInt() }
        return Array(MELS) { m ->
            DoubleArray(FFT / 2 + 1) { k ->
                val l = bins[m]; val c = bins[m + 1]; val r = bins[m + 2]
                when {
                    k in l until c && c > l -> (k - l).toDouble() / (c - l)
                    k in c..r && r > c -> (r - k).toDouble() / (r - c)
                    else -> 0.0
                }
            }
        }
    }

    /** In-place iterative radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wr = cos(angle); val wi = sin(angle)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
