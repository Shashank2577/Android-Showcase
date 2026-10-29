package com.showcase.desertant.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import com.showcase.desertant.ui.hand.SwitchRow
import com.showcase.desertant.ui.photos.photoPermissions
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
    var photos by remember { mutableStateOf(granted(photoPermissions().first())) }
    var mic by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var notify by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) }
    var offlineAfter by rememberSaveable { mutableStateOf(true) }

    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { photos = granted(photoPermissions().first()) || it.values.any { v -> v } }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notify = it }

    LaunchedEffect(Unit) { app.hub.refreshAll() }
    val ready = SETUP_MODELS.count { status[it] == ModelStatus.Ready }
    val downloading = SETUP_MODELS.any { status[it] == ModelStatus.Downloading }
    val failed = SETUP_MODELS.firstOrNull { status[it] is ModelStatus.Failed }

    HandPage(
        onBack = onBack,
        bottom = {
            InkButton(
                "start",
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
        Spacer(Modifier.height(8.dp))
        MarkedText("you decide what Burrow sees.", mark = "you decide", style = Hand.headline.copy(fontSize = 44.sp, lineHeight = 46.sp))
        Spacer(Modifier.height(8.dp))
        Meta("Each switch asks Android for one thing. You can change any of them later.")
        Spacer(Modifier.height(20.dp))
        SwitchRow("your photos", photos, { if (it) askPhotos.launch(photoPermissions()) }, body = "to tidy them and keep private ones safe", doodle = Doodle.EYE)
        SwitchRow("the microphone", mic, { if (it) askMic.launch(Manifest.permission.RECORD_AUDIO) }, body = "only while you press record", doodle = Doodle.MIC)
        SwitchRow(
            "a quiet notification", notify,
            { if (it && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) },
            body = "when a scan or a talk's notes are done", doodle = Doodle.SPARK,
        )
        Spacer(Modifier.height(18.dp))
        HandDivider()
        Spacer(Modifier.height(14.dp))
        Text("one-time setup", style = Hand.title, color = burrow.ink)
        Body(
            "Download the models (about $SETUP_MB MB — Wi-Fi is best). Then Burrow can work with no internet at all.",
            color = burrow.pencil,
        )
        Spacer(Modifier.height(12.dp))
        HandProgress(if (ready == 0 && !downloading) null else ready / SETUP_MODELS.size.toFloat())
        Spacer(Modifier.height(6.dp))
        Meta(
            SETUP_MODELS.joinToString("  ·  ") { k ->
                val s = status[k]
                k.title.lowercase() + when {
                    s == ModelStatus.Ready -> " ✓"
                    s == ModelStatus.Downloading -> " …"
                    s is ModelStatus.Failed -> " ✗"
                    else -> ""
                }
            },
        )
        failed?.let { Meta("${it.title} didn't download: ${(status[it] as ModelStatus.Failed).message}", color = burrow.redPen) }
        Spacer(Modifier.height(6.dp))
        if (ready < SETUP_MODELS.size && !downloading) {
            HandLink(if (ready == 0) "download now" else "download the rest", { app.hub.downloadInBackground(*SETUP_MODELS.toTypedArray()) })
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow("then never go online", offlineAfter, { offlineAfter = it }, body = "Burrow cuts its own network once the models are here")
    }
}
