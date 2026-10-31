package com.showcase.desertant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.app
import com.showcase.desertant.ask.AskModel
import com.showcase.desertant.core.VOZ_LANGUAGES
import com.showcase.desertant.core.languageName
import com.showcase.desertant.speech.SpeechPacks
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow

/** One speech pack: its name, its state, and the one action that makes sense now. */
@Composable
fun PackRow(pack: SpeechPacks.Pack, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val neverOnline by context.app.privacy.neverOnline.collectAsState()
    val states by SpeechPacks.state.collectAsState()
    val st = states[pack.code] ?: SpeechPacks.State.Missing
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (pack.kind == SpeechPacks.Kind.UNIVERSAL) pack.name.lowercase() else "${pack.name} · ${pack.native}", style = Hand.label, color = burrow.ink)
            Meta(
                when (st) {
                    SpeechPacks.State.Ready -> if (pack.kind == SpeechPacks.Kind.UNIVERSAL) "on the phone · Omnilingual, for any language without its own pack" else "on the phone · talks in ${pack.name} get notes"
                    is SpeechPacks.State.Downloading -> "downloading… ${st.percent}%"
                    is SpeechPacks.State.Failed -> "didn't download: ${st.message}"
                    SpeechPacks.State.Missing -> if (pack.kind == SpeechPacks.Kind.UNIVERSAL) "${pack.native} · less accurate · about ${pack.sizeMb} MB" else "about ${pack.sizeMb} MB"
                },
                color = if (st is SpeechPacks.State.Failed) burrow.redPen else burrow.pencil,
            )
        }
        Spacer(Modifier.width(10.dp))
        when {
            st == SpeechPacks.State.Ready -> PillButton("remove", { SpeechPacks.remove(context, pack) })
            st is SpeechPacks.State.Downloading -> Unit
            neverOnline -> Meta("network off")
            else -> PillButton(if (st is SpeechPacks.State.Failed) "try again" else "download", { SpeechPacks.download(context, pack) })
        }
    }
}

/**
 * Speech packs for languages Voz doesn't speak. [compact] shows the five most
 * spoken Indian languages (plus any already downloaded) and points to the rest.
 */
@Composable
fun SpeechPackList(compact: Boolean = false, onMore: (() -> Unit)? = null) {
    val states by SpeechPacks.state.collectAsState()
    var all by remember { mutableStateOf(!compact) }
    Meta("Voz writes 25 European languages. For Indian languages Burrow uses AI4Bharat's IndicConformer — one download per language — and Meta's Omnilingual for everything else. All of it runs on the phone.")
    val shown = if (all) SpeechPacks.INDIAN else SpeechPacks.INDIAN.filterIndexed { i, p -> i < 5 || states[p.code] == SpeechPacks.State.Ready }
    shown.forEachIndexed { i, p ->
        if (i > 0) HandDivider(seed = i)
        PackRow(p)
    }
    if (!all) {
        HandLink(
            "all 22 Indian languages",
            { if (onMore != null) onMore() else all = true },
            Modifier.padding(vertical = 4.dp),
        )
    }
    if (!compact) {
        HandDivider(seed = 99)
        PackRow(SpeechPacks.UNIVERSAL)
    }
}

/** "What language is this talk in?" — automatic, or one the user picks. */
@Composable
fun LanguageSheet(current: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val european = remember { VOZ_LANGUAGES.map { it to languageName(it).substringBefore(" (") }.sortedBy { it.second } }
    HandSheet(onDismiss) {
        Text("what language is this talk in?", style = Hand.headline.copy(fontSize = 32.sp), color = burrow.ink)
        Meta("Burrow usually works this out itself. If it got it wrong, or the language is a rare one, pick it and the notes are written again.")
        SectionHead("Indian languages")
        SpeechPacks.INDIAN.forEach { p -> LanguageChoice("${p.name} · ${p.native}", current == p.code) { onPick(p.code) } }
        SectionHead("European languages")
        european.forEach { (code, name) -> LanguageChoice(name, current == code) { onPick(code) } }
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (selected) Modifier.background(burrow.smudge) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = Hand.label, color = burrow.ink)
        if (selected) Meta("now")
    }
}

/** The "ask Burrow" model: Gemma, downloaded on request like a speech pack. */
@Composable
fun AskModelRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val neverOnline by context.app.privacy.neverOnline.collectAsState()
    val st by AskModel.state.collectAsState()
    LaunchedEffect(Unit) { AskModel.refresh(context) }
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${AskModel.NAME.lowercase()} · Google, Apache-2.0", style = Hand.label, color = burrow.ink)
            Meta(
                when (val s = st) {
                    AskModel.State.Ready -> "on the phone · answers questions with Burrow's tools"
                    is AskModel.State.Downloading -> "downloading… ${s.percent}%"
                    is AskModel.State.Failed -> "didn't download: ${s.message}"
                    AskModel.State.Missing -> "about ${"%.1f".format(AskModel.SIZE_MB / 1000.0)} GB · only needed for ask Burrow"
                },
                color = if (st is AskModel.State.Failed) burrow.redPen else burrow.pencil,
            )
        }
        Spacer(Modifier.width(10.dp))
        when {
            st == AskModel.State.Ready -> PillButton("remove", { AskModel.remove(context) })
            st is AskModel.State.Downloading -> Unit
            neverOnline -> Meta("network off")
            else -> PillButton(if (st is AskModel.State.Failed) "try again" else "download", { AskModel.download(context) })
        }
    }
}
