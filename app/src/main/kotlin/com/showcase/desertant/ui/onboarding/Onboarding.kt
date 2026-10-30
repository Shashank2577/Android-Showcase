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
import com.showcase.desertant.ui.hand.Body
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
import com.showcase.desertant.ui.settings.IndicLanguages
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
            InkButton("let's start", onStart, Modifier.fillMaxWidth(), trailing = Doodle.ARROW)
            HandLink("how does privacy work?", { explain = true }, Modifier.align(Alignment.CenterHorizontally))
        },
    ) {
        Spacer(Modifier.height(48.dp))
        Text("your phone,", style = Hand.display, color = burrow.ink)
        MarkedText("your notebook.", mark = "notebook", style = Hand.display)
        Spacer(Modifier.height(20.dp))
        Body(
            "Burrow tidies your photos, turns talks into notes, remembers who said what, and hides your secrets from AI chats — " +
                "with models that run on this phone. Nothing you give it is uploaded.",
            color = burrow.pencil,
        )
        Spacer(Modifier.height(36.dp))
        Promise(Doodle.LOCK, "nothing leaves this phone", "no account, no cloud, no tracking")
        Promise(Doodle.TICK, "it asks first", "every permission, every delete, every voice")
        Promise(Doodle.CROSS, "delete means delete", "one tap forgets a person, a talk, or everything")
    }
    if (explain) PrivacyExplainer { explain = false }
}

@Composable
private fun Promise(doodle: Doodle, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        DoodleIcon(doodle, null, Modifier.padding(top = 4.dp), size = 24.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = Hand.door, color = burrow.ink)
            Meta(body)
        }
    }
}

@Composable
fun PrivacyExplainer(onDismiss: () -> Unit) {
    HandSheet(onDismiss) {
        Text("how privacy works", style = Hand.headline, color = burrow.ink)
        Explain("the models live here", "Burrow downloads its models once, from Hugging Face. After that every photo, voice and word is processed on this phone's own chip.")
        Explain("there's no upload code", "Burrow has no account and no server. You can check: the privacy page shows the bytes it has sent, and a switch cuts the network for good.")
        Explain("the vault is locked", "Private photos are encrypted with a key held in Android's Keystore that only opens with your fingerprint or screen lock, and never go into a cloud backup.")
        Explain("you can forget anything", "Delete a talk, forget a person's voice, or wipe everything Burrow knows in one go.")
        InkButton("got it", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Explain(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = Hand.door, color = burrow.ink)
        Body(body, color = burrow.pencil)
    }
}

/** Step two: each permission, with why; and the one-time model download. */
@Composable
fun AskFirstScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val status by app.hub.status.collectAsState()
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var photos by remember { mutableStateOf(photoPermissions().any { granted(it) }) }
    var mic by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var notify by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) }
    var offlineAfter by rememberSaveable { mutableStateOf(true) }
    // After Android stops showing its dialog, the only way left is the app's settings page.
    var refused by remember { mutableStateOf(setOf<String>()) }

    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        photos = photoPermissions().any { p -> granted(p) }
        if (!photos) refused = refused + "photos"
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it; if (!it) refused = refused + "mic" }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notify = it; if (!it) refused = refused + "notify" }
    fun openSettings() = runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    LaunchedEffect(Unit) { app.hub.refreshAll() }
    val ready = SETUP_MODELS.count { status[it] == ModelStatus.Ready }
    val downloading = SETUP_MODELS.any { status[it] == ModelStatus.Downloading }
    val failed = SETUP_MODELS.firstOrNull { status[it] is ModelStatus.Failed }

    HandPage(
        onBack = onBack,
        bottom = {
            InkButton(
                if (ready == SETUP_MODELS.size) "start" else "start — download later",
                {
                    // "Never go online" only once the models are here, or the jobs would have nothing to run.
                    if (offlineAfter && ready == SETUP_MODELS.size) app.privacy.setNeverOnline(true)
                    app.privacy.setOnboarded(true)
                    onDone()
                },
                Modifier.fillMaxWidth(),
                trailing = Doodle.ARROW,
            )
        },
    ) {
        PageIntro(
            "you decide what Burrow sees.",
            "Allow only what you want to use. Each one asks Android once; you can change it later in Android's settings.",
            mark = "you decide",
        )
        Permission("your photos", "to tidy them and keep private ones safe", Doodle.EYE, photos, "photos" in refused, { askPhotos.launch(photoPermissions()) }, ::openSettings)
        Permission("the microphone", "only while you press record", Doodle.MIC, mic, "mic" in refused, { askMic.launch(Manifest.permission.RECORD_AUDIO) }, ::openSettings)
        if (Build.VERSION.SDK_INT >= 33) {
            Permission("a quiet notification", "when a look-through or a talk's notes are done", Doodle.SPARK, notify, "notify" in refused, { askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }, ::openSettings)
        }
        Spacer(Modifier.height(18.dp))
        HandDivider()
        Spacer(Modifier.height(14.dp))
        SectionHead("one-time setup", note = "about $SETUP_MB MB")
        Body("Download the models once — Wi-Fi is best. After that Burrow works with no internet at all.", color = burrow.pencil)
        Spacer(Modifier.height(12.dp))
        HandProgress(if (ready == 0 && !downloading) null else ready / SETUP_MODELS.size.toFloat())
        Spacer(Modifier.height(6.dp))
        Meta(
            SETUP_MODELS.joinToString("  ·  ") { k ->
                val st = status[k]
                k.title.lowercase() + when {
                    st == ModelStatus.Ready -> " ✓"
                    st == ModelStatus.Downloading -> " …"
                    st is ModelStatus.Failed -> " ✗"
                    else -> ""
                }
            },
        )
        failed?.let { Meta("${it.title} didn't download: ${(status[it] as ModelStatus.Failed).message}", color = burrow.redPen) }
        Spacer(Modifier.height(8.dp))
        when {
            ready == SETUP_MODELS.size -> Meta("All here. ✓")
            downloading -> Meta("Downloading — you can start using Burrow meanwhile.")
            else -> PillButton(if (ready == 0) "download the models" else "download the rest", { app.hub.downloadInBackground(*SETUP_MODELS.toTypedArray()) }, leading = Doodle.PLUS)
        }
        Spacer(Modifier.height(18.dp))
        SectionHead("talks in Hindi?", note = "optional")
        IndicLanguages()
        Spacer(Modifier.height(8.dp))
        SwitchRow("then never go online", offlineAfter, { offlineAfter = it }, body = "Once the models are here, Burrow cuts its own network for good. You can undo this on the privacy page.")
    }
}

/** One permission: what it's for, and either "allowed ✓" or a button that asks (or opens settings if Android won't ask again). */
@Composable
private fun Permission(title: String, why: String, doodle: Doodle, allowed: Boolean, refused: Boolean, ask: () -> Unit, settings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        DoodleIcon(doodle, null, size = 26.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Hand.door.copy(fontSize = 24.sp), color = burrow.ink)
            Meta(why)
        }
        Spacer(Modifier.width(10.dp))
        when {
            allowed -> Text("allowed ✓", style = Hand.small, color = burrow.ink)
            refused -> PillButton("settings", settings)
            else -> PillButton("allow", ask)
        }
    }
}
