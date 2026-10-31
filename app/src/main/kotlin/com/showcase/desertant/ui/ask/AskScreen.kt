@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.app
import com.showcase.desertant.ask.AskModel
import com.showcase.desertant.ask.AskSource
import com.showcase.desertant.ask.AskStep
import com.showcase.desertant.ask.AskUi
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.ChoicePill
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.Snapshot
import com.showcase.desertant.ui.hand.Steps
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.bodyStyle
import com.showcase.desertant.ui.theme.burrow

/** "ask Burrow": questions about talks, people and photos, answered on the phone with tool calls. */
@Composable
fun AskScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val session = app.ask
    val ui by session.ui.collectAsState()
    val model by AskModel.state.collectAsState()
    val neverOnline by app.privacy.neverOnline.collectAsState()
    LaunchedEffect(Unit) { AskModel.refresh(context) }
    // The model takes ~1.5 GB of memory; let it go a minute after the page closes.
    DisposableEffect(Unit) {
        AskModel.keep()
        onDispose { AskModel.releaseSoon(app.scope) }
    }
    LaunchedEffect(ui.openRoute) {
        ui.openRoute?.let { session.routeHandled(); open(it) }
    }
    val suggestions by produceState(DEFAULT_SUGGESTIONS) {
        val people = app.db.profiles().allNow().filterNot { it.isMe }.map { it.name.substringBefore(' ') }
        val talk = app.db.meetings().byStatus(MeetingStatus.READY).firstOrNull()?.title
        value = listOfNotNull(
            people.firstOrNull()?.let { "what did $it promise?" },
            talk?.let { "what was “$it” about?" },
            people.getOrNull(1)?.let { "when did I last talk to $it?" },
            "photos of receipts",
        ).ifEmpty { DEFAULT_SUGGESTIONS }
    }
    AskContent(
        ui = ui,
        model = model,
        ramGb = remember { AskModel.ramGb(context) },
        neverOnline = neverOnline,
        suggestions = suggestions,
        onBack = onBack,
        onAsk = session::ask,
        onDecide = session::decide,
        onClear = session::clear,
        onDownload = { AskModel.download(context) },
        open = open,
    )
}

private val DEFAULT_SUGGESTIONS = listOf("what are my follow-ups this week?", "who talks about the budget?", "photos of receipts")

/** The page itself, without the model, so it can be rendered in tests. */
@Composable
fun AskContent(
    ui: AskUi,
    model: AskModel.State,
    ramGb: Int,
    neverOnline: Boolean,
    suggestions: List<String>,
    onBack: () -> Unit,
    onAsk: (String) -> Unit,
    onDecide: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit,
    open: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val ready = model is AskModel.State.Ready
    HandPage(
        onBack = onBack,
        bottom = if (ready && !ui.busy && ui.pending == null) {
            {
                InkButton(
                    if (ui.answer != null || ui.error != null) "ask this" else "ask",
                    { onAsk(text) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank(), trailing = Doodle.ARROW,
                )
            }
        } else null,
    ) {
        PageIntro(
            "ask Burrow",
            "Ask about your talks, people and photos. A small model on this phone looks it up with Burrow's own tools, and shows every step. Nothing leaves the phone.",
            doodle = Doodle.BUBBLE,
        )
        if (!ready) {
            Setup(model, ramGb, neverOnline, onDownload)
            return@HandPage
        }

        HandField(text, { text = it }, placeholder = "what did Priya promise last week?", singleLine = true, leading = Doodle.SEARCH)
        if (ui.phase == AskUi.Phase.IDLE) {
            Spacer(Modifier.height(14.dp))
            Meta("try one:")
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s -> ChoicePill(s, selected = false, onClick = { text = s; onAsk(s) }) }
            }
            Spacer(Modifier.height(24.dp))
            SectionHead("what it can do")
            Steps(
                listOf(
                    "find what someone said, and in which talk",
                    "list follow-ups and promises, by person or week",
                    "search chats you've shared, and sum up a talk",
                    "find photos by what's in them",
                    "rename a talk or start recording — only after you say yes",
                ),
            )
        }

        if (ui.phase != AskUi.Phase.IDLE) {
            Spacer(Modifier.height(18.dp))
            SectionHead("what Burrow did", note = "all on this phone")
            Meta("“${ui.question}”", maxLines = 2)
            Spacer(Modifier.height(4.dp))
            if (ui.phase == AskUi.Phase.LOADING) Working("waking the model up… (a few seconds the first time)")
            ui.steps.forEach { StepRow(it) }
            if (ui.phase == AskUi.Phase.THINKING && ui.pending == null) Working(if (ui.steps.isEmpty()) "reading your question…" else "reading what it found…")
        }

        ui.pending?.let { c ->
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().border(1.8.dp, burrow.ink, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DoodleIcon(Doodle.EXCLAIM, null, size = 22.dp, color = burrow.redPen)
                    Spacer(Modifier.width(8.dp))
                    Text("Burrow wants to ${c.sentence}.", style = Hand.door.copy(fontSize = 24.sp, lineHeight = 28.sp), color = burrow.ink)
                }
                Meta("This changes something, so it waits for you.")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkButton("do it", { onDecide(true) }, Modifier.weight(1f), trailing = Doodle.TICK)
                    Spacer(Modifier.width(18.dp))
                    HandLink("no", { onDecide(false) })
                }
            }
        }

        ui.answer?.let { a ->
            Spacer(Modifier.height(18.dp))
            SectionHead("answer")
            Column(Modifier.fillMaxWidth().border(1.8.dp, burrow.ink, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Body(a)
                if (ui.sources.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Meta("from — tap to open:")
                    ui.sources.forEach { SourceRow(it, open) }
                }
            }
            Spacer(Modifier.height(12.dp))
            AnsweredNote(onClear)
        }

        ui.error?.let { e ->
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(burrow.smudge).padding(16.dp)) {
                Text("that didn't work", style = Hand.title.copy(fontSize = 24.sp), color = burrow.redPen)
                Meta(e)
                Spacer(Modifier.height(6.dp))
                HandLink("start again", onClear)
            }
        }
    }
}

