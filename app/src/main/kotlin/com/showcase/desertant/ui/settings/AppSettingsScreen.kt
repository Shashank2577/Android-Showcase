@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.app
import com.showcase.desertant.ask.AskModel
import com.showcase.desertant.ask.SummarySettings
import androidx.compose.foundation.layout.heightIn
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.ChoicePill
import com.showcase.desertant.ui.hand.DoneNote
import com.showcase.desertant.ui.theme.Type
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.SwitchRow
import com.showcase.desertant.ui.meetings.LanguageSheet
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.ThemeMode
import com.showcase.desertant.ui.theme.Voice
import com.showcase.desertant.ui.theme.burrow

/** Burrow's own settings: how it looks, and the way to privacy and the models. */
@Composable
fun AppSettingsScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val appearance = context.app.appearance
    val mode by appearance.mode.collectAsState()
    val voice by appearance.voice.collectAsState()
    val typeNote by appearance.typeNote.collectAsState()
    val summaries = LocalContext.current.app.summaries
    val language by summaries.language.collectAsState()
    val auto by summaries.auto.collectAsState()
    val modelState by AskModel.state.collectAsState()
    var choosingLanguage by remember { mutableStateOf(false) }

    HandPage(onBack = onBack) {
        PageIntro("settings", "How Burrow looks, and where its privacy switches and models live.", doodle = Doodle.GEAR)
        if (typeNote) DoneNote("Reading text is now a plain font. Change it under handwriting.", action = "ok", onAction = appearance::dismissTypeNote)

        SectionHead("light or dark", note = "Burrow's own choice, whatever the phone is set to.", first = !typeNote)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoicePill("like the phone", mode == ThemeMode.SYSTEM, { appearance.setMode(ThemeMode.SYSTEM) }, leading = Doodle.PHONE)
            ChoicePill("light", mode == ThemeMode.LIGHT, { appearance.setMode(ThemeMode.LIGHT) }, leading = Doodle.SUN)
            ChoicePill("dark", mode == ThemeMode.DARK, { appearance.setMode(ThemeMode.DARK) }, leading = Doodle.MOON)
        }

        SectionHead("handwriting", note = "Headings stay hand-lettered. Pick the face for everything you read.")
        Voice.entries.forEach { v -> VoiceRow(v, v == voice) { appearance.setVoice(v) } }

        SectionHead("summaries", note = "Written on this phone: what each talk was about, what was decided, who does what by when.")
        Text("write them in", style = Hand.label, color = burrow.ink)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                (if (language == SummarySettings.SAME_AS_TALK) "each talk's own" else SummarySettings.LANGUAGES.firstOrNull { it.english == language }?.let { if (it.native == it.english) it.english else "${it.native} · ${it.english}" } ?: language) + " ▾",
                { choosingLanguage = true },
            )
        }
        SwitchRow("summarise each talk", auto, summaries::setAuto, body = "As soon as its notes are ready. Or tap “summarise” on a talk when you want one.")
        if (modelState !is AskModel.State.Ready) {
            Door(
                "get the summary model", { open("models") },
                meta = "${AskModel.NAME}, ${"%.1f".format(AskModel.SIZE_MB / 1000.0)} GB once. The same one ask Burrow uses.",
                leading = { com.showcase.desertant.ui.hand.DoodleIcon(Doodle.SPARK, null, size = 26.dp) },
            )
        }

        SectionHead("more")
        Door("privacy", { open("privacy") }, meta = "The ledger, never go online, lock, wipe everything.",
            leading = { com.showcase.desertant.ui.hand.DoodleIcon(Doodle.LOCK, null, size = 26.dp) })
        Door("models & languages", { open("models") }, meta = "What's on the phone, and speech packs for 1,600+ languages.",
            leading = { com.showcase.desertant.ui.hand.DoodleIcon(Doodle.SPARK, null, size = 26.dp) })
        Door("logs", { open("logs") }, meta = "Each step Burrow took, to copy when something fails.",
            leading = { com.showcase.desertant.ui.hand.DoodleIcon(Doodle.PEN, null, size = 26.dp) })
    }
    if (choosingLanguage) {
        LanguageSheet(current = language, talk = null, onDismiss = { choosingLanguage = false }, offerSameAsTalk = true) {
            summaries.setLanguage(it)
            choosingLanguage = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { AskModel.refresh(context) }
}

@Composable
private fun VoiceRow(v: Voice, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(burrow.smudge) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).border(1.8.dp, burrow.ink, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(burrow.ink))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.label, style = Hand.label, color = burrow.ink)
            // The sample is set in the voice itself, so the choice is made by looking.
            Text("Priya promised to book the launch venue next week.", style = Type.body.copy(fontFamily = v.family), color = burrow.ink)
            Caption(v.note)
        }
    }
}
