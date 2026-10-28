@file:OptIn(ExperimentalMaterial3Api::class)

package com.showcase.desertant.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.showcase.desertant.core.ModelCategory
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.ModelStatus
import com.showcase.desertant.core.Pipeline
import com.showcase.desertant.ui.components.LocalModelHub

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val status by hub.status.collectAsState()
    LaunchedEffect(Unit) { hub.refreshAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model lab") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Every model on its own", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Try each Desert Ant model directly — the building blocks behind Photos, Meetings and People. Everything runs on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val ready = ModelKind.entries.count { status[it] == ModelStatus.Ready }
                        Text("$ready of ${ModelKind.entries.size} models on device", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Header("Pipelines — models working together") }
            items(Pipeline.entries, span = { GridItemSpan(maxLineSpan) }) { p ->
                ElevatedCard(onClick = { onOpen(p.route) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(p.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(p.title, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(p.description, style = MaterialTheme.typography.bodySmall)
                        Text(p.models.joinToString(" → ") { it.title }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            ModelCategory.entries.forEach { category ->
                item(span = { GridItemSpan(maxLineSpan) }) { Header(category.title) }
                items(ModelKind.entries.filter { it.category == category }) { kind ->
                    ModelCard(kind, status[kind] ?: ModelStatus.Unknown) { onOpen(kind.route) }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ModelCard(kind: ModelKind, status: ModelStatus, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp).height(128.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kind.icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                when (status) {
                    ModelStatus.Ready -> Icon(Icons.Filled.CheckCircle, "On device", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    ModelStatus.Downloading -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else -> Icon(Icons.Outlined.CloudDownload, "Not downloaded", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                }
            }
            Text(kind.title, style = MaterialTheme.typography.titleMedium)
            Text(kind.tagline, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text(kind.sizeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
