@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.photos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.ChoicePill
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.ItemRow
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.hand.Door
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandProgress
import com.showcase.desertant.ui.hand.HandTag
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.SectionHead
import com.showcase.desertant.ui.hand.Snapshot
import com.showcase.desertant.ui.hand.Steps
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
fun PhotosHome(onBack: () -> Unit, open: (String) -> Unit) {
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

    val i = insights
    val scanned = i != null && i.analyzed > 0
    val startScan = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        PhotoScanWorker.start(context, PhotoScope(album?.id, album?.name, days))
    }

    HandPage(onBack = onBack) {
        PageIntro("photos", "Free up space and keep private photos out of sight, checked on this phone.", doodle = Doodle.CAMERA)

        if (!granted) {
            Steps(listOf("Let Burrow see your photos", "It looks through them, on the phone", "You decide what to let go and what to lock away"), current = 0)
            Spacer(Modifier.height(20.dp))
            InkButton("let Burrow see them", { permission.launch(photoPermissions()) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Door("the vault", { open("vault") }, meta = vaultMeta(app.vault.count), leading = { DoodleIcon(Doodle.LOCK, null, size = 24.dp) })
            return@HandPage
        }

        // What's happening right now comes first.
        if (running != null) {
            Body("Looking through your photos…")
            Caption(if (total > 0) "$done of $total checked. You can leave the app; it keeps going." else "Getting ready…")
            HandProgress(if (total > 0) done.toFloat() / total else null, Modifier.padding(vertical = 6.dp))
            PillButton("stop", { PhotoScanWorker.cancel(context) }, leading = Doodle.STOP)
            Spacer(Modifier.height(18.dp))
        }

        if (!scanned) {
            if (running == null) {
                Body("Burrow hasn't looked through your photos yet. Pick how far back to go, then start.")
                Spacer(Modifier.height(10.dp))
                ScanChoices(albums, album, { album = it }, days) { days = it }
                InkButton(scanLabel(days), startScan, Modifier.fillMaxWidth().padding(top = 8.dp))
                Caption("A few hundred photos take a couple of minutes.", Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
            Door("the vault", { open("vault") }, meta = vaultMeta(app.vault.count), leading = { DoodleIcon(Doodle.LOCK, null, size = 24.dp) })
            return@HandPage
        }

        val reclaim = i!!.duplicateBytes + i.junkBytes
        val reclaimText = formatBytes(context, reclaim)
        SectionHead("tidy up", note = if (reclaim > 0) "$reclaimText you can free." else "All tidy.", first = true)
        if (i.groups.isNotEmpty()) {
            ItemRow(
                "Keep the best of each moment",
                meta = "${i.groups.size} moments with ${i.duplicateCount} extra takes · ${formatBytes(context, i.duplicateBytes)}",
                onClick = { open("photos/review/${ReviewKind.SIMILAR.name}") },
            )
            Previews(i.groups.take(5).map { it.best })
        }
        if (i.junk.isNotEmpty()) ItemRow(
            "Throwaway shots",
            meta = "${i.junk.size} blurry, accidental or empty · ${formatBytes(context, i.junkBytes)}",
            onClick = { open("photos/review/${ReviewKind.JUNK.name}") },
        )
        if (i.screenshots.isNotEmpty()) ItemRow(
            "Old screenshots",
            meta = "${i.screenshots.size} screenshots · ${formatBytes(context, i.screenshotBytes)}",
            onClick = { open("photos/review/${ReviewKind.SCREENSHOTS.name}") },
        )
        if (i.groups.isEmpty() && i.junk.isEmpty() && i.screenshots.isEmpty()) Body("No bursts, retakes, blurry shots or old screenshots found.", color = burrow.pencil)

        Spacer(Modifier.height(16.dp))
        HandDivider(seed = 7)
        Door(
            "private sweep",
            if (i.privateCount == 0) null else ({ open("sweep") }),
            alert = i.privateCount > 0,
            meta = if (i.privateCount == 0) "No IDs, cards, medical or intimate photos in plain sight." else "${i.privateCount} private photos in plain sight. Move them to the vault.",
            leading = { DoodleIcon(Doodle.EYE_OFF, null, size = 24.dp) },
        )
        if (i.privateCount > 0) Previews(i.private.values.flatten().take(5), blurred = true)
        Door("the vault", { open("vault") }, meta = vaultMeta(app.vault.count), leading = { DoodleIcon(Doodle.LOCK, null, size = 24.dp) })

        SectionHead("look through more", note = "${i.analyzed} checked. Photos already checked are skipped, so this is quick.")
        if (running == null) {
            ScanChoices(albums, album, { album = it }, days) { days = it }
            PillButton(scanLabel(days), startScan, Modifier.padding(top = 4.dp), leading = Doodle.SEARCH)
        } else {
            Body("A look-through is running; see the top of the page.", color = burrow.pencil)
        }

        Find(i)
    }
}

private fun scanLabel(days: Int?) = if (days == null) "look through all my photos" else "look through the last ${PERIODS.first { it.first == days }.second}"

@Composable
private fun ScanChoices(albums: List<Album>, album: Album?, onAlbum: (Album?) -> Unit, days: Int?, onDays: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Meta("from ")
        AlbumPicker(albums, album, onAlbum)
    }
    FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PERIODS.forEach { (d, label) -> ChoicePill(label, days == d, { onDays(d) }) }
    }
}

private fun vaultMeta(count: Int) = if (count == 0) "Encrypted on this phone, opens with your fingerprint." else "$count photos, encrypted on this phone, open with your fingerprint."

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
        PillButton(album?.let { "${it.name} (${it.count}) ▾" } ?: "all albums ▾", { menu = true })
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
    SectionHead("find a photo", note = "Search by what's in it. Tap a word below, or type your own.")
    HandField(query, { query = it }, placeholder = "dog, beach, receipt, smiling…", singleLine = true, leading = Doodle.SEARCH)
    Spacer(Modifier.height(10.dp))
    if (query.isBlank()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            insights.topTags.take(14).forEach { (tag, n) -> HandTag("${prettify(tag).lowercase()} $n", onClick = { query = tag }) }
        }
    } else {
        Caption("${results.size} photos")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            results.take(60).forEach { p -> Thumbnail(p.uri, Modifier.size(84.dp).clickable { preview = p }) }
        }
    }
    preview?.let { PhotoPreview(it) { preview = null } }
}
