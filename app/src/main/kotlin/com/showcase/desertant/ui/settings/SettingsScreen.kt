package com.showcase.desertant.ui.settings

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.showcase.desertant.BuildConfig
import com.showcase.desertant.app
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.ModelStatus
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.ItemRow
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Warning
import com.showcase.desertant.ui.theme.Type
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

/** What each model does for Burrow, in the user's words rather than the model's. */
private val JOBS = mapOf(
    ModelKind.EYE to "looks at photos: moments, best shots, private things",
    ModelKind.MODERATOR to "double-checks photos that might be intimate",
    ModelKind.REDACT to "finds names and numbers to mask",
    ModelKind.VOZ to "writes down what was said (25 languages)",
    ModelKind.EAR to "recognises which language a talk is in",
    ModelKind.WHO to "works out who spoke when",
    ModelKind.CLEAR to "cleans up noisy audio",
    ModelKind.GIST to "finds the topics of a talk",
    ModelKind.TONGUE to "recognises the language of a text",
    ModelKind.EMO to "suggests emoji (model lab only)",
    ModelKind.SHAPES to "turns scribbles into shapes (model lab only)",
)

@Composable
fun SettingsScreen(onBack: () -> Unit, openLab: () -> Unit) {
    val hub = LocalModelHub.current
    val context = LocalContext.current
    val app = context.app
    val status by hub.status.collectAsState()
    val neverOnline by app.privacy.neverOnline.collectAsState()
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(hub.apiKey) }
    var saved by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var loadedTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { hub.refreshAll() }
    LaunchedEffect(status) { cacheBytes = hub.cacheBytes() }

    HandPage(onBack = onBack) {
        PageIntro(doodle = Doodle.SPARK, title = "models", purpose = "The AI behind Burrow. Each one is downloaded once, then runs on this phone's own chip.")
        val onPhone = Formatter.formatShortFileSize(context, cacheBytes)
        Row(verticalAlignment = Alignment.Bottom) {
            MarkedText(onPhone, mark = onPhone, style = Type.contentTitle)
            Caption("  on this phone · ai.desertant ${BuildConfig.DESERT_ANT_SDK_VERSION}", Modifier.padding(bottom = 3.dp))
        }
        if (neverOnline) Warning("“Never go online” is on, so missing models can't be downloaded. Turn it off on the privacy page to fetch them once.", Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(12.dp))
        val missing = ModelKind.entries.filter { status[it] == ModelStatus.NotDownloaded || status[it] is ModelStatus.Failed }
        // Past three missing, one pill fetches them all and each row gets a quiet link instead of its own button.
        val many = missing.size > 3
        if (many && !neverOnline) {
            val mb = missing.sumOf { k -> k.sizeLabel.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0 }.toInt()
            PillButton("download all ($mb MB)", { hub.downloadInBackground(*missing.toTypedArray()) }, leading = Doodle.PLUS)
            Spacer(Modifier.height(8.dp))
        }

        ModelKind.entries.forEachIndexed { n, kind ->
            if (n > 0) HandDivider(seed = n)
            val s = status[kind] ?: ModelStatus.Unknown
            val loaded = remember(loadedTick, s) { hub.isLoaded(kind) }
            val canDownload = (s == ModelStatus.NotDownloaded || s is ModelStatus.Failed) && !neverOnline
            ItemRow(
                kind.title,
                meta = JOBS[kind] ?: kind.tagline,
                trailing = { Caption(kind.sizeLabel) },
                below = {
                    Spacer(Modifier.height(6.dp))
                    when {
                        s is ModelStatus.Failed -> Warning("It didn't download: ${s.message}")
                        s == ModelStatus.Ready -> Caption(if (kind.bundled) "Built into the app" else if (loaded) "On this phone · in memory now" else "On this phone")
                        s == ModelStatus.Downloading -> Caption("Downloading…")
                        s == ModelStatus.Unknown -> Caption("Checking…")
                        !canDownload -> Caption("Not downloaded yet")
                    }
                    when {
                        canDownload && many -> HandLink("download", { hub.downloadInBackground(kind) })
                        canDownload -> PillButton("download", { hub.downloadInBackground(kind) }, Modifier.padding(top = 4.dp))
                        loaded && !kind.bundled -> HandLink("free memory", { scope.launch { hub.unload(kind); loadedTick++ } })
                    }
                },
            )
        }

        SectionHead("languages", note = "For talks.")
        SpeechPackList()

        SectionHead("questions", note = "For ask Burrow and summaries.")
        AskModelRow()

        SectionHead("more")
        Door("the model lab", openLab, meta = "Try each model on its own, outside Burrow's jobs.")

        SectionHead("license key", note = "Optional.")
        Body("Desert Ant models are free for most apps. If you were given a publishable key (pk_live_…), it's sent when a model's license is activated.")
        Spacer(Modifier.height(8.dp))
        HandField(key, { key = it; saved = false }, placeholder = "pk_live_…", singleLine = true)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("save key", { hub.apiKey = key; saved = true })
            if (saved) Caption("Saved. Used from the next model load.")
        }

        SectionHead("about")
        Body("Models by Desert Ant Labs, under the Desert Ant Labs Source-Available License. Indian-language speech: AI4Bharat's IndicConformer (MIT), exported for phones by the sherpa-onnx community (Apache-2.0). Other languages: Meta's Omnilingual ASR (Apache-2.0).")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HandLink("desertant.com", { open(context, "https://desertant.com") })
            HandLink("license", { open(context, "https://license.desertant.com/") })
            HandLink("ai4bharat", { open(context, "https://ai4bharat.iitm.ac.in/") })
        }
    }
}

private fun open(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
