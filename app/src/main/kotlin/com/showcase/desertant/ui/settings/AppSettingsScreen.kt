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
import com.showcase.desertant.ui.hand.ChoicePill
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.ThemeMode
import com.showcase.desertant.ui.theme.Voice
import com.showcase.desertant.ui.theme.burrow

/** Burrow's own settings: how it looks, and the way to privacy and the models. */
@Composable
fun AppSettingsScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val appearance = LocalContext.current.app.appearance
    val mode by appearance.mode.collectAsState()
    val voice by appearance.voice.collectAsState()

    HandPage(onBack = onBack) {
        PageIntro("settings", "How Burrow looks, and where its privacy switches and models live.", doodle = Doodle.GEAR)

        SectionHead("light or dark")
        Meta("Burrow's own choice, whatever the phone is set to.")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoicePill("like the phone", mode == ThemeMode.SYSTEM, { appearance.setMode(ThemeMode.SYSTEM) }, leading = Doodle.PHONE)
            ChoicePill("light", mode == ThemeMode.LIGHT, { appearance.setMode(ThemeMode.LIGHT) }, leading = Doodle.SUN)
            ChoicePill("dark", mode == ThemeMode.DARK, { appearance.setMode(ThemeMode.DARK) }, leading = Doodle.MOON)
        }

        Spacer(Modifier.height(24.dp))
        SectionHead("handwriting")
        Meta("Headlines are always hand-lettered. Pick how handwritten the smaller text under them should be.")
        Spacer(Modifier.height(8.dp))
        Scale(Voice.entries.indexOf(voice))
        Voice.entries.forEach { v -> VoiceRow(v, v == voice) { appearance.setVoice(v) } }

        Spacer(Modifier.height(20.dp))
        Door("privacy", { open("privacy") }, meta = "the ledger, never go online, lock, wipe everything",
            leading = { com.showcase.desertant.ui.hand.DoodleIcon(Doodle.LOCK, null, size = 26.dp) })
        Door("models & languages", { open("models") }, meta = "what's on the phone, speech packs for 1,600+ languages",
            leading = { com.showcase.desertant.ui.hand.DoodleIcon(Doodle.SPARK, null, size = 26.dp) })
        Door("logs", { open("logs") }, meta = "each step Burrow took, to copy when something fails",
            leading = { com.showcase.desertant.ui.hand.DoodleIcon(Doodle.PEN, null, size = 26.dp) })
    }
}

/** A hand-drawn line from formal to handwritten, with the current stop marked. */
@Composable
private fun Scale(selected: Int) {
    val ink = burrow.ink
    val rule = burrow.rule
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val y = size.height / 2
            val n = Voice.entries.size
            drawLine(rule, Offset(6f, y), Offset(size.width - 6f, y + 1f), 2.dp.toPx(), StrokeCap.Round)
            for (i in 0 until n) {
                val x = 6f + (size.width - 12f) * i / (n - 1)
                if (i == selected) drawCircle(ink, 7.dp.toPx(), Offset(x, y))
                else drawCircle(ink, 3.dp.toPx(), Offset(x, y))
            }
        }
        Row {
            Meta("formal", Modifier.weight(1f))
            Meta("handwritten")
        }
    }
}

@Composable
private fun VoiceRow(v: Voice, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(burrow.smudge) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).border(1.8.dp, burrow.ink, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(burrow.ink))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.label, style = Hand.label, color = burrow.ink)
            // The sample is set in the voice itself, so the choice is made by looking.
            Text(
                "Burrow checks your photos on this phone.",
                style = TextStyle(fontFamily = v.family, fontSize = (15 * v.scale).sp, lineHeight = (21 * v.scale).sp),
                color = burrow.ink,
            )
            Meta(v.note)
        }
    }
}
