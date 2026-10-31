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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.takahirom.roborazzi.captureRoboImage
import com.showcase.desertant.data.ChatEntity
import com.showcase.desertant.data.ChatMessageEntity
import com.showcase.desertant.data.ChatPersonEntity
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.showcase.desertant.ask.AskModel
import com.showcase.desertant.ask.AskSource
import com.showcase.desertant.ask.AskStep
import com.showcase.desertant.ask.AskUi
import com.showcase.desertant.ask.Confirmation
import com.showcase.desertant.ui.ask.AskContent
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
open class ScreenshotsTest {
    /** Subclasses render the same screens under another configuration, into files with this prefix. */
    protected open val prefix = ""

    @get:Rule
    val compose = createComposeRule()

    private val app: ShowcaseApp get() = ApplicationProvider.getApplicationContext()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/shots/$prefix$name.png")

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        BurrowTheme { CompositionLocalProvider(LocalModelHub provides app.hub) { content() } }
    }

    @Before
    fun seed() = seedSampleData(app)

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

    @Test fun person() {
        show { com.showcase.desertant.ui.people.PersonScreen(2, {}, {}) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Priya Shah", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("16-person")
    }

    @Test fun models() { show { com.showcase.desertant.ui.settings.SettingsScreen({}, {}) }; shot("17-models") }

    @Test fun recordings() { show { com.showcase.desertant.ui.meetings.DeviceRecordingsScreen {} }; shot("18-call-recordings") }

    @Test fun hindiTalk() {
        show { BurrowApp(app.hub, "meeting/3") {} }
        compose.waitForIdle()
        shot("19-hindi-talk")
    }

    @Test fun icon() {
        show {
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFFDDDDDD)).padding(24.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
            ) {
                listOf(androidx.compose.foundation.shape.CircleShape, androidx.compose.foundation.shape.RoundedCornerShape(30)).forEach { shape ->
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.size(150.dp).clip(shape).background(androidx.compose.ui.graphics.Color(0xFFFFE45C)),
                    ) {
                        androidx.compose.foundation.Image(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null,
                            androidx.compose.ui.Modifier.matchParentSize().scale(1.5f),
                        )
                    }
                }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredSize(150.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(0xFF2B2F3A))) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null,
                        androidx.compose.ui.Modifier.matchParentSize().scale(1.5f),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color(0xFFC9D4F2)),
                    )
                }
            }
        }
        shot("20-icon")
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

    private fun ask(ui: AskUi, model: AskModel.State = AskModel.State.Ready, ram: Int = 12) = show {
        AskContent(ui, model, ram, neverOnline = false, suggestions = listOf("what did Priya promise?", "what was “Q3 roadmap sync” about?", "photos of receipts"), {}, {}, {}, {}, {}, {})
    }

    @Test fun chat() {
        show { com.showcase.desertant.ui.chats.ChatScreen(1, {}, {}) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Running 10 min", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("25-chat")
    }

    @Test fun chatImport() {
        show {
            com.showcase.desertant.ui.chats.ChatImportContent(
                "Marco Rossi", 1_284, "3 Mar 2025 – 30 Oct 2026",
                listOf("Marco" to 702, "Sam" to 582),
                mapOf("Marco" to com.showcase.desertant.chats.ChatImporter.Who.Person(3), "Sam" to com.showcase.desertant.chats.ChatImporter.Who.Me),
                listOf(ProfileEntity(3, "Marco Rossi", 2)), saving = false, {}, {}, {},
            )
        }
        shot("26-chat-import")
    }

    @Test fun audioImport() {
        show {
            com.showcase.desertant.ui.meetings.AudioImportContent(
                "Priya Shah(+447700900123)_20241030181205.mp3",
                listOf(ProfileEntity(1, "Sam Carter", 0, isMe = true), ProfileEntity(2, "Priya Shah", 1, phones = "447700900123"), ProfileEntity(3, "Marco Rossi", 2)),
                busy = false, {}, { _, _, _ -> },
            )
        }
        shot("27-add-recording")
    }

    @Test fun askSetup() { ask(AskUi(), AskModel.State.Downloading(37), ram = 6); shot("21-ask-setup") }

    @Test fun askIdle() { ask(AskUi()); shot("22-ask") }

    @Test fun askConfirm() {
        ask(
            AskUi(
                question = "rename the roadmap talk to Q3 planning", phase = AskUi.Phase.THINKING,
                steps = listOf(AskStep("search_talks", "search talks", "query: \"roadmap\"", AskStep.Status.DONE, "Q3 roadmap sync")),
                pending = Confirmation("rename_talk", "rename “Q3 roadmap sync” to “Q3 planning”"),
            ),
        )
        shot("23-ask-confirm")
    }

    @Test fun askAnswer() {
        ask(SAMPLE_ANSWER)
        shot("24-ask-answer")
    }

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

/** Every screen again in dark mode, to check contrast. */
@Config(sdk = [34], qualifiers = "w412dp-h915dp-night-xhdpi")
class DarkScreenshotsTest : ScreenshotsTest() {
    override val prefix = "dark-"
}

/**
 * A real answer: what Gemma 4 E2B did for this question over the sample data in
 * AskLiveTest (build/ask-live.md), replayed here because the model can't run in
 * a screenshot test.
 */
val SAMPLE_ANSWER = AskUi(
    question = "what did Priya promise?",
    phase = AskUi.Phase.DONE,
    steps = listOf(
        AskStep("find_person", "find a person", "name: \"Priya\"", AskStep.Status.DONE, "Priya Shah · 1 talk"),
        AskStep("search_talks", "search talks", "query: \"promise\"", AskStep.Status.DONE, "Q3 roadmap sync"),
        AskStep("follow_ups", "find follow-ups", "person: \"Priya Shah\"", AskStep.Status.DONE, "1 follow-up"),
    ),
    answer = "Priya promised to book the launch venue next week, according to the \"Q3 roadmap sync\" talk.",
    sources = listOf(AskSource.Talk(1, "Q3 roadmap sync", 2260.0, "Then we book the launch venue next week."), AskSource.Person(2, "Priya Shah")),
)

/** Sample people, talks and ledger entries shared by the screenshot tests. */
fun seedSampleData(app: ShowcaseApp) = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val db = app.db
        if (db.profiles().allNow().isNotEmpty()) return@runBlocking
        val me = db.profiles().insert(ProfileEntity(name = "Sam Carter", colorIndex = 0, isMe = true, signatureCount = 3))
        val priya = db.profiles().insert(ProfileEntity(name = "Priya Shah", colorIndex = 1, signatureCount = 4, phones = "447700900123", contactUri = "content://com.android.contacts/contacts/lookup/sample/7", aliases = "Priya 🌸"))
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
                followUpsJson = StoreJson.encodeToString(ListSerializer(Moment.serializer()), listOf(Moment(2, 1700.0, "I'll send it Thursday, so we have a day to look."), Moment(1, 2260.0, "Then we book the launch venue next week."), Moment(3, 1980.0, "I can take the hiring plan if that helps."))),
                speakerCount = 4,
            ),
        )
        val mum = db.meetings().insert(
            MeetingEntity(
                title = "call with Mum", createdAt = now - 26 * 3_600_000L, durationSec = 1090.0, audioPath = "/nonexistent2.m4a",
                source = MeetingSource.CALL, status = MeetingStatus.READY, speakerCount = 2, callWith = "Mum",
                topicsJson = StoreJson.encodeToString(ListSerializer(TopicScore.serializer()), listOf(TopicScore("family", "Family", 0.7), TopicScore("travel", "Travel", 0.5))),
            ),
        )
        db.meetings().insert(
            MeetingEntity(
                title = "Hindi talk · Tue 28 Oct, 18:05", createdAt = now - 30 * 3_600_000L, durationSec = 840.0, audioPath = "/nonexistent4.m4a",
                status = MeetingStatus.FAILED, language = "hi", languageSupported = false,
                error = "This talk is in Hindi. Download Hindi (about 140 MB, once) and Burrow can write its notes.",
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
        // A call recording named by number (as phone recorders do), and a WhatsApp chat Priya's in.
        db.meetings().insert(
            MeetingEntity(
                title = "call with Priya · launch", createdAt = now - 3 * 86_400_000L, durationSec = 612.0, audioPath = "/nonexistent5.m4a",
                source = MeetingSource.CALL, status = MeetingStatus.READY, speakerCount = 2, callWith = "+44 7700 900123", callProfileId = priya,
            ),
        )
        val chat = db.chats().insert(ChatEntity(title = "Priya Shah", source = "whatsapp", importedAt = now, firstAt = now - 5 * 86_400_000L, lastAt = now - 3_600_000L, messageCount = 6))
        val texts = listOf(
            "Priya 🌸" to "Venue shortlist is in the doc, have a look?",
            "Sam" to "Looks good. The second one is closer to the station",
            "Priya 🌸" to "Agreed. I'll book it next week, deposit is 500",
            "Sam" to "Perfect, budget from Marco on Thursday",
            "Priya 🌸" to "(a photo, video or file — not included)",
            "Priya 🌸" to "Running 10 min late, start without me",
        )
        db.chats().insertMessages(texts.mapIndexed { i, (who, t) -> ChatMessageEntity(chatId = chat, at = now - (5 - i) * 86_400_000L + i * 60_000L, sender = who, text = t) })
        db.chats().upsertPeople(
            listOf(
                ChatPersonEntity(chat, "Priya 🌸", priya, messages = 4),
                ChatPersonEntity(chat, "Sam", me, isMe = true, messages = 2),
            ),
        )
        app.ledger.add(LedgerEntry.PHOTOS_CHECKED, 312)
        app.ledger.add(LedgerEntry.TALKS_TRANSCRIBED, 1)
        app.ledger.add(LedgerEntry.TEXTS_MASKED, 2)
        app.ledger.add(LedgerEntry.VOICES_LEARNED, 1)
        app.privacy.setOnboarded(true)
    }
