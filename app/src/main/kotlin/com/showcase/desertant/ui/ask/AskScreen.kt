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
import androidx.compose.ui.unit.dp
import com.showcase.desertant.app
import com.showcase.desertant.ask.AskModel
import com.showcase.desertant.ask.AskSource
import com.showcase.desertant.ask.AskStep
import com.showcase.desertant.ask.AskUi
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.ui.common.Thumbnail
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.ConfirmSheet
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.ItemRow
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.Prose
import com.showcase.desertant.ui.hand.SheetTitle
import com.showcase.desertant.ui.hand.Warning
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
import com.showcase.desertant.ui.theme.Type
import com.showcase.desertant.ui.theme.burrow

/**
 * "ask Burrow", a tab. Its job: get an answer about your talks, and see where
 * it came from. The answer comes first, then its sources; the steps the model
 * took are one link away. Changes wait for a yes in a sheet.
 */
@Composable
fun AskScreen(open: (String) -> Unit, onBack: (() -> Unit)? = null) {
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
            "my to-dos this week",
        ).take(3).ifEmpty { DEFAULT_SUGGESTIONS }
    }
    AskContent(
        ui = ui,
        model = model,
        ramGb = remember { AskModel.ramGb(context) },
        neverOnline = neverOnline,
        suggestions = suggestions,
        onBack = onBack ?: {},
        onAsk = session::ask,
        onDecide = session::decide,
        onClear = session::clear,
        onDownload = { AskModel.download(context) },
        open = open,
        showBack = onBack != null,
    )
}

private val DEFAULT_SUGGESTIONS = listOf("my to-dos this week", "who talks about the budget?", "photos of receipts")

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
    showBack: Boolean = false,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var trace by remember { mutableStateOf(false) }
    var abilities by remember { mutableStateOf(false) }
    val ready = model is AskModel.State.Ready
    val ask = { q: String -> if (q.isNotBlank() && !ui.busy) { onAsk(q.trim()); text = "" } }
    HandPage(
        onBack = if (showBack) onBack else null,
        topEnd = { DoodleButton(Doodle.GEAR, "Settings", { open("settings") }); DoodleButton(Doodle.LOCK, "Privacy", { open("privacy") }) },
        bottom = when {
            ready -> ({
                HandField(
                    text, { text = it },
                    placeholder = "ask about your talks…",
                    singleLine = true,
                    trailing = { SendButton(enabled = text.isNotBlank() && !ui.busy) { ask(text) } },
                )
            })
            model !is AskModel.State.Downloading && !neverOnline -> ({
                InkButton(
                    if (model is AskModel.State.Failed) "try again" else "download",
                    onDownload, Modifier.widthIn(max = 400.dp).fillMaxWidth(), trailing = Doodle.ARROW,
                )
            })
            else -> null
        },
    ) {
        Text("ask Burrow", Modifier.padding(bottom = 12.dp), style = Hand.headline, color = burrow.ink)
        if (!ready) {
            Setup(model, ramGb, neverOnline)
            return@HandPage
        }

        if (ui.phase == AskUi.Phase.IDLE) {
            Body("Ask about your talks, people and photos. A small model on this phone looks it up, and nothing leaves the phone.")
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s -> PillButton(s, { ask(s) }) }
            }
            Spacer(Modifier.height(8.dp))
            HandLink("what it can do", { abilities = true })
            return@HandPage
        }

        // Your question, on the right, in a smudge well.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                ui.question,
                Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(burrow.smudge).padding(horizontal = 14.dp, vertical = 10.dp),
                style = Type.body, color = burrow.ink,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (ui.busy) {
            val now = ui.steps.lastOrNull { it.status == AskStep.Status.RUNNING }?.label
            Caption(
                when {
                    ui.phase == AskUi.Phase.LOADING -> "Waking the model up (a few seconds the first time)…"
                    now != null -> "$now…"
                    ui.steps.isEmpty() -> "Reading your question…"
                    else -> "Reading what it found…"
                },
            )
            HandProgress(null, Modifier.padding(vertical = 6.dp))
        }

        ui.answer?.let { a ->
            Prose(a)
            if (ui.sources.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Caption("from")
                ui.sources.forEach { SourceRow(it, open) }
            }
        }

        ui.error?.let { e ->
            Warning("That didn't work. $e")
            HandLink("start again", onClear)
        }

        if (ui.steps.isNotEmpty() && !ui.busy) {
            Spacer(Modifier.height(8.dp))
            HandLink(
                (if (ui.steps.size == 1) "1 step" else "${ui.steps.size} steps") + ", all on this phone",
                { trace = true },
            )
        }
        if (ui.answer != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                DoodleIcon(Doodle.CLOUD_OFF, null, Modifier.padding(top = 1.dp), size = 16.dp, color = burrow.pencil)
                Spacer(Modifier.width(6.dp))
                Caption("Answered by a small model on this phone. It can be wrong, so check the talk. Nothing is saved.", Modifier.weight(1f))
            }
            HandLink("clear", onClear)
        }
    }

    ui.pending?.let { c ->
        ConfirmSheet(
            title = "go ahead?",
            body = "Burrow wants to ${c.sentence}. This changes something, so it waits for you.",
            confirm = "do it",
            onConfirm = { onDecide(true) },
            onDismiss = { onDecide(false) },
            cancel = "no",
        )
    }
    if (trace) {
        HandSheet({ trace = false }) {
            SheetTitle("what Burrow did")
            Body("“${ui.question}”")
            ui.steps.forEach { StepRow(it) }
            Caption("Every step ran on this phone, with Burrow's own tools.")
        }
    }
    if (abilities) {
        HandSheet({ abilities = false }) {
            SheetTitle("what it can do")
            listOf(
                "Find what someone said, and in which talk.",
                "List to-dos and promises, by person or week.",
                "Search chats you've shared, and sum up a talk.",
                "Find photos by what's in them.",
                "Rename a talk or start recording, only after you say yes.",
            ).forEach { Body("•  $it") }
        }
    }
}

