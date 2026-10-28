@file:OptIn(ExperimentalMaterial3Api::class)

package com.showcase.desertant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.ModelStatus

/** Content column width cap: comfortable on a phone, centered on a Fold's inner screen. */
private val MAX_CONTENT_WIDTH = 720.dp

@Composable
fun ShowcaseScaffold(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
            )
        },
    ) { padding ->
        // On a wide screen (a Fold's inner display, a tablet, landscape) keep a
        // readable column centered rather than stretching cards edge to edge.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

/** A model's playground: its description and download state, then [content]. */
@Composable
fun ModelScreen(
    kind: ModelKind,
    onBack: () -> Unit,
    runner: Runner? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ShowcaseScaffold(title = kind.title, onBack = onBack) {
        ModelHeader(kind)
        content()
        runner?.let { RunFooter(it) }
    }
}

@Composable
fun ModelHeader(kind: ModelKind) {
    val hub = LocalModelHub.current
    val status by hub.status.collectAsState()
    LaunchedEffect(kind) { hub.refresh(kind) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(kind.tagline, style = MaterialTheme.typography.titleMedium)
            Text(kind.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${kind.artifact} · ${kind.sizeLabel}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
            )
            ModelStatusRow(kind, status[kind] ?: ModelStatus.Unknown) { hub.downloadInBackground(kind) }
        }
    }
}

@Composable
fun ModelStatusRow(kind: ModelKind, status: ModelStatus, onDownload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            ModelStatus.Ready -> {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (kind.bundled) "Bundled in the app — always offline" else "On device — works offline", style = MaterialTheme.typography.labelLarge)
            }
            ModelStatus.Downloading -> {
                Column(Modifier.fillMaxWidth()) {
                    Text("Downloading ${kind.sizeLabel} from Hugging Face…", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.size(4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            ModelStatus.NotDownloaded, is ModelStatus.Failed -> {
                if (status is ModelStatus.Failed) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(status.message, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                } else {
                    Text("Not downloaded yet", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                }
                FilledTonalButton(onClick = onDownload) {
                    Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (status is ModelStatus.Failed) "Retry" else "Download")
                }
            }
            ModelStatus.Unknown -> {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking…", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun RunFooter(runner: Runner) {
    runner.error?.let { ErrorCard(it) }
    if (runner.busy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Running on device…", style = MaterialTheme.typography.labelLarge)
        }
    } else {
        runner.lastMillis?.let {
            AssistChip(onClick = {}, label = { Text("Last run: $it ms on device") })
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
