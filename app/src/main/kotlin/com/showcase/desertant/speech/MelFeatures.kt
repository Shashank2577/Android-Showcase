package com.showcase.desertant.speech

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Log-mel features the way NeMo's AudioToMelSpectrogramPreprocessor makes them
 * for IndicConformer: 16 kHz, 25 ms Hann windows every 10 ms, 512-point FFT,
 * pre-emphasis 0.97, 80 Slaney mel bands, log(power + 2^-24), then each band
 * normalised to zero mean and unit variance over the clip.
 */
object MelFeatures {
    const val RATE = 16_000
    const val BANDS = 80
    const val HOP = 160
    private const val WIN = 400
    private const val NFFT = 512
    private const val PREEMPH = 0.97f
    private val LOG_GUARD = 2.0.pow(-24).toFloat()

    private val window = FloatArray(WIN) { i -> (0.5 - 0.5 * cos(2 * PI * i / WIN)).toFloat() }
    private val filters: Array<FloatArray> by lazy { melFilters() }

    /** Frames for [samples] (16 kHz mono): `[frame][band]`, normalised per band. */
    fun compute(samples: FloatArray): Array<FloatArray> {
        val x = FloatArray(samples.size)
        if (samples.isNotEmpty()) {
            x[0] = samples[0]
            for (i in 1 until samples.size) x[i] = samples[i] - PREEMPH * samples[i - 1]
        }
        // Centered frames (torch.stft center=True, zero padding).
        val frames = 1 + samples.size / HOP
        val out = Array(frames) { FloatArray(BANDS) }
        val re = FloatArray(NFFT)
        val im = FloatArray(NFFT)
        val power = FloatArray(NFFT / 2 + 1)
        val pad = NFFT / 2
        val offset = (NFFT - WIN) / 2
        for (f in 0 until frames) {
            re.fill(0f)
            im.fill(0f)
            val start = f * HOP - pad
            for (i in 0 until WIN) {
                val idx = start + offset + i
                if (idx in x.indices) re[offset + i] = x[idx] * window[i]
            }
            fft(re, im)
            for (k in power.indices) power[k] = re[k] * re[k] + im[k] * im[k]
            val row = out[f]
            for (b in 0 until BANDS) {
                val fb = filters[b]
                var s = 0f
                for (k in fb.indices) s += fb[k] * power[k]
                row[b] = ln(s + LOG_GUARD)
            }
        }
        normalize(out)
        return out
    }

    private fun normalize(m: Array<FloatArray>) {
        val n = m.size
        if (n == 0) return
        for (b in 0 until BANDS) {
            var mean = 0.0
            for (f in 0 until n) mean += m[f][b]
            mean /= n
            var v = 0.0
            for (f in 0 until n) { val d = m[f][b] - mean; v += d * d }
            val std = sqrt(v / max(1, n - 1)) + 1e-5
            for (f in 0 until n) m[f][b] = ((m[f][b] - mean) / std).toFloat()
        }
    }

    /** In-place radix-2 FFT. */
    internal fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
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

    // Slaney mel scale, as librosa.filters.mel(htk=False, norm="slaney").
    private fun hzToMel(hz: Double): Double {
        val fSp = 200.0 / 3
        val minLogHz = 1000.0
        val minLogMel = minLogHz / fSp
        val logStep = ln(6.4) / 27.0
        return if (hz >= minLogHz) minLogMel + ln(hz / minLogHz) / logStep else hz / fSp
    }

    private fun melToHz(mel: Double): Double {
        val fSp = 200.0 / 3
        val minLogHz = 1000.0
        val minLogMel = minLogHz / fSp
        val logStep = ln(6.4) / 27.0
        return if (mel >= minLogMel) minLogHz * kotlin.math.exp(logStep * (mel - minLogMel)) else fSp * mel
    }

    private fun melFilters(): Array<FloatArray> {
        val bins = NFFT / 2 + 1
        val fftFreqs = DoubleArray(bins) { it * RATE.toDouble() / NFFT }
        val lo = hzToMel(0.0)
        val hi = hzToMel(RATE / 2.0)
        val melPts = DoubleArray(BANDS + 2) { melToHz(lo + (hi - lo) * it / (BANDS + 1)) }
        return Array(BANDS) { b ->
            val l = melPts[b]; val c = melPts[b + 1]; val r = melPts[b + 2]
            val enorm = 2.0 / (r - l)
            FloatArray(bins) { k ->
                val f = fftFreqs[k]
                val up = (f - l) / (c - l)
                val down = (r - f) / (r - c)
                (max(0.0, min(up, down)) * enorm).toFloat()
            }
        }
    }
}
