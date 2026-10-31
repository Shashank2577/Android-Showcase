package com.showcase.desertant.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.app
import com.showcase.desertant.chats.ChatImporter
import com.showcase.desertant.chats.ChatImporter.Who
import com.showcase.desertant.data.ChatMessageEntity
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.ConfirmSheet
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.bodyStyle
import com.showcase.desertant.ui.theme.burrow
import com.showcase.desertant.ui.theme.seriesColor
import kotlinx.coroutines.launch

/** A chat the user shared into Burrow: who's who, search, and the latest messages. */
@Composable
fun ChatScreen(id: Long, onBack: () -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val db = context.app.db
    val scope = rememberCoroutineScope()
    val chat by remember(id) { db.chats().observe(id) }.collectAsState(initial = null)
    val names by remember(id) { db.chats().people(id) }.collectAsState(initial = emptyList())
    val profiles by remember { db.profiles().all() }.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf(120) }
    val latest by remember(id, limit) { db.chats().latest(id, limit) }.collectAsState(initial = emptyList())
    val found by remember(id, query) { db.chats().search(id, query.trim()) }.collectAsState(initial = emptyList())
    var choosing by remember { mutableStateOf<String?>(null) }
    var forgetting by remember { mutableStateOf(false) }

    val c = chat ?: return
    val who = names.associate { n -> n.sender to whoOf(n.isMe, n.profileId) }
    HandPage(onBack = onBack, topEnd = { PillButton("forget", { forgetting = true }, Modifier.padding(end = 8.dp)) }) {
        PageIntro(
            c.title,
            "A chat you shared into Burrow, kept only on this phone. Search it, or ask Burrow about it. Share a newer export to bring it up to date.",
            doodle = Doodle.BUBBLE,
        )
        Meta("${c.messageCount} messages · ${dateTime(context, c.firstAt)} – ${dateTime(context, c.lastAt)}")
        Spacer(Modifier.height(14.dp))
        SectionHead("who's who", note = "tap to change")
        names.forEach { n ->
            WhoRow(n.sender, n.messages, who[n.sender] ?: Who.Nobody, profiles, onClick = { choosing = n.sender }, open = open)
        }
        Spacer(Modifier.height(16.dp))
        HandField(query, { query = it }, placeholder = "search this chat", singleLine = true, leading = Doodle.SEARCH)
        Spacer(Modifier.height(10.dp))
        val shown = if (query.isBlank()) latest.reversed() else found
        if (query.isNotBlank()) Meta(if (found.isEmpty()) "nothing matches “$query”" else "${found.size} messages match, newest first")
        else if (c.messageCount > latest.size) HandLink("show earlier messages", { limit += 300 }, style = Hand.small)
        val me = names.filter { it.isMe }.map { it.sender }.toSet()
        shown.forEach { m -> Bubble(m, mine = m.sender in me, colorIndex = names.indexOfFirst { it.sender == m.sender }) }
    }

    choosing?.let { sender ->
        WhoSheet(sender, profiles, onDismiss = { choosing = null }) { w ->
            scope.launch { ChatImporter.setWho(context, id, sender, w) }
            choosing = null
        }
    }
    if (forgetting) {
        ConfirmSheet(
            "forget this chat?",
            "Burrow deletes its copy of these ${c.messageCount} messages. WhatsApp and your phone keep theirs.",
            "forget it",
            { scope.launch { db.chats().delete(id); forgetting = false; onBack() } },
            { forgetting = false },
            danger = true,
        )
    }
}

fun whoOf(isMe: Boolean, profileId: Long?): Who = when {
    isMe -> Who.Me
    profileId != null -> Who.Person(profileId)
    else -> Who.Nobody
}

/** One name in a chat and who Burrow thinks it is. */
@Composable
fun WhoRow(sender: String, messages: Int, who: Who, profiles: List<ProfileEntity>, onClick: () -> Unit, open: ((String) -> Unit)? = null) {
    val label = when (who) {
        Who.Me -> "you"
        is Who.Person -> profiles.firstOrNull { it.id == who.id }?.name ?: "someone"
        Who.New -> "a new person, “${sender.trim()}”"
        Who.Nobody -> "not linked"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(sender, style = Hand.label, color = burrow.ink, maxLines = 1)
            Meta("$messages messages")
        }
        Text("→ $label", style = Hand.small, color = if (who == Who.Nobody) burrow.pencil else burrow.ink)
        if (who is Who.Person && open != null) {
            Spacer(Modifier.width(4.dp))
            HandLink("page", { open("person/${who.id}") }, style = Hand.small)
        }
    }
}

/** Who is this name? You, one of your people, someone new, or nobody. */
@Composable
fun WhoSheet(sender: String, profiles: List<ProfileEntity>, onDismiss: () -> Unit, onPick: (Who) -> Unit) {
    HandSheet(onDismiss) {
        Text("who is “${sender.trim()}”?", style = Hand.headline.copy(fontSize = 32.sp), color = burrow.ink)
        Meta("Their messages go on that person's page. Burrow remembers the name, so their calls and next chats link too.")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("that's me", { onPick(Who.Me) }, leading = Doodle.PERSON)
            profiles.filterNot { it.isMe }.forEach { p -> PillButton(p.name, { onPick(Who.Person(p.id)) }) }
            PillButton("someone new, “${sender.trim()}”", { onPick(Who.New) }, leading = Doodle.PLUS)
            HandLink("nobody — leave it unlinked", { onPick(Who.Nobody) }, style = Hand.small)
        }
    }
}

@Composable
private fun Bubble(m: ChatMessageEntity, mine: Boolean, colorIndex: Int) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) burrow.smudge else burrow.paper)
                .then(if (mine) Modifier else Modifier.background(seriesColor(colorIndex).copy(alpha = 0.10f)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (!mine) Text(m.sender, style = Hand.small, color = burrow.pencil)
            Text(m.text, style = bodyStyle(15f, 21f), color = burrow.ink)
        }
        Meta(dateTime(context, m.at), Modifier.padding(horizontal = 6.dp))
    }
}

/** What the share-sheet import shows before anything is kept. */
@Composable
fun ChatImportContent(
    title: String,
    messages: Int,
    range: String,
    senders: List<Pair<String, Int>>,
    who: Map<String, Who>,
    profiles: List<ProfileEntity>,
    saving: Boolean,
    onChoose: (String) -> Unit,
    onKeep: () -> Unit,
    onClose: () -> Unit,
) {
    HandPage(
        onBack = onClose,
        bottom = {
            com.showcase.desertant.ui.hand.InkButton("keep it in Burrow", onKeep, Modifier.fillMaxWidth(), busy = saving, trailing = Doodle.ARROW)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DoodleIcon(Doodle.CLOUD_OFF, null, size = 16.dp, color = burrow.pencil)
                Spacer(Modifier.width(6.dp))
                Meta("Kept only on this phone. Forget it any time from the chat.")
            }
        },
    ) {
        PageIntro("add a chat", "Burrow keeps this chat on the phone and puts each person's messages on their page, so you can search them and ask about them.", doodle = Doodle.BUBBLE)
        Text(title, style = Hand.title, color = burrow.ink)
        Meta("$messages messages · $range")
        Spacer(Modifier.height(16.dp))
        SectionHead("who's who", note = "tap to change")
        Meta("Burrow guessed from your people and the chat's name.")
        senders.forEach { (s, n) -> WhoRow(s, n, who[s] ?: Who.Nobody, profiles, onClick = { onChoose(s) }) }
        Spacer(Modifier.height(16.dp))
        Body("Media isn't copied: photos and voice notes show as a note in the text.", color = burrow.pencil)
    }
}