/** The send control inside the field: a 48 dp circle, ink when there's a question, graphite when empty. */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val c = burrow
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.background(c.ink) else Modifier.border(1.6.dp, c.graphite, CircleShape))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "ask", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { DoodleIcon(Doodle.ARROW, "Ask", size = 22.dp, color = if (enabled) c.paper else c.graphite) }
}

@Composable
private fun Setup(model: AskModel.State, ramGb: Int, neverOnline: Boolean) {
    val size = "%.1f GB".format(AskModel.SIZE_MB / 1000.0)
    Body("Burrow answers with ${AskModel.NAME}, Google's small open model, running on this phone. It's one download, then it works offline.")
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        MarkedText(size, mark = size, style = Type.contentTitle)
        Caption("  once, Wi-Fi is best", Modifier.padding(bottom = 3.dp))
    }
    Spacer(Modifier.height(12.dp))
    if (ramGb in 1 until AskModel.COMFORTABLE_RAM_GB) {
        Warning("This phone has about $ramGb GB of memory. Answers may be slow, and Burrow may close other apps to load the model.")
        Spacer(Modifier.height(8.dp))
    }
    when (model) {
        is AskModel.State.Downloading -> {
            HandProgress(model.percent / 100f)
            Caption("${model.percent}% · keeps going if you leave this page")
        }
        is AskModel.State.Failed -> Warning("It didn't finish: ${model.message}. Trying again picks up where it stopped.")
        else -> Unit
    }
    if (neverOnline) Body("“Never go online” is on. Turn it off on the privacy page to download the model, then back on.", Modifier.padding(top = 8.dp))
    Caption("You can remove it any time in settings → models.", Modifier.padding(top = 8.dp))
}

@Composable
private fun StepRow(step: AskStep) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        val (doodle, color) = when (step.status) {
            AskStep.Status.RUNNING -> Doodle.SQUIGGLE to burrow.pencil
            AskStep.Status.DONE -> Doodle.TICK to burrow.ink
            AskStep.Status.DECLINED -> Doodle.CROSS to burrow.pencil
            AskStep.Status.FAILED -> Doodle.EXCLAIM to burrow.redPen
        }
        DoodleIcon(doodle, null, Modifier.padding(top = 2.dp), size = 18.dp, color = color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(step.label, style = Type.rowTitle, color = burrow.ink)
            Text(
                "${step.tool}(${step.args})",
                Modifier.padding(top = 2.dp).background(burrow.smudge, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = Type.mono,
                color = burrow.ink,
            )
            step.summary?.let { Meta("→ $it", Modifier.padding(top = 2.dp)) }
        }
    }
}

@Composable
private fun SourceRow(s: AskSource, open: (String) -> Unit) {
    when (s) {
        is AskSource.Talk -> ItemRow(s.title + (s.atSec?.let { " · ${formatClock(it)}" } ?: ""), meta = s.quote?.let { "“$it”" }, onClick = { open("meeting/${s.id}") })
        is AskSource.Chat -> ItemRow(s.title, meta = "“${s.quote}”", onClick = { open("chat/${s.id}") })
        is AskSource.Person -> ItemRow(s.name, meta = "Their page", onClick = { open("person/${s.id}") })
        is AskSource.Photos -> {
            ItemRow(if (s.count == 1) "1 photo of ${s.query}" else "${s.count} photos of ${s.query}", meta = "On this phone", onClick = { open("photos") })
            Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.uris.take(4).forEachIndexed { i, uri ->
                    Snapshot(tilt = listOf(-3f, 2f, -1f, 3f)[i]) { Thumbnail(uri, Modifier.size(64.dp), sizePx = 192) }
                }
            }
        }
    }
}
