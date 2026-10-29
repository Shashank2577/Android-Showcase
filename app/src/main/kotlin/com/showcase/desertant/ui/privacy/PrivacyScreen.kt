package com.showcase.desertant.ui.privacy

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.app
import com.showcase.desertant.privacy.Biometrics
import com.showcase.desertant.privacy.Housekeeping
import com.showcase.desertant.privacy.Ledger
import com.showcase.desertant.privacy.LedgerEntry
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.common.relativeDay
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.SwitchRow
import com.showcase.desertant.ui.onboarding.PrivacyExplainer
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PrivacyScreen(onBack: () -> Unit, openModels: () -> Unit, onWiped: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val p = app.privacy
    val scope = rememberCoroutineScope()
    val neverOnline by p.neverOnline.collectAsState()
    val lockApp by p.lockApp.collectAsState()
    val autoDelete by p.autoDeleteAudio.collectAsState()
    val learnVoices by p.learnVoices.collectAsState()
    val secure by p.secureScreens.collectAsState()
    val version by app.ledger.version.collectAsState()
    var sent by remember { mutableLongStateOf(Ledger.bytesSent()) }
    var wipe by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            sent = Ledger.bytesSent()
            delay(2000)
        }
    }

    HandPage(onBack = onBack, topEnd = { HandLink("how it works", { explain = true }) }) {
        val today = remember(version) { LedgerEntry.entries.map { it to app.ledger.today(it) }.filter { it.second > 0 } }
        val total = remember(version) { LedgerEntry.entries.map { it to app.ledger.total(it) }.filter { it.second > 0 } }

        MarkedText("nothing of yours left this phone.", mark = "nothing", style = Hand.headline.copy(fontSize = 42.sp, lineHeight = 44.sp))
        Spacer(Modifier.height(6.dp))
        Body(
            "Burrow has sent ${formatBytes(context, sent)} since the phone started — requests for model files, nothing else. " +
                "It has no upload code, no account and no analytics." + if (neverOnline) " The network is off." else "",
            color = burrow.pencil,
        )

        Spacer(Modifier.height(22.dp))
        SectionHead("today")
        if (today.isEmpty()) Meta("Nothing yet today.", Modifier.padding(top = 6.dp))
        today.forEach { (e, n) -> LedgerLine(e.line(n)) }

        if (total.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionHead("since you set Burrow up", note = relativeDay(app.ledger.since).lowercase())
            total.forEach { (e, n) -> LedgerLine(e.line(n)) }
        }

        Spacer(Modifier.height(20.dp))
        HandDivider()
        Spacer(Modifier.height(8.dp))
        SwitchRow("never go online", neverOnline, p::setNeverOnline, body = "Burrow won't touch the network, even to fetch a missing model")
        SwitchRow(
            "lock with fingerprint", lockApp,
            { on ->
                if (!on) p.setLockApp(false)
                else if (!Biometrics.deviceSecure(context)) Toast.makeText(context, "Set a screen lock in Android's settings first.", Toast.LENGTH_LONG).show()
                else scope.launch { if (Biometrics.confirm(context, "Lock Burrow with your fingerprint")) p.setLockApp(true) }
            },
            body = "when you come back after 30 seconds away",
        )
        SwitchRow("delete talk audio after 30 days", autoDelete, { on ->
            p.setAutoDeleteAudio(on)
            if (on) scope.launch {
                val n = Housekeeping.deleteOldAudio(app)
                if (n > 0) Toast.makeText(context, "Deleted the audio of $n older talks. Their notes stay.", Toast.LENGTH_LONG).show()
            }
        }, body = "the notes and transcript stay")
        SwitchRow("learn voices when I name someone", learnVoices, p::setLearnVoices, body = "so Burrow can suggest them in later talks")
        SwitchRow("hide from screenshots & recents", secure, p::setSecureScreens, body = "blocks screen capture of Burrow")

        Spacer(Modifier.height(16.dp))
        Door("the models", openModels, meta = "what's on the phone, sizes, the model lab")
        Spacer(Modifier.height(24.dp))
        HandLink("wipe everything", { wipe = true }, Modifier.align(Alignment.CenterHorizontally), color = burrow.redPen)
        Meta("Photos stay in your gallery; Burrow forgets all it learned.", Modifier.align(Alignment.CenterHorizontally))
    }

    if (explain) PrivacyExplainer { explain = false }
    if (wipe) {
        var models by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        HandSheet({ if (!busy) wipe = false }) {
            Text("wipe everything?", style = Hand.headline.copy(fontSize = 34.sp), color = burrow.ink)
            Body(
                "Burrow forgets every photo it checked, every talk and its audio, every person and voice, the vault and its photos, and your settings. " +
                    "Your gallery isn't touched. This can't be undone.",
                color = burrow.pencil,
            )
            SwitchRow("also delete the downloaded models", models, { models = it }, body = "you'd download them again to use Burrow")
            InkButton("wipe it all", {
                busy = true
                scope.launch {
                    Housekeeping.wipeEverything(app, models)
                    busy = false
                    wipe = false
                    onWiped()
                }
            }, Modifier.fillMaxWidth(), danger = true, busy = busy)
            HandLink("keep everything", { wipe = false }, Modifier.align(Alignment.CenterHorizontally), enabled = !busy)
        }
    }
}

@Composable
private fun LedgerLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        DoodleIcon(Doodle.TICK, null, size = 18.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = Hand.label, color = burrow.ink)
    }
}
