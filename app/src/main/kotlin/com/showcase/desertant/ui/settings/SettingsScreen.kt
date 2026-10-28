package com.showcase.desertant.ui.settings

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.showcase.desertant.BuildConfig
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.ModelStatus
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.ShowcaseScaffold
import com.showcase.desertant.ui.components.Stat
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, openLab: () -> Unit) {
    val hub = LocalModelHub.current
    val context = LocalContext.current
    val status by hub.status.collectAsState()
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(hub.apiKey) }
    var saved by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var loadedTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(status) { cacheBytes = hub.cacheBytes() }

    ShowcaseScaffold("Settings", onBack) {
        SectionCard("Privacy") {
            Text(
                "Photos, recordings, transcripts and voice profiles are analyzed and stored only on this phone. The app goes online once per model, to download its weights from Hugging Face.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SectionCard("Model lab") {
            Text("Try each Desert Ant model on its own — the building blocks behind Photos, Meetings and People.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = openLab) { Text("Open the model lab") }
        }
        SectionCard("License key") {
            Text(
                "Most apps can use the SDK for free without a key. If Desert Ant Labs gave you a publishable key (pk_live_…), it is sent when a model's license is activated.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; saved = false },
                label = { Text("Publishable API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { hub.apiKey = key; saved = true }) { Text("Save") }
                if (saved) Text("  Saved — applies to the next model load.", style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionCard("Models") {
            Stat("SDK", "ai.desertant ${BuildConfig.DESERT_ANT_SDK_VERSION}")
            Stat("Model cache", Formatter.formatShortFileSize(context, cacheBytes))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { hub.downloadInBackground(*ModelKind.entries.toTypedArray()) }) { Text("Download all") }
                OutlinedButton(onClick = { scope.launch { hub.unloadAll(); loadedTick++ } }) { Text("Unload all") }
            }
            Text(
                "Voz alone is 337 MB — prefer Wi-Fi. Unloading frees a model's memory; its files stay cached.",
                style = MaterialTheme.typography.bodySmall,
            )
            ModelKind.entries.forEach { kind ->
                HorizontalDivider()
                val s = status[kind] ?: ModelStatus.Unknown
                val loaded = remember(loadedTick, s) { hub.isLoaded(kind) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${kind.title} · ${kind.sizeLabel}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when (s) {
                                ModelStatus.Ready -> if (loaded) "On device · loaded in memory" else "On device"
                                ModelStatus.Downloading -> "Downloading…"
                                ModelStatus.NotDownloaded -> "Not downloaded"
                                is ModelStatus.Failed -> "Failed: ${s.message}"
                                ModelStatus.Unknown -> "Checking…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (s is ModelStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        s == ModelStatus.NotDownloaded || s is ModelStatus.Failed ->
                            TextButton(onClick = { hub.downloadInBackground(kind) }) { Text("Download") }
                        loaded && !kind.bundled ->
                            TextButton(onClick = { scope.launch { hub.unload(kind); loadedTick++ } }) { Text("Unload") }
                    }
                }
            }
        }
        SectionCard("About") {
            Text(
                "Burrow runs on-device models by Desert Ant Labs, under the Desert Ant Labs Source-Available License: free for most apps, commercial license at scale.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row {
                TextButton(onClick = { open(context, "https://desertant.com") }) { Text("desertant.com") }
                TextButton(onClick = { open(context, "https://license.desertant.com/") }) { Text("License") }
                TextButton(onClick = { open(context, "https://huggingface.co/desert-ant-labs") }) { Text("Weights") }
            }
        }
    }
}

private fun open(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
