package com.showcase.desertant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.showcase.desertant.ui.theme.Appearance
import com.showcase.desertant.ui.theme.TypeStyle
import com.showcase.desertant.ui.theme.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The handwritten / formal choice: the default, what each upgrade path keeps, and that a pick sticks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppearanceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    @Before fun clear() { prefs.edit().clear().commit() }

    private fun stored(voice: String?, typeVersion: Int?, note: Boolean? = null) {
        prefs.edit().clear().apply {
            if (voice != null) putString("voice", voice)
            if (typeVersion != null) putInt("typeVersion", typeVersion)
            if (note != null) putBoolean("typeNote", note)
        }.commit()
    }

    @Test fun freshInstallStartsHandwrittenInTheNeatHand() {
        val a = Appearance(context)
        assertEquals(Voice.NEAT, a.voice.value)
        assertEquals(TypeStyle.HANDWRITTEN, a.voice.value.style)
        assertFalse("nothing is stored until the user picks", prefs.contains("voice"))
    }

    @Test fun theVoicesBelongToTheirStyles() {
        assertEquals(listOf(Voice.NEAT, Voice.LOOSE, Voice.HANDWRITTEN), TypeStyle.HANDWRITTEN.voices)
        assertEquals(listOf(Voice.FRIENDLY, Voice.FORMAL), TypeStyle.FORMAL.voices)
        assertTrue(TypeStyle.entries.all { it.defaultVoice in it.voices })
        assertEquals(Voice.NEAT, Voice.DEFAULT)
    }

    @Test fun installMovedToThePlainFontBy40GoesBackToHandwrittenOnce() {
        stored("FRIENDLY", 2, note = true)
        assertEquals(Voice.NEAT, Appearance(context).voice.value)
        assertFalse("the 4.0 note about the plain font is gone", prefs.contains("typeNote"))

        // After that, choosing the plain font is a choice, and it survives every later start.
        Appearance(context).setVoice(Voice.FRIENDLY)
        assertEquals(Voice.FRIENDLY, Appearance(context).voice.value)
        assertEquals(Voice.FRIENDLY, Appearance(context).voice.value)
    }

    @Test fun aVoicePickedByHandIn40IsKept() {
        stored("FORMAL", 2)
        assertEquals(Voice.FORMAL, Appearance(context).voice.value)
        stored("NEAT", 2)
        assertEquals(Voice.NEAT, Appearance(context).voice.value)
    }

    @Test fun installsStraightFrom39KeepTheirVoice() {
        // Up to 3.9 there was no typeVersion, and all five voices could be stored.
        for (name in listOf("FORMAL", "FRIENDLY", "NEAT", "LOOSE", "HANDWRITTEN")) {
            stored(name, null)
            assertEquals(name, Appearance(context).voice.value.name)
            assertEquals(name, Appearance(context).voice.value.name)
        }
    }

    @Test fun anUnknownStoredVoiceFallsBackToTheDefault() {
        stored("COMIC", null)
        assertEquals(Voice.DEFAULT, Appearance(context).voice.value)
    }

    @Test fun upgradeRule() {
        assertNull(Appearance.voiceAfterUpgrade(1, null))
        assertNull(Appearance.voiceAfterUpgrade(2, null))
        assertNull(Appearance.voiceAfterUpgrade(2, "FRIENDLY"))
        assertEquals("FRIENDLY", Appearance.voiceAfterUpgrade(1, "FRIENDLY"))
        assertEquals("FORMAL", Appearance.voiceAfterUpgrade(2, "FORMAL"))
        assertEquals("LOOSE", Appearance.voiceAfterUpgrade(1, "LOOSE"))
        assertEquals("HANDWRITTEN", Appearance.voiceAfterUpgrade(1, "HANDWRITTEN"))
    }

    @Test fun switchingStyleGoesToItsDefaultVoiceThenBackToTheLastOneUsed() {
        val a = Appearance(context)
        a.setStyle(TypeStyle.FORMAL)
        assertEquals(Voice.FRIENDLY, a.voice.value)
        a.setVoice(Voice.FORMAL)
        a.setStyle(TypeStyle.HANDWRITTEN)
        assertEquals(Voice.NEAT, a.voice.value)
        a.setVoice(Voice.HANDWRITTEN)
        a.setStyle(TypeStyle.FORMAL)
        assertEquals(Voice.FORMAL, a.voice.value)
        a.setStyle(TypeStyle.HANDWRITTEN)
        assertEquals(Voice.HANDWRITTEN, a.voice.value)
        // Tapping the style already chosen changes nothing.
        a.setStyle(TypeStyle.HANDWRITTEN)
        assertEquals(Voice.HANDWRITTEN, a.voice.value)
        assertEquals(Voice.HANDWRITTEN, Appearance(context).voice.value)
    }
}
