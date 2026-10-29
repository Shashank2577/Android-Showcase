@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandNav
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.HandSwitch
import com.showcase.desertant.ui.hand.HandTab
import com.showcase.desertant.ui.hand.HandTag
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.Snapshot
import com.showcase.desertant.ui.theme.BurrowTheme
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The design system on one sheet, for design/burrow-a/theme. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w820dp-h1500dp-xhdpi")
class DesignSystemTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun light() {
        compose.setContent { BurrowTheme(dark = false) { Sheet() } }
        compose.onRoot().captureRoboImage("build/shots/00-design-system.png")
    }

    @Test fun dark() {
        compose.setContent { BurrowTheme(dark = true) { Sheet() } }
        compose.onRoot().captureRoboImage("build/shots/00-design-system-dark.png")
    }
}

@Composable
private fun Sheet() {
    val c = burrow
    Row(Modifier.fillMaxSize().background(c.paper).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MarkedText("burrow.", mark = "burrow", style = Hand.display)
            Meta("hand-drawn minimal · design system v1")
            SectionHead("paper, ink, one marker")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("paper" to c.paper, "ink" to c.ink, "pencil" to c.pencil, "graphite" to c.graphite, "smudge" to c.smudge, "marker" to c.marker, "red pen" to c.redPen)
                    .forEach { (n, col) -> Swatch(n, col) }
            }
            SectionHead("type")
            Text("display 52", style = Hand.display, color = c.ink)
            Text("headline 40", style = Hand.headline, color = c.ink)
            Text("title 30", style = Hand.title, color = c.ink)
            Text("door 26 — a line that leads somewhere", style = Hand.door, color = c.ink)
            Text("label 21 — buttons, links, nav", style = Hand.label, color = c.ink)
            Body("Body 15 · Inter. Quiet explanations sit under the hand-lettering, never louder than it.")
            Meta("Meta 13 · dates, sizes, counts")
            SectionHead("doodles")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Doodle.entries.forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                        DoodleIcon(d, null, size = 30.dp, color = if (d == Doodle.EXCLAIM) c.redPen else c.ink)
                        Meta(d.name.lowercase().replace('_', ' '), maxLines = 1)
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHead("actions")
            InkButton("the one ink pill", {}, Modifier.fillMaxWidth(), trailing = Doodle.ARROW)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                HandLink("a hand-underlined link", {})
                HandLink("destructive", {}, color = c.redPen)
            }
            InkButton("wipe it all", {}, Modifier.fillMaxWidth(), danger = true)
            SectionHead("switches & tags")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                HandSwitch(true, {})
                HandSwitch(false, {})
                HandTag("roadmap", marked = true)
                HandTag("budget")
                HandTag("on this phone")
            }
            SectionHead("doors")
            Door("2.4 GB of extra shots to tidy", {}, mark = "2.4 GB")
            Door("14 private photos in plain sight", {}, alert = true)
            Door("Q3 roadmap sync — notes are ready", {}, meta = "Tue 28 Oct · 42 min")
            HandDivider()
            SectionHead("inputs & progress")
            HandField("", {}, placeholder = "search every conversation…", singleLine = true, leading = Doodle.SEARCH)
            HandProgress(0.62f)
            SectionHead("photos are snapshots")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(-3f, 2f, -1f).forEachIndexed { i, t ->
                    Snapshot(tilt = t, faded = i == 2) { Box(Modifier.size(84.dp).background(listOf(Color(0xFFB9A58C), Color(0xFF8FA3A8), Color(0xFFC9B79C))[i])) }
                }
            }
            SectionHead("navigation")
            Box(Modifier.fillMaxWidth().border(1.dp, c.rule, RoundedCornerShape(12.dp))) { HandNav(HandTab.TODAY, {}) }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).background(color, RoundedCornerShape(14.dp)).border(1.dp, burrow.rule, RoundedCornerShape(14.dp)))
        Meta(name)
    }
}
