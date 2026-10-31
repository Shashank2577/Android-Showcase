package com.showcase.desertant.ui.chats

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.showcase.desertant.MainActivity
import com.showcase.desertant.app
import com.showcase.desertant.chats.ChatImporter
import com.showcase.desertant.chats.ChatParser
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.HandEmpty
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * "Add chat to Burrow" in the share sheet. WhatsApp's Export chat shares a
 * .txt (or a .zip with it); Burrow shows what it found and who's who, and
 * keeps nothing until the user says so.
 */
class ChatImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shared = intent
        setContent {
            AppTheme {
                val context = this
                val scope = rememberCoroutineScope()
                var chat by remember { mutableStateOf<ChatParser.Chat?>(null) }
                var failed by remember { mutableStateOf(false) }
                var saving by remember { mutableStateOf(false) }
                var choosing by remember { mutableStateOf<String?>(null) }
                val who = remember { mutableStateMapOf<String, ChatImporter.Who>() }
                val profiles by remember { app.db.profiles().all() }.collectAsState(initial = emptyList())

                LaunchedEffect(Unit) {
                    val read = ChatImporter.read(context, shared)
                    val parsed = read?.let { (text, name) -> ChatParser.parse(text, name) }?.takeIf { it.messages.isNotEmpty() }
                    if (parsed == null) failed = true
                    else {
                        who.putAll(ChatImporter.guess(parsed, app.db.profiles().allNow()))
                        chat = parsed
                    }
                }

                val c = chat
                when {
                    failed -> HandPage(onBack = ::finish) {
                        HandEmpty(
                            "that isn't a chat export",
                            "Burrow reads chats you export yourself. In WhatsApp: open the chat → ⋮ → More → Export chat → Without media → Burrow.",
                            doodle = Doodle.BUBBLE,
                        )
                        Spacer(Modifier.height(16.dp))
                        InkButton("close", ::finish, Modifier.fillMaxWidth())
                    }
                    c == null -> HandPage(onBack = ::finish) { Meta("reading the chat…") }
                    else -> ChatImportContent(
                        title = c.title ?: "chat",
                        messages = c.messages.size,
                        range = "${dateTime(context, c.messages.minOf { it.at })} – ${dateTime(context, c.messages.maxOf { it.at })}",
                        senders = c.senders,
                        who = who,
                        profiles = profiles,
                        saving = saving,
                        onChoose = { choosing = it },
                        onKeep = {
                            saving = true
                            scope.launch {
                                val id = runCatching { ChatImporter.save(context, c, who.toMap()) }.getOrNull()
                                saving = false
                                if (id != null) {
                                    startActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "chat/$id").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                                    finish()
                                } else failed = true
                            }
                        },
                        onClose = ::finish,
                    )
                }
                choosing?.let { s ->
                    WhoSheet(s, profiles, onDismiss = { choosing = null }) { w -> who[s] = w; choosing = null }
                }
            }
        }
    }
}
