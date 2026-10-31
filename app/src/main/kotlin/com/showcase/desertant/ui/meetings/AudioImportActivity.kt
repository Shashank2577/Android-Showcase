package com.showcase.desertant.ui.meetings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.showcase.desertant.MainActivity
import com.showcase.desertant.app
import com.showcase.desertant.data.MeetingSource
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.meetings.MeetingImporter
import com.showcase.desertant.people.CallLinks
import com.showcase.desertant.people.Contacts
import com.showcase.desertant.ui.hand.ChoicePill
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

/**
 * "Add to Burrow" for audio from any app: a call recording from any phone's
 * recorder, a voice memo, or an iPhone recording that reached this phone
 * through Files, Drive or email. Burrow asks whether it's a call and who with,
 * then writes its notes like any talk.
 */
class AudioImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        if (uri == null) {
            finish()
            return
        }
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "recording"
        setContent {
            com.showcase.desertant.ui.theme.AppTheme {
                val scope = rememberCoroutineScope()
                val profiles by remember { app.db.profiles().all() }.collectAsState(initial = emptyList())
                var busy by remember { mutableStateOf(false) }
                AudioImportContent(name, profiles, busy, onClose = ::finish) { isCall, who, personId ->
                    busy = true
                    scope.launch {
                        val id = runCatching {
                            MeetingImporter.import(this@AudioImportActivity, uri, if (isCall) MeetingSource.CALL else MeetingSource.IMPORTED, name, callWith = who, callProfileId = personId)
                        }.getOrNull()
                        if (id != null) startActivity(Intent(this@AudioImportActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "meeting/$id").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                        finish()
                    }
                }
            }
        }
    }
}

/** Is it a call, and with whom? Pre-filled from the file name when Burrow can tell. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioImportContent(fileName: String, profiles: List<ProfileEntity>, busy: Boolean, onClose: () -> Unit, onAdd: (isCall: Boolean, who: String?, personId: Long?) -> Unit) {
    val guessWho = remember(fileName) { CallLinks.whoFromFileName(fileName) }
    val guessCall = remember(fileName) { CallLinks.isCallRecording("", fileName) }
    var isCall by remember { mutableStateOf(guessCall) }
    var person by remember(profiles) { mutableStateOf(guessWho?.let { Contacts.match(it, profiles) }?.id) }
    var who by remember { mutableStateOf(if (guessCall) guessWho.orEmpty() else "") }
    HandPage(
        onBack = onClose,
        bottom = {
            InkButton("add to Burrow", { onAdd(isCall, if (isCall) (profiles.firstOrNull { it.id == person }?.name ?: who.trim().ifEmpty { null }) else null, if (isCall) person else null) }, Modifier.fillMaxWidth(), busy = busy, trailing = Doodle.ARROW)
        },
    ) {
        PageIntro("add a recording", "Burrow writes its notes on this phone, like any talk. A call goes on the page of the person it was with.", doodle = Doodle.MIC)
        Text(fileName, style = Hand.label, color = burrow.ink, maxLines = 2)
        Spacer(Modifier.height(16.dp))
        SectionHead("what is it?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("a talk or memo", !isCall, { isCall = false }, leading = Doodle.MIC)
            ChoicePill("a phone call", isCall, { isCall = true }, leading = Doodle.PHONE)
        }
        if (isCall) {
            Spacer(Modifier.height(16.dp))
            SectionHead("with whom?", note = if (guessCall && guessWho != null) "from the file name" else null)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.filterNot { it.isMe }.forEach { p -> ChoicePill(p.name, person == p.id, { person = if (person == p.id) null else p.id }) }
                }
                if (person == null) {
                    HandField(who, { who = it }, placeholder = "their name or number", singleLine = true, leading = Doodle.PERSON)
                    Meta("Not one of your people yet? Type their name: the call links to them once you add them.")
                }
            }
        }
    }
}
