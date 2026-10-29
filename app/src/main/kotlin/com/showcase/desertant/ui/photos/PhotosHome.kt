@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.photos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.showcase.desertant.app
import com.showcase.desertant.data.PhotoEntity
import com.showcase.desertant.photos.Album
import com.showcase.desertant.photos.PhotoInsights
import com.showcase.desertant.photos.PhotoLibrary
import com.showcase.desertant.photos.PhotoScanWorker
import com.showcase.desertant.photos.PhotoScope
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.components.prettify
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.HandTag
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.Snapshot
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow

enum class ReviewKind(val title: String) { SIMILAR("keep the best"), JUNK("throwaway shots"), SCREENSHOTS("screenshots") }

private val PERIODS = listOf(30 to "30 days", 90 to "90 days", 365 to "a year", null to "everything")

fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
fun PhotosHome(open: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val insights by app.photoInsights.collectAsStateWithLifecycle()
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

    HandPage(topEnd = { DoodleButton(Doodle.LOCK, "Privacy", { open("privacy") }) }) {
        Text("photos", style = Hand.headline, color = burrow.ink)
        val i = insights
        Meta(if (i == null || i.analyzed == 0) "looked at on this phone, never uploaded" else "${i.analyzed} photos checked · on this phone")
        Spacer(Modifier.height(24.dp))

        if (!granted) {
            Body(
                "Burrow looks through your photos on this phone: it groups bursts and retakes and keeps the best shot, " +
                    "flags blurry and accidental ones, and finds IDs, cards and other private photos sitting in plain sight.",
                color = burrow.pencil,
            )
            Spacer(Modifier.height(20.dp))
            InkButton("let Burrow see your photos", { permission.launch(photoPermissions()) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Door("the vault", { open("vault") }, meta = "your encrypted, fingerprint-locked photos")
            return@HandPage
        }

        val reclaim = (i?.duplicateBytes ?: 0) + (i?.junkBytes ?: 0)
        if (i != null && i.analyzed > 0) {
            if (reclaim > 0) {
                val size = formatBytes(context, reclaim)
                MarkedText("you can free up $size", mark = size, style = Hand.headline.copy(fontSize = 38.sp))
                Spacer(Modifier.height(4.dp))
                Meta("${i.duplicateCount} extra takes in ${i.groups.size} moments · ${i.junk.size} throwaway shots")
            } else {
                MarkedText("nothing to tidy.", mark = "nothing", style = Hand.headline.copy(fontSize = 38.sp))
            }
            Spacer(Modifier.height(16.dp))
            Door(
                "moments — keep the best", if (i.groups.isEmpty()) null else ({ open("photos/review/${ReviewKind.SIMILAR.name}") }),
                meta = if (i.groups.isEmpty()) "no bursts or retakes found" else "${i.groups.size} moments with extra takes · ${formatBytes(context, i.duplicateBytes)}",
            )
            Previews(i.groups.take(5).map { it.best })
            Door(
                "throwaway shots", if (i.junk.isEmpty()) null else ({ open("photos/review/${ReviewKind.JUNK.name}") }),
                meta = if (i.junk.isEmpty()) "nothing blurry or accidental" else "${i.junk.size} blurry, accidental or empty · ${formatBytes(context, i.junkBytes)}",
            )
            Door(
                "screenshots that did their job", if (i.screenshots.isEmpty()) null else ({ open("photos/review/${ReviewKind.SCREENSHOTS.name}") }),
                meta = if (i.screenshots.isEmpty()) "none found" else "${i.screenshots.size} screenshots · ${formatBytes(context, i.screenshotBytes)}",
            )
            HandDivider(Modifier.padding(vertical = 8.dp), seed = 4)
            Door(
                "private things — ${i.privateCount}", if (i.privateCount == 0) null else ({ open("sweep") }),
                alert = i.privateCount > 0,
                meta = if (i.privateCount == 0) "no IDs, cards or intimate photos in plain sight" else "anyone scrolling your gallery could see these",
            )
            if (i.privateCount > 0) Previews(i.private.values.flatten().take(5), blurred = true)
        }
        Door("the vault — ${app.vault.count}", { open("vault") }, meta = "encrypted on this phone, opens with your fingerprint")

        Spacer(Modifier.height(20.dp))
        SectionHead("look through")
        if (running != null) {
            Spacer(Modifier.height(6.dp))
            Body(if (total > 0) "checked $done of $total — you can leave the app" else "getting ready…")
            Spacer(Modifier.height(8.dp))
            HandProgress(if (total > 0) done.toFloat() / total else null)
            HandLink("stop", { PhotoScanWorker.cancel(context) })
        } else {
            AlbumPicker(albums, album) { album = it }
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PERIODS.forEach { (d, label) -> HandTag(label, marked = days == d, onClick = { days = d }) }
            }
            InkButton(
                if (days == null) "look through everything" else "look through the last ${PERIODS.first { it.first == days }.second}",
                {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    PhotoScanWorker.start(context, PhotoScope(album?.id, album?.name, days))
                },
                Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Meta("Photos already checked are skipped, so it's quick the second time.", Modifier.padding(top = 8.dp))
        }

        if (i != null && i.analyzed > 0) {
            Spacer(Modifier.height(24.dp))
            Find(i)
        }
    }
}

@Composable
private fun Previews(photos: List<PhotoEntity>, blurred: Boolean = false) {
    if (photos.isEmpty()) return
    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        photos.forEachIndexed { n, p ->
            Snapshot(tilt = listOf(-3f, 2f, -1.5f, 3f, -2f)[n % 5]) {
                Thumbnail(p.uri, Modifier.size(52.dp).then(if (blurred) Modifier.blur(8.dp) else Modifier), sizePx = if (blurred) 20 else 160)
            }
        }
    }
}

@Composable
private fun AlbumPicker(albums: List<Album>, album: Album?, onAlbum: (Album?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        HandLink(album?.let { "${it.name.lowercase()} (${it.count})" } ?: "all albums", { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("All albums") }, onClick = { onAlbum(null); menu = false })
            albums.forEach { a -> DropdownMenuItem(text = { Text("${a.name} (${a.count})") }, onClick = { onAlbum(a); menu = false }) }
        }
    }
}

private fun hasFullPhotoAccess(context: android.content.Context): Boolean =
    photoPermissions().first().let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ||
        (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)

@Composable
private fun Find(insights: PhotoInsights) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, insights) { insights.search(query) }
    var preview by remember { mutableStateOf<PhotoEntity?>(null) }
    SectionHead("find a photo")
    Spacer(Modifier.height(8.dp))
    HandField(query, { query = it }, placeholder = "dog, beach, receipt, smiling…", singleLine = true, leading = Doodle.SEARCH)
    Spacer(Modifier.height(10.dp))
    if (query.isBlank()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            insights.topTags.take(14).forEach { (tag, n) -> HandTag("${prettify(tag).lowercase()} $n", onClick = { query = tag }) }
        }
    } else {
        Meta("${results.size} photos")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            results.take(60).forEach { p -> Thumbnail(p.uri, Modifier.size(84.dp).clickable { preview = p }) }
        }
    }
    preview?.let { PhotoPreview(it) { preview = null } }
}
