package com.showcase.desertant

import com.showcase.desertant.people.VoiceSignatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class VoiceSignaturesTest {
    /**
     * A crude vowel-like voice: a glottal pulse train at [pitch] Hz through
     * resonances at [formants], with syllable-rate loudness changes and noise.
     */
    private fun voice(pitch: Double, formants: List<Double>, seconds: Double, seed: Int): FloatArray {
        val rate = 16_000
        val rnd = Random(seed)
        val n = (seconds * rate).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            val jitter = 1 + 0.03 * sin(2 * PI * 0.7 * t)
            var v = 0.0
            for (h in 1..30) {
                val f = pitch * jitter * h
                if (f > 7000) break
                val gain = formants.sumOf { fm -> 1.0 / (1 + ((f - fm) / 120).let { it * it }) }
                v += gain * sin(2 * PI * f * t) / h
            }
            val syllable = 0.6 + 0.4 * sin(2 * PI * 3.5 * t)
            ((v * syllable * 0.1) + rnd.nextDouble(-0.002, 0.002)).toFloat()
        }
    }

    private val alice1 = voice(210.0, listOf(800.0, 1800.0, 2900.0), 6.0, 1)
    private val alice2 = voice(205.0, listOf(820.0, 1750.0, 2950.0), 6.0, 2)
    private val bob = voice(110.0, listOf(500.0, 1100.0, 2400.0), 6.0, 3)

    @Test fun silenceHasNoSignature() {
        assertNull(VoiceSignatures.compute(FloatArray(16_000 * 5)))
    }

    @Test fun shortClipHasNoSignature() {
        assertNull(VoiceSignatures.compute(alice1.copyOf(8_000)))
    }

    @Test fun pitchIsMeasured() {
        val a = VoiceSignatures.compute(alice1)!!
        val b = VoiceSignatures.compute(bob)!!
        assertEquals(210.0, a.pitchHz.toDouble(), 25.0)
        assertEquals(110.0, b.pitchHz.toDouble(), 15.0)
    }

    @Test fun sameVoiceScoresHigherThanDifferentVoice() {
        val a1 = VoiceSignatures.compute(alice1)!!
        val a2 = VoiceSignatures.compute(alice2)!!
        val b = VoiceSignatures.compute(bob)!!
        val center = VoiceSignatures.center(listOf(a1, a2, b))
        val same = VoiceSignatures.similarity(a1, a2, center)
        val different = VoiceSignatures.similarity(a1, b, center)
        assertTrue("same=$same different=$different", same > different + 0.2)
        assertTrue("same voice should clear the suggestion threshold: $same", same >= 0.72)
        assertTrue("different voice should stay under it: $different", different < 0.72)
    }

    @Test fun mergeAveragesTowardNewSamples() {
        val a = VoiceSignatures.compute(alice1)!!
        val b = VoiceSignatures.compute(bob)!!
        val merged = VoiceSignatures.merge(a, 1, b)
        assertNotNull(merged)
        assertEquals((a.pitchHz + b.pitchHz) / 2, merged.pitchHz, 0.5f)
        assertEquals(a.values.size, merged.values.size)
    }
}
