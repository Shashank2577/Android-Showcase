package com.showcase.desertant

import ai.desertant.redact.Redaction
import ai.desertant.redact.RedactionItem
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.takahirom.roborazzi.captureRoboImage
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSource
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.Moment
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.TopicScore
import com.showcase.desertant.data.Utterance
import com.showcase.desertant.privacy.LedgerEntry
import com.showcase.desertant.privacy.Masked
import com.showcase.desertant.privacy.Region
import com.showcase.desertant.privacy.ShareReport
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.mask.MaskScreen
import com.showcase.desertant.ui.meetings.RecordScreen
import com.showcase.desertant.ui.onboarding.AskFirstScreen
import com.showcase.desertant.ui.onboarding.WelcomeScreen
import com.showcase.desertant.ui.photos.SweepScreen
import com.showcase.desertant.ui.photos.VaultScreen
import com.showcase.desertant.ui.privacy.PrivacyScreen
import com.showcase.desertant.ui.share.ShareCheckScreen
import com.showcase.desertant.ui.shell.BurrowApp
import com.showcase.desertant.ui.theme.BurrowTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders Burrow's real screens with sample data into build/shots/, so the
 * design can be checked without a phone. Run: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotsTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ScreenshotsTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: ShowcaseApp get() = ApplicationProvider.getApplicationContext()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        BurrowTheme { CompositionLocalProvider(LocalModelHub provides app.hub) { content() } }
    }

    @Before
    fun seed() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val db = app.db
        if (db.profiles().allNow().isNotEmpty()) return@runBlocking
        val me = db.profiles().insert(ProfileEntity(name = "Sam Carter", colorIndex = 0, isMe = true, signatureCount = 3))
        val priya = db.profiles().insert(ProfileEntity(name = "Priya Shah", colorIndex = 1, signatureCount = 4))
        val marco = db.profiles().insert(ProfileEntity(name = "Marco Rossi", colorIndex = 2, signatureCount = 2))
        val now = System.currentTimeMillis()
        val topics = listOf(TopicScore("roadmap", "Roadmap", 0.8), TopicScore("budget", "Budget", 0.6), TopicScore("hiring", "Hiring", 0.4))
        val lines = listOf(
            0 to "Let's start with where the roadmap stands for Q3.",
            1 to "Offline mode ships in November, at the same price as today.",
            0 to "And the no-cloud promise holds for every plan?",
            1 to "Yes. No cloud tier; everything keeps working without signal.",
            2 to "The launch budget is agreed. The venue deposit is still open.",
            0 to "Marco, can you send the budget by Friday?",
            2 to "I'll send it Thursday, so we have a day to look.",
            3 to "I can take the hiring plan if that helps.",
            1 to "Then we book the launch venue next week.",
        )
        // Spread across the 42-minute talk, as a real one would be.
        val utterances = lines.mapIndexed { i, (who, text) -> Utterance(who, i * 280.0 + 20, i * 280.0 + 20 + 60 + (i % 3) * 70, text) }
        val q3 = db.meetings().insert(
            MeetingEntity(
                title = "Q3 roadmap sync", createdAt = now - 2 * 3_600_000L, durationSec = 2525.0, audioPath = "/nonexistent.m4a",
                status = MeetingStatus.READY, language = "en", languageSupported = true,
                transcriptText = utterances.joinToString(" ") { it.text },
                utterancesJson = StoreJson.encodeToString(ListSerializer(Utterance.serializer()), utterances),
                topicsJson = StoreJson.encodeToString(ListSerializer(TopicScore.serializer()), topics),
                highlightsJson = StoreJson.encodeToString(ListSerializer(Moment.serializer()), listOf(Moment(1, 9.5, "Offline mode ships in November, at the same price."), Moment(0, 32.0, "No cloud tier — everything keeps working without signal."))),
                followUpsJson = StoreJson.encodeToString(ListSerializer(Moment.serializer()), listOf(Moment(1, 24.5, "Marco will send the budget by Thursday."), Moment(3, 30.5, "Take the hiring plan."))),
                speakerCount = 4,
            ),
        )
        val mum = db.meetings().insert(
            MeetingEntity(
                title = "call with Mum", createdAt = now - 26 * 3_600_000L, durationSec = 1090.0, audioPath = "/nonexistent2.m4a",
                source = MeetingSource.CALL, status = MeetingStatus.READY, speakerCount = 2,
                topicsJson = StoreJson.encodeToString(ListSerializer(TopicScore.serializer()), listOf(TopicScore("family", "Family", 0.7), TopicScore("travel", "Travel", 0.5))),
            ),
        )
        db.meetings().insert(
            MeetingEntity(title = "design crit", createdAt = now - 20 * 60_000L, durationSec = 1860.0, audioPath = "/nonexistent3.m4a", status = MeetingStatus.PROCESSING, progress = "Transcribing part 2 of 3"),
        )
        db.speakers().insert(
            listOf(
                MeetingSpeakerEntity(meetingId = q3, index = 0, profileId = me, talkSec = 600.0, words = 900),
                MeetingSpeakerEntity(meetingId = q3, index = 1, profileId = priya, talkSec = 1000.0, words = 1500, topicsJson = StoreJson.encodeToString(ListSerializer(TopicScore.serializer()), topics.take(2))),
                MeetingSpeakerEntity(meetingId = q3, index = 2, profileId = marco, talkSec = 700.0, words = 1000, topicsJson = StoreJson.encodeToString(ListSerializer(TopicScore.serializer()), topics.drop(1))),
                MeetingSpeakerEntity(meetingId = q3, index = 3, talkSec = 220.0, words = 310, sampleQuote = "I can take the hiring plan if that helps."),
                MeetingSpeakerEntity(meetingId = mum, index = 0, profileId = me, talkSec = 500.0, words = 700),
                MeetingSpeakerEntity(meetingId = mum, index = 1, talkSec = 590.0, words = 820, sampleQuote = "Let's check the train times tomorrow."),
            ),
        )
        app.ledger.add(LedgerEntry.PHOTOS_CHECKED, 312)
        app.ledger.add(LedgerEntry.TALKS_TRANSCRIBED, 1)
        app.ledger.add(LedgerEntry.TEXTS_MASKED, 2)
        app.ledger.add(LedgerEntry.VOICES_LEARNED, 1)
        app.privacy.setOnboarded(true)
    }

    @Test fun welcome() { show { WelcomeScreen {} }; shot("01-welcome") }

    @Test fun askFirst() { show { AskFirstScreen({}, {}) }; shot("02-ask-first") }

    @Test fun today() {
        show { BurrowApp(app.hub, null) {} }
        shot("03-today")
    }

    @Test fun talks() {
        show { BurrowApp(app.hub, null) {} }
        compose.onNodeWithText("talks").performClick()
        compose.waitForIdle()
        shot("04-talks")
    }

    @Test fun people() {
        show { BurrowApp(app.hub, null) {} }
        compose.onNodeWithText("people").performClick()
        compose.waitForIdle()
        shot("05-people")
    }

    @Test fun photos() {
        show { BurrowApp(app.hub, null) {} }
        compose.onNodeWithText("photos").performClick()
        compose.waitForIdle()
        shot("06-photos")
    }

    @Test fun notes() {
        show { BurrowApp(app.hub, "meeting/1") {} }
        compose.waitForIdle()
        shot("07-talk-notes")
    }

    @Test fun record() { show { RecordScreen({}, {}) }; shot("08-record") }

    @Test fun mask() {
        val text = "Hi, I'm Sam Carter, card 4111 1111 1111 1111, call me on +44 7700 900123 about my order at 12 Baker Street."
        fun item(label: String, original: String, placeholder: String) = text.indexOf(original).let { RedactionItem(label = label, original = original, placeholder = placeholder, confidence = 0.97, start = it, end = it + original.length) }
        val items = listOf(item("NAME", "Sam Carter", "[NAME_1]"), item("CARD", "4111 1111 1111 1111", "[CARD_1]"), item("PHONE", "+44 7700 900123", "[PHONE_1]"), item("ADDRESS", "12 Baker Street", "[ADDRESS_1]"))
        var masked = text
        items.sortedByDescending { it.start }.forEach { masked = masked.replaceRange(it.start, it.end, it.placeholder) }
        app.mask.preload(Masked(text, Redaction(masked, items), "en"))
        show { MaskScreen(onBack = {}, initial = text) }
        shot("09-mask")
    }

    @Test fun shareCheck() {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            c.drawColor(Color.rgb(120, 86, 60))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(226, 196, 160); listOf(180f, 400f, 620f).forEach { c.drawCircle(it, 230f, 70f, p) }
            p.color = Color.rgb(40, 70, 140); c.drawRoundRect(520f, 430f, 700f, 540f, 14f, 14f, p)
        }
        val report = ShareReport(
            bitmap,
            faces = listOf(Region(0.14f, 0.27f, 0.31f, 0.5f), Region(0.42f, 0.27f, 0.58f, 0.5f), Region(0.69f, 0.27f, 0.86f, 0.5f)),
            sensitive = "an ID or bank card", intimate = false, hasLocation = true, cameraData = "samsung SM-F966B · 2025:10:24 20:42",
            crop = Region(0.05f, 0.08f, 0.9f, 0.68f),
        )
        show { ShareCheckScreen(Uri.EMPTY, {}, { _, _ -> }, preloaded = report) }
        shot("10-share-check")
    }

    @Test fun privacy() { show { PrivacyScreen({}, {}, {}) }; shot("11-privacy") }

    @Test fun vault() { show { VaultScreen {} }; shot("12-vault-locked") }

    @Test fun sweepEmpty() { show { SweepScreen({}, {}) }; shot("13-sweep") }

    @Config(qualifiers = "w412dp-h915dp-night-xhdpi")
    @Test fun todayDark() {
        show { BurrowApp(app.hub, null) {} }
        shot("14-today-dark")
    }

    @Config(qualifiers = "w832dp-h748dp-xhdpi")
    @Test fun foldInner() {
        show { BurrowApp(app.hub, null) {} }
        compose.onNodeWithText("talks").performClick()
        compose.waitForIdle()
        shot("15-fold-inner-talks")
    }
}
