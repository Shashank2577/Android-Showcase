package com.showcase.desertant.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.showcase.desertant.app
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.ModelStatus
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableIntStateOf
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.ItemRow
import com.showcase.desertant.ui.hand.SheetTitle
import com.showcase.desertant.ui.hand.Warning
import com.showcase.desertant.ui.theme.Type
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.SwitchRow
import com.showcase.desertant.ui.photos.photoPermissions
import com.showcase.desertant.ui.settings.SpeechPackList
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow

/** The models Burrow's six jobs use, in the order they're worth having. Tongue is bundled. */
val SETUP_MODELS = listOf(ModelKind.EYE, ModelKind.REDACT, ModelKind.MODERATOR, ModelKind.WHO, ModelKind.EAR, ModelKind.CLEAR, ModelKind.GIST, ModelKind.VOZ)

/** Rough total download, from each model's documented size. */
val SETUP_MB: Int = SETUP_MODELS.sumOf { k -> k.sizeLabel.filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.toInt() ?: 0 }

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    var explain by remember { mutableStateOf(false) }
    HandPage(
        wordmark = true,
        bottom = {
            InkButton("let's start", onStart, Modifier.widthIn(max = 400.dp).fillMaxWidth(), trailing = Doodle.ARROW)
            HandLink("how does privacy work?", { explain = true })
        },
    ) {
        Spacer(Modifier.height(40.dp))
        Text("your phone,", style = Hand.display, color = burrow.ink)
        MarkedText("your notebook.", mark = "notebook", style = Hand.display)
        Spacer(Modifier.height(20.dp))
        Body(
            "Burrow turns talks into notes, remembers who said what, tidies your photos and hides your secrets from AI chats, " +
                "with models that run on this phone. Nothing you give it is uploaded.",
        )
        Spacer(Modifier.height(24.dp))
        Promise(Doodle.LOCK, "Nothing leaves this phone", "No account, no cloud, no tracking.")
        Promise(Doodle.TICK, "It asks first", "Every permission, every delete, every voice.")
        Promise(Doodle.CROSS, "Delete means delete", "One tap forgets a person, a talk, or everything.")
    }
    if (explain) PrivacyExplainer { explain = false }
}

@Composable
private fun Promise(doodle: Doodle, title: String, body: String) {
    ItemRow(title, meta = body, leading = { DoodleIcon(doodle, null, size = 24.dp) })
}

@Composable
fun PrivacyExplainer(onDismiss: () -> Unit) {
    HandSheet(onDismiss) {
        SheetTitle("how privacy works")
        Explain("The models live here", "Burrow downloads its models once, from Hugging Face. After that every photo, voice and word is processed on this phone's own chip.")
        Explain("There's no upload code", "Burrow has no account and no server. You can check: the privacy page shows the bytes it has sent, and a switch cuts the network for good.")
        Explain("The vault is locked", "Private photos are encrypted with a key held in Android's Keystore that only opens with your fingerprint or screen lock, and never go into a cloud backup.")
        Explain("You can forget anything", "Delete a talk, forget a person's voice, or wipe everything Burrow knows in one go.")
        InkButton("got it", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Explain(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = Type.rowTitle, color = burrow.ink)
        Body(body)
    }
}

/**
 * Setup after the welcome, two short pages: "nothing leaves this phone" (the
 * never-go-online switch), then "one download". Permissions are asked when
 * they're first needed: the microphone on record, photos on the photos page,
 * notifications when the first long job starts. The model list and the
 * speech packs for other languages live on the models page.
 */
@Composable
fun AskFirstScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val status by app.hub.status.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var offlineAfter by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { app.hub.refreshAll() }
    val ready = SETUP_MODELS.count { status[it] == ModelStatus.Ready }
    val allReady = ready == SETUP_MODELS.size
    val downloading = SETUP_MODELS.any { status[it] == ModelStatus.Downloading }
    val failed = SETUP_MODELS.firstOrNull { status[it] is ModelStatus.Failed }
    fun finish() {
        // "Never go online" only once the models are here, or the jobs would have nothing to run.
        if (offlineAfter && allReady) app.privacy.setNeverOnline(true)
        app.privacy.setOnboarded(true)
        onDone()
    }

    if (page == 0) {
        HandPage(
            onBack = onBack,
            bottom = { InkButton("next", { page = 1 }, Modifier.widthIn(max = 400.dp).fillMaxWidth(), trailing = Doodle.ARROW) },
        ) {
            PageIntro("nothing leaves this phone", "Burrow has no account and no server. Your talks, photos and people stay here.", doodle = Doodle.LOCK)
            SwitchRow(
                "never go online",
                offlineAfter, { offlineAfter = it },
                body = "Once its models are on the phone, Burrow cuts its own network for good. You can undo this on the privacy page.",
            )
            Spacer(Modifier.height(16.dp))
            Body("Burrow asks for the microphone when you first record, and for your photos when you first open photos.", color = burrow.pencil)
        }
        return
    }

    val size = "$SETUP_MB MB"
    HandPage(
        onBack = { page = 0 },
        bottom = {
            if (allReady || downloading) InkButton("start", ::finish, Modifier.widthIn(max = 400.dp).fillMaxWidth(), trailing = Doodle.ARROW)
            else InkButton(
                if (failed != null) "try again" else "download",
                { app.hub.downloadInBackground(*SETUP_MODELS.toTypedArray()) },
                Modifier.widthIn(max = 400.dp).fillMaxWidth(), trailing = Doodle.ARROW,
            )
            if (!allReady && !downloading) HandLink("later", ::finish)
        },
    ) {
        PageIntro("one download", "Burrow's models, once. After that it works with no internet at all.", doodle = Doodle.SPARK)
        Row(verticalAlignment = Alignment.Bottom) {
            MarkedText(size, mark = size, style = Type.contentTitle)
            Caption("  once, Wi-Fi is best", Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(16.dp))
        when {
            allReady -> Body("All here. Burrow is ready.")
            downloading -> {
                HandProgress(ready / SETUP_MODELS.size.toFloat())
                Caption("$ready of ${SETUP_MODELS.size} models here. You can start using Burrow meanwhile.")
            }
            failed != null -> Warning("${failed.title} didn't download: ${(status[failed] as ModelStatus.Failed).message}")
            else -> Body("Talks in Indian and other languages need a speech pack, from settings → models when you want one.", color = burrow.pencil)
        }
        if (offlineAfter && !allReady) {
            Spacer(Modifier.height(12.dp))
            Caption("“Never go online” turns on if the models are here when you start; otherwise switch it on later on the privacy page.")
        }
    }
}
