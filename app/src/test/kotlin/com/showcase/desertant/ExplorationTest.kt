package com.showcase.desertant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.settings.AppSettingsScreen
import com.showcase.desertant.ui.shell.BurrowApp
import com.showcase.desertant.ui.theme.BurrowTheme
import com.showcase.desertant.ui.theme.ThemeMode
import com.showcase.desertant.ui.theme.Voice
import com.showcase.desertant.ui.theme.bodyStyle
import com.showcase.desertant.ui.theme.burrow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design explorations, rendered from the real Compose code into build/explore/:
 * every "handwriting" voice on the same two screens, the settings page, and a
 * concept for asking Burrow questions with on-device tool calling.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ExplorationTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: ShowcaseApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun seed() = seedSampleData(app)

    private fun render(name: String, voice: Voice, mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
        compose.setContent {
            BurrowTheme(mode, voice) { CompositionLocalProvider(LocalModelHub provides app.hub) { content() } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/explore/$name.png")
    }

    private fun today(v: Voice) = render("voice-${v.ordinal}-${v.name.lowercase()}-today", v) { BurrowApp(app.hub, null) {} }
    private fun notes(v: Voice) = render("voice-${v.ordinal}-${v.name.lowercase()}-notes", v) { BurrowApp(app.hub, "meeting/1") {} }

    @Test fun formalToday() = today(Voice.FORMAL)
    @Test fun friendlyToday() = today(Voice.FRIENDLY)
    @Test fun neatToday() = today(Voice.NEAT)
    @Test fun looseToday() = today(Voice.LOOSE)
    @Test fun handwrittenToday() = today(Voice.HANDWRITTEN)
    @Test fun formalNotes() = notes(Voice.FORMAL)
    @Test fun friendlyNotes() = notes(Voice.FRIENDLY)
    @Test fun neatNotes() = notes(Voice.NEAT)
    @Test fun looseNotes() = notes(Voice.LOOSE)
    @Test fun handwrittenNotes() = notes(Voice.HANDWRITTEN)

    @Test fun settingsLight() = render("settings-light", Voice.DEFAULT) { AppSettingsScreen({}, {}) }
    @Test fun settingsDark() = render("settings-dark", Voice.DEFAULT, ThemeMode.DARK) { AppSettingsScreen({}, {}) }
    @Test fun askLight() = render("ask-concept-light", Voice.DEFAULT) { AskConcept() }
    @Test fun askDark() = render("ask-concept-dark", Voice.DEFAULT, ThemeMode.DARK) { AskConcept() }
}

/**
 * Concept only: "ask Burrow". A small on-device LLM reads the question and calls
 * Burrow's own functions as tools; every step is shown, and every answer cites
 * where it came from. Nothing here leaves the phone.
 */
@Composable
private fun AskConcept() {
    HandPage(onBack = {}) {
        PageIntro("ask Burrow", "Questions about your talks, people and photos, answered on this phone from what Burrow already knows.", doodle = Doodle.BUBBLE)
        HandField("what did Priya promise last week?", {}, singleLine = true, leading = Doodle.SEARCH)
        Spacer(Modifier.height(16.dp))
        SectionHead("what Burrow did", note = "all on the phone")
        ToolStep("find_person", "name: \"Priya\"", "Priya Shah · 3 talks")
        ToolStep("search_talks", "person: Priya · since: 7 days", "Q3 roadmap sync, design crit")
        ToolStep("follow_ups", "talks: 2 · said by: Priya", "2 promises found")
        Spacer(Modifier.height(16.dp))
        SectionHead("answer")
        Column(Modifier.fillMaxWidth().border(1.8.dp, burrow.ink, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Body("Priya said she'd ship offline mode in November at the same price, and that she'd book the launch venue next week.")
            Spacer(Modifier.height(8.dp))
            Meta("from: Q3 roadmap sync · 0:09 and 37:40 — tap to hear it")
        }
        Spacer(Modifier.height(16.dp))
        InkButton("add both to my to-dos", {}, Modifier.fillMaxWidth(), trailing = Doodle.TICK)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DoodleIcon(Doodle.CLOUD_OFF, null, size = 18.dp, color = burrow.pencil)
            Spacer(Modifier.width(8.dp))
            Meta("Answered by a small model on this phone. Actions that change anything ask you first.")
        }
    }
}

@Composable
private fun ToolStep(tool: String, args: String, result: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        DoodleIcon(Doodle.TICK, null, Modifier.padding(top = 2.dp), size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "$tool($args)",
                Modifier.background(burrow.smudge, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = burrow.ink,
            )
            Text("→ $result", style = bodyStyle(14f, 19f), color = burrow.pencil)
        }
    }
}
