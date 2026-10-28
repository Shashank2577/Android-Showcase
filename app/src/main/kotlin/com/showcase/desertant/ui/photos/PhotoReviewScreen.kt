@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.showcase.desertant.ui.photos

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.showcase.desertant.app
import com.showcase.desertant.data.PhotoEntity
import com.showcase.desertant.photos.SimilarGroup
import com.showcase.desertant.ui.common.EmptyState
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.common.relativeDay
import kotlinx.coroutines.launch

@Composable
fun PhotoReviewScreen(kind: ReviewKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = context.app.db.photos()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val insights by context.app.photoInsights.collectAsStateWithLifecycle()
    val i = insights

    // The user's own choices win over the suggestion for each photo.
    val overrides = remember { mutableStateMapOf<Long, Boolean>() }
    var preview by remember { mutableStateOf<PhotoEntity?>(null) }
    var pending by remember { mutableStateOf<List<PhotoEntity>>(emptyList()) }

    val groups = i?.groups.orEmpty()
    val list = when (kind) {
        ReviewKind.SIMILAR -> emptyList()
        ReviewKind.JUNK -> i?.junk.orEmpty()
        ReviewKind.SCREENSHOTS -> i?.screenshots.orEmpty()
    }
    fun suggested(p: PhotoEntity): Boolean = when (kind) {
        ReviewKind.SIMILAR -> !p.kept && groups.any { g -> g.extras.any { it.mediaId == p.mediaId } }
        ReviewKind.JUNK -> true
        ReviewKind.SCREENSHOTS -> false
    }
    fun selected(p: PhotoEntity) = overrides[p.mediaId] ?: suggested(p)
    val candidates = if (kind == ReviewKind.SIMILAR) groups.flatMap { it.photos } else list
    val chosen = candidates.filter(::selected)

    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val removed = pending
            scope.launch {
                dao.delete(removed.map { it.mediaId })
                removed.forEach { overrides.remove(it.mediaId) }
                snackbar.showSnackbar("Moved ${removed.size} photos to the trash — recoverable for 30 days.")
            }
        }
        pending = emptyList()
    }

    fun moveToTrash(photos: List<PhotoEntity>) {
        if (photos.isEmpty()) return
        pending = photos
        val request = MediaStore.createTrashRequest(context.contentResolver, photos.map { Uri.parse(it.uri) }, true)
        trash.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kind.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (kind != ReviewKind.SIMILAR && list.isNotEmpty()) {
                        val all = list.all(::selected)
                        TextButton(onClick = { list.forEach { overrides[it.mediaId] = !all } }) { Text(if (all) "Select none" else "Select all") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (chosen.isNotEmpty()) {
                BottomAppBar {
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("${chosen.size} selected", style = MaterialTheme.typography.titleSmall)
                        Text(formatBytes(context, chosen.sumOf { it.sizeBytes }), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { moveToTrash(chosen) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(end = 16.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Move to trash")
                    }
                }
            }
        },
    ) { padding ->
        when {
            i == null -> Unit
            kind == ReviewKind.SIMILAR && groups.isEmpty() || kind != ReviewKind.SIMILAR && list.isEmpty() ->
                EmptyState(Icons.Outlined.TaskAlt, "All tidy", "Nothing left to review here.", Modifier.padding(padding))
            kind == ReviewKind.SIMILAR -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        "Each set is one moment. The best shot is starred and kept; the rest are selected for the trash. Tap to change, hold to look closer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(groups, key = { it.key }) { g ->
                    GroupCard(
                        group = g,
                        selected = ::selected,
                        onToggle = { p -> overrides[p.mediaId] = !selected(p) },
                        onPreview = { preview = it },
                        onKeepAll = {
                            scope.launch { dao.markKept(g.extras.map { it.mediaId }) }
                        },
                    )
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(116.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(list, key = { it.mediaId }) { p ->
                    Column {
                        SelectableTile(p, selected(p), best = false, onToggle = { overrides[p.mediaId] = !selected(p) }, onPreview = { preview = p })
                        Text(
                            p.junkReason ?: relativeDay(p.dateTaken),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    preview?.let { PhotoPreview(it) { preview = null } }
}

@Composable
private fun GroupCard(
    group: SimilarGroup,
    selected: (PhotoEntity) -> Boolean,
    onToggle: (PhotoEntity) -> Unit,
    onPreview: (PhotoEntity) -> Unit,
    onKeepAll: () -> Unit,
) {
    val context = LocalContext.current
    ElevatedCard {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${group.photos.size} similar · ${relativeDay(group.best.dateTaken)}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "★ ${group.bestReason} · frees ${formatBytes(context, group.extras.filter(selected).sumOf { it.sizeBytes })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onKeepAll) { Text("Keep all") }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(group.photos, key = { it.mediaId }) { p ->
                    Box(Modifier.size(132.dp)) {
                        SelectableTile(p, selected(p), best = p.mediaId == group.best.mediaId, onToggle = { onToggle(p) }, onPreview = { onPreview(p) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableTile(p: PhotoEntity, selected: Boolean, best: Boolean, onToggle: () -> Unit, onPreview: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.error, shape) else if (best) Modifier.border(3.dp, Color(0xFFFFC107), shape) else Modifier)
            .combinedClickable(onClick = onToggle, onLongClick = onPreview),
    ) {
        Thumbnail(p.uri, Modifier.fillMaxSize())
        if (selected) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            if (selected) "Selected for trash" else "Keep",
            tint = if (selected) MaterialTheme.colorScheme.error else Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.25f), CircleShape),
        )
        if (best) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                Text(" Best", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