@Composable
private fun Setup(model: AskModel.State, ramGb: Int, neverOnline: Boolean, onDownload: () -> Unit) {
    SectionHead("first, the model", note = "once")
    Body("Burrow answers with ${AskModel.NAME}, Google's small open model, running on this phone. It's a ${"%.1f".format(AskModel.SIZE_MB / 1000.0)} GB download — Wi-Fi is best — and it can be removed any time in settings → models & languages.")
    Spacer(Modifier.height(10.dp))
    Steps(listOf("download the model (once)", "ask in your own words", "check every step, and the talk it came from"), current = 0)
    if (ramGb in 1 until AskModel.COMFORTABLE_RAM_GB) {
        Spacer(Modifier.height(8.dp))
        Meta("This phone has about $ramGb GB of memory. Answers may be slow, and Burrow may need to close other apps' memory to load the model.", color = burrow.redPen)
    }
    Spacer(Modifier.height(16.dp))
    when (model) {
        is AskModel.State.Downloading -> {
            HandProgress(model.percent / 100f)
            Meta("${model.percent}% · keeps going if you leave this page")
        }
        else -> {
            if (model is AskModel.State.Failed) {
                Meta("Didn't finish: ${model.message}. Trying again picks up where it stopped.", color = burrow.redPen)
                Spacer(Modifier.height(8.dp))
            }
            InkButton(
                if (model is AskModel.State.Failed) "try again" else "download ${AskModel.NAME}", onDownload, Modifier.fillMaxWidth(),
                enabled = !neverOnline, trailing = Doodle.ARROW,
            )
            if (neverOnline) Meta("“never go online” is on. Turn it off in privacy to download the model, then back on.", Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Working(text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HandProgress(null, Modifier.width(56.dp))
        Spacer(Modifier.width(12.dp))
        Meta(text)
    }
}

@Composable
private fun StepRow(step: AskStep) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        val (doodle, color) = when (step.status) {
            AskStep.Status.RUNNING -> Doodle.SQUIGGLE to burrow.pencil
            AskStep.Status.DONE -> Doodle.TICK to burrow.ink
            AskStep.Status.DECLINED -> Doodle.CROSS to burrow.pencil
            AskStep.Status.FAILED -> Doodle.CROSS to burrow.redPen
        }
        DoodleIcon(doodle, null, Modifier.padding(top = 2.dp), size = 18.dp, color = color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(step.label, style = Hand.label, color = burrow.ink)
            Text(
                "${step.tool}(${step.args})",
                Modifier.background(burrow.smudge, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = burrow.pencil,
            )
            step.summary?.let { Text("→ $it", style = bodyStyle(14f, 19f), color = burrow.pencil) }
        }
    }
}

@Composable
private fun SourceRow(s: AskSource, open: (String) -> Unit) {
    when (s) {
        is AskSource.Talk -> LinkRow(Doodle.WAVE, s.title + (s.atSec?.let { " · ${formatClock(it)}" } ?: ""), s.quote?.let { "“$it”" }) { open("meeting/${s.id}") }
        is AskSource.Chat -> LinkRow(Doodle.BUBBLE, s.title, "“${s.quote}”") { open("chat/${s.id}") }
        is AskSource.Person -> LinkRow(Doodle.PERSON, s.name, "their page") { open("person/${s.id}") }
        is AskSource.Photos -> {
            LinkRow(Doodle.CAMERA, if (s.count == 1) "1 photo of ${s.query}" else "${s.count} photos of ${s.query}", "on this phone") { open("photos") }
            Row(Modifier.padding(top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.uris.take(4).forEachIndexed { i, uri ->
                    Snapshot(tilt = listOf(-3f, 2f, -1f, 3f)[i]) { Thumbnail(uri, Modifier.size(64.dp), sizePx = 192) }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(doodle: Doodle, title: String, meta: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DoodleIcon(doodle, null, size = 22.dp, color = burrow.pencil)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Hand.label, color = burrow.ink, maxLines = 1)
            meta?.let { Meta(it, maxLines = 2) }
        }
        DoodleIcon(Doodle.ARROW, null, size = 18.dp, color = burrow.pencil)
    }
}

@Composable
private fun AnsweredNote(onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DoodleIcon(Doodle.CLOUD_OFF, null, size = 18.dp, color = burrow.pencil)
        Spacer(Modifier.width(8.dp))
        Meta("Answered by a small model on this phone; it can be wrong, so check the talk. Nothing is saved.", Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    HandLink("clear and ask something else", onClear, style = Hand.small)
}
