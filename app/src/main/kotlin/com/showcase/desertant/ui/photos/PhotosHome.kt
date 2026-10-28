@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.photos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.showcase.desertant.app
import com.showcase.desertant.photos.Album
import com.showcase.desertant.photos.PhotoLibrary
import com.showcase.desertant.photos.PhotoScanWorker
import com.showcase.desertant.photos.PhotoScope
import com.showcase.desertant.ui.common.EmptyState
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.components.prettify

enum class ReviewKind(val title: String) { SIMILAR("Similar shots"), JUNK("Throwaway shots"), SCREENSHOTS("Screenshots") }

private val PERIODS = listOf(30 to "30 days", 90 to "90 days", 365 to "1 year", null to "All time")

fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
fun PhotosHome(open: (String) -> Unit) {
    val context = LocalContext.current
    val insights by context.app.photoInsights.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(hasFullPhotoAccess(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasFullPhotoAccess(context)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PhotoScanWorker.UNIQUE) }
        .collectAsState(initial = emptyList())
    val running = work.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    val done = running?.progress?.getInt(PhotoScanWorker.KEY_DONE, 0) ?: 0
    val total = running?.progress?.getInt(PhotoScanWorker.KEY_TOTAL, 0) ?: 0

    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var album by remember { mutableStateOf<Album?>(null) }
    var days by rememberSaveable { mutableStateOf<Int?>(30) }
    LaunchedEffect(granted) { if (granted) albums = PhotoLibrary.albums(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos") },
                actions = { IconButton(onClick = { open("settings") }) { Icon(Icons.Outlined.Settings, "Settings") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!granted) {
                    EmptyState(
                        Icons.Outlined.PhotoLibrary,
                        "Clean up your gallery, privately",
                        "Burrow looks through your photos on this phone to find bursts and near-duplicates, picks the best shot of each moment, and flags blurry and accidental shots. Nothing is uploaded.",
                    ) {
                        Button(onClick = { permission.launch(photoPermissions()) }) { Text("Allow photo access") }
                    }
                    return@Column
                }

                val i = insights
                val reclaim = (i?.duplicateBytes ?: 0) + (i?.junkBytes ?: 0)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (i == null || i.analyzed == 0) {
                            Text("Scan to see what you can clear", style = MaterialTheme.typography.titleLarge)
                            Text("Start with recent photos — a few hundred take a couple of minutes, and the scan carries on in the background.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("You can free up", style = MaterialTheme.typography.titleMedium)
                            Text(formatBytes(context, reclaim), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${i.duplicateCount} extra shots in ${i.groups.size} similar sets · ${i.junk.size} throwaway shots · ${i.analyzed} photos checked",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                ScanCard(
                    albums = albums, album = album, onAlbum = { album = it },
                    days = days, onDays = { days = it },
                    running = running != null, done = done, total = total,
                    onScan = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        PhotoScanWorker.start(context, PhotoScope(album?.id, album?.name, days))
                    },
                    onStop = { PhotoScanWorker.cancel(context) },
                )

                if (i != null && i.analyzed > 0) {
                    ReviewTile(
                        icon = Icons.Outlined.AutoAwesomeMosaic,
                        title = "Similar shots",
                        subtitle = if (i.groups.isEmpty()) "No near-duplicates found" else "${i.groups.size} moments with extra takes · ${formatBytes(context, i.duplicateBytes)}",
                        previews = i.groups.take(4).map { it.best.uri },
                        enabled = i.groups.isNotEmpty(),
                    ) { open("photos/review/${ReviewKind.SIMILAR.name}") }
                    ReviewTile(
                        icon = Icons.Outlined.BlurOn,
                        title = "Throwaway shots",
                        subtitle = if (i.junk.isEmpty()) "Nothing blurry or accidental" else "${i.junk.size} blurry, accidental or empty · ${formatBytes(context, i.junkBytes)}",
                        previews = i.junk.take(4).map { it.uri },
                        enabled = i.junk.isNotEmpty(),
                    ) { open("photos/review/${ReviewKind.JUNK.name}") }
                    ReviewTile(
                        icon = Icons.Outlined.Screenshot,
                        title = "Screenshots",
                        subtitle = if (i.screenshots.isEmpty()) "None found" else "${i.screenshots.size} screenshots · ${formatBytes(context, i.screenshotBytes)}",
                        previews = i.screenshots.take(4).map { it.uri },
                        enabled = i.screenshots.isNotEmpty(),
                    ) { open("photos/review/${ReviewKind.SCREENSHOTS.name}") }
                    SearchCard(i)
                }
            }
        }
    }
}

private fun hasFullPhotoAccess(context: android.content.Context): Boolean =
    photoPermissions().first().let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ||
        (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)

@Composable
private fun ScanCard(
    albums: List<Album>, album: Album?, onAlbum: (Album?) -> Unit,
    days: Int?, onDays: (Int?) -> Unit,
    running: Boolean, done: Int, total: Int,
    onScan: () -> Unit, onStop: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Scan", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(onClick = { menu = true }, enabled = !running) {
                    Text(album?.let { "${it.name} (${it.count})" } ?: "All albums")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("All albums") }, onClick = { onAlbum(null); menu = false })
                    albums.forEach { a ->
                        DropdownMenuItem(text = { Text("${a.name} (${a.count})") }, onClick = { onAlbum(a); menu = false })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PERIODS.forEach { (d, label) ->
                    FilterChip(selected = days == d, onClick = { onDays(d) }, label = { Text(label) }, enabled = !running)
                }
            }
            if (running) {
                Text(if (total > 0) "Analyzing $done of $total photos — you can leave the app" else "Preparing…", style = MaterialTheme.typography.bodyMedium)
                if (total > 0) LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = onStop) { Text("Stop") }
            } else {
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan ${PhotoScope(album?.id, album?.name, days).label}") }
                Text("Already-scanned photos are skipped, so rescans are quick.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReviewTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    previews: List<String>,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, enabled = enabled) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (enabled) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
            if (previews.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    previews.forEach { Thumbnail(it, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))) }
                    repeat(4 - previews.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SearchCard(insights: com.showcase.desertant.photos.PhotoInsights) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, insights) { insights.search(query) }
    var preview by remember { mutableStateOf<com.showcase.desertant.data.PhotoEntity?>(null) }
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Find photos", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("dog, beach, receipt, smiling…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    insights.topTags.take(14).forEach { (tag, n) ->
                        AssistChip(onClick = { query = tag }, label = { Text("${prettify(tag)} · $n") })
                    }
                }
            } else {
                Text("${results.size} photos", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    results.take(60).forEach { p ->
                        Thumbnail(p.uri, Modifier.size(86.dp).clip(RoundedCornerShape(8.dp)).clickable { preview = p })
                    }
                }
            }
        }
    }
    preview?.let { PhotoPreview(it) { preview = null } }
}
