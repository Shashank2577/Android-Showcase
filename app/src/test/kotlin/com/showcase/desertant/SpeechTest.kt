package com.showcase.desertant

import com.showcase.desertant.speech.CtcDecoder
import com.showcase.desertant.speech.MelFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SpeechTest {
    @Test fun fftMatchesADirectDft() {
        val n = 512
        val x = FloatArray(n) { (sin(it * 0.3) + 0.5 * cos(it * 1.7)).toFloat() }
        val re = x.copyOf()
        val im = FloatArray(n)
        MelFeatures.fft(re, im)
        for (k in listOf(0, 1, 7, 100, 255)) {
            var r = 0.0
            var i = 0.0
            for (t in 0 until n) { r += x[t] * cos(2 * PI * k * t / n); i -= x[t] * sin(2 * PI * k * t / n) }
            assertEquals(r, re[k].toDouble(), 1e-2)
            assertEquals(i, im[k].toDouble(), 1e-2)
        }
    }

    @Test fun featuresHaveOneFramePerHopAndAreNormalised() {
        val sec = 2
        val tone = FloatArray(MelFeatures.RATE * sec) { (0.3 * sin(2 * PI * 440 * it / MelFeatures.RATE)).toFloat() }
        val m = MelFeatures.compute(tone)
        assertEquals(1 + tone.size / MelFeatures.HOP, m.size)
        assertEquals(MelFeatures.BANDS, m[0].size)
        for (b in 0 until MelFeatures.BANDS) assertTrue(abs(m.sumOf { it[b].toDouble() } / m.size) < 1e-3)
        // A 440 Hz tone puts its energy in the low bands, not the top ones.
        val mid = m[m.size / 2]
        assertTrue(mid.take(20).max() > mid.takeLast(20).max())
    }

    @Test fun ctcDropsBlanksAndRepeatsAndTimesWords() {
        val tokens = listOf("▁नम", "स्ते", "▁दुनिया", "<blk>")
        val d = CtcDecoder(tokens, blank = 3)
        fun f(i: Int) = FloatArray(4).also { it[i] = 1f }
        val frames = arrayOf(f(3), f(0), f(0), f(1), f(3), f(3), f(2), f(2), f(3))
        val words = d.decode(frames, frameSec = 0.08)
        assertEquals(listOf("नमस्ते", "दुनिया"), words.map { it.text })
        assertEquals(0.08, words[0].start, 1e-9)
        assertEquals(0.32, words[0].end, 1e-9)
        assertEquals(0.48, words[1].start, 1e-9)
    }

    @Test fun readsSherpaTokensFiles() {
        val f = File.createTempFile("tokens", ".txt").apply { writeText("<unk> 0\n▁क 1\nा 2\n<blk> 3\n") }
        val d = CtcDecoder.fromTokensFile(f)
        fun one(i: Int) = FloatArray(4).also { it[i] = 1f }
        assertEquals("का", d.decode(arrayOf(one(1), one(2), one(3)), 0.08).single().text)
    }
}
