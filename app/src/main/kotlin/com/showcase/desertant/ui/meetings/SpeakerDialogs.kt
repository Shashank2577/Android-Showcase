package com.showcase.desertant.ui.meetings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.VoiceSignature
import com.showcase.desertant.people.People
import com.showcase.desertant.people.ProfileMatcher
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.SwitchRow
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

/** "Who is this?" — pick a person (best voice match first), or name someone new. */
@Composable
fun AssignSpeakerDialog(speaker: MeetingSpeakerEntity, profiles: List<ProfileEntity>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val people = remember { People(context) }
    val learn by context.app.privacy.learnVoices.collectAsState()
    val scores = remember(speaker.id, profiles) {
        speaker.signatureJson?.let { json ->
            val sig = runCatching { StoreJson.decodeFromString(VoiceSignature.serializer(), json) }.getOrNull()
            sig?.let { ProfileMatcher(profiles).scores(it).toMap() }
        }.orEmpty()
    }
    val ranked = profiles.sortedByDescending { scores[it.id] ?: -1.0 }
    var choice by remember { mutableStateOf<Long?>(speaker.profileId ?: speaker.suggestedProfileId ?: if (profiles.isEmpty()) NEW else null) }
    var newName by remember { mutableStateOf("") }
    var isMe by remember { mutableStateOf(profiles.none { it.isMe }) }
    val ready = (choice == NEW && newName.isNotBlank()) || (choice != null && choice != NEW)

    HandSheet(onDismiss) {
        Text("who is this voice?", style = Hand.headline.copy(fontSize = 34.sp), color = burrow.ink)
        speaker.sampleQuote?.let { Body("“$it”") }
        Meta(
            if (learn) "Pick a person. Burrow learns their voice from this talk, so it can suggest them next time."
            else "Pick a person. Voice learning is off, so this only labels this talk.",
        )
        ranked.forEach { p ->
            Choice(if (p.isMe) "${p.name} (you)" else p.name, scores[p.id]?.let { "${"%.0f".format(it * 100)}% voice match" }, choice == p.id) { choice = p.id }
        }
        Choice("someone new", null, choice == NEW) { choice = NEW }
        if (choice == NEW) {
            HandField(newName, { newName = it }, placeholder = "their name", singleLine = true)
            SwitchRow("this is me", isMe, { isMe = it })
        }
        InkButton(
            "save",
            {
                val c = choice
                scope.launch {
                    if (c == NEW) people.createFromSpeaker(speaker.id, newName, isMe) else if (c != null) people.assign(speaker.id, c)
                    onDismiss()
                }
            },
            Modifier.fillMaxWidth(),
            enabled = ready,
        )
        if (speaker.profileId != null) {
            HandLink("not them — unlink", { scope.launch { people.unassign(speaker.id); onDismiss() } }, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** A radio row drawn in ink: an empty ring, or a filled one when chosen. */
@Composable
private fun Choice(title: String, note: String?, selected: Boolean, onClick: () -> Unit) {
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
            Text(title, style = Hand.door.copy(fontSize = 23.sp), color = burrow.ink)
            if (note != null) Meta(note)
        }
    }
}

private const val NEW = -1L
