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
        PageIntro(doodle = Doodle.SPARK, title = "the models", purpose = "The AI behind Burrow. Each one is downloaded once, then runs on this phone's own chip — your photos, voices and words never go to it over the internet.")
        Meta("${Formatter.formatShortFileSize(context, cacheBytes)} on this phone · ai.desertant ${BuildConfig.DESERT_ANT_SDK_VERSION}")
        if (neverOnline) Body("“Never go online” is on, so missing models can't be downloaded. Turn it off on the privacy page to fetch them once.", Modifier.padding(top = 8.dp), color = burrow.redPen)
        Spacer(Modifier.height(10.dp))
        val missing = ModelKind.entries.filter { status[it] == ModelStatus.NotDownloaded || status[it] is ModelStatus.Failed }
        if (missing.isNotEmpty() && !neverOnline) {
            PillButton("download the ${missing.size} missing", { hub.downloadInBackground(*missing.toTypedArray()) })
            Spacer(Modifier.height(8.dp))
        }

        ModelKind.entries.forEachIndexed { n, kind ->
            if (n > 0) HandDivider(seed = n)
            val s = status[kind] ?: ModelStatus.Unknown
            val loaded = remember(loadedTick, s) { hub.isLoaded(kind) }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${kind.title.lowercase()} · ${kind.sizeLabel}", style = Hand.label, color = burrow.ink)
                    Meta(JOBS[kind] ?: kind.tagline)
                    Meta(
                        when (s) {
                            ModelStatus.Ready -> if (kind.bundled) "built into the app" else if (loaded) "on the phone · in memory now" else "on the phone"
                            ModelStatus.Downloading -> "downloading…"
                            ModelStatus.NotDownloaded -> "not downloaded yet"
                            is ModelStatus.Failed -> "didn't download: ${s.message}"
                            ModelStatus.Unknown -> "checking…"
                        },
                        color = if (s is ModelStatus.Failed) burrow.redPen else if (s == ModelStatus.Ready) burrow.ink else burrow.pencil,
                    )
                }
                Spacer(Modifier.width(10.dp))
                when {
                    (s == ModelStatus.NotDownloaded || s is ModelStatus.Failed) && !neverOnline ->
                        PillButton("download", { hub.downloadInBackground(kind) })
                    loaded && !kind.bundled -> PillButton("free memory", { scope.launch { hub.unload(kind); loadedTick++ } })
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHead("languages", note = "for talks")
        SpeechPackList()

        Spacer(Modifier.height(18.dp))
        SectionHead("questions", note = "for ask Burrow")
        AskModelRow()

        Spacer(Modifier.height(18.dp))
        Door("the model lab", openLab, meta = "try each model on its own, outside Burrow's jobs")

        Spacer(Modifier.height(18.dp))
        SectionHead("license key", note = "optional")
        Meta("Desert Ant models are free for most apps. If you were given a publishable key (pk_live_…), it's sent when a model's license is activated.")
        Spacer(Modifier.height(8.dp))
        HandField(key, { key = it; saved = false }, placeholder = "pk_live_…", singleLine = true)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("save key", { hub.apiKey = key; saved = true })
            if (saved) Meta("saved — used from the next model load")
        }

        Spacer(Modifier.height(18.dp))
        SectionHead("about")
        Meta("Models by Desert Ant Labs, under the Desert Ant Labs Source-Available License. Indian-language speech: AI4Bharat's IndicConformer (MIT), exported for phones by the sherpa-onnx community (Apache-2.0). Other languages: Meta's Omnilingual ASR (Apache-2.0).")
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
