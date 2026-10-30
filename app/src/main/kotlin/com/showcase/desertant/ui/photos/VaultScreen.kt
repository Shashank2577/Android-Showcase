@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.photos

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.showcase.desertant.app
import com.showcase.desertant.data.PhotoEntity
import com.showcase.desertant.photos.PhotoLibrary
import com.showcase.desertant.photos.PhotoScope
import com.showcase.desertant.privacy.LedgerEntry
import com.showcase.desertant.privacy.VaultItem
import com.showcase.desertant.privacy.VaultLockedException
import com.showcase.desertant.privacy.VaultOpen
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.common.relativeDay
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.ConfirmSheet
import com.showcase.desertant.ui.hand.DoneNote
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PickMark
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.Snapshot
import com.showcase.desertant.ui.hand.Steps
import com.showcase.desertant.ui.privacy.SecureScreen
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

/** The words for an open attempt that didn't work, or null if there's nothing to say. */
fun VaultOpen.problem(): String? = when (this) {
    VaultOpen.Opened, VaultOpen.Cancelled -> null
    VaultOpen.NoScreenLock -> "Set a screen lock (PIN, pattern or fingerprint) in Android's settings first. The vault's key is tied to it."
    VaultOpen.KeyLost -> "Android destroyed the vault's key because the screen lock was removed or reset. The photos in it can't be opened any more."
    is VaultOpen.Failed -> "The vault didn't open: $message"
}

@Composable
fun VaultScreen(onBack: () -> Unit) {
    SecureScreen()
    val context = LocalContext.current
    val app = context.app
    val vault = app.vault
    val scope = rememberCoroutineScope()
    val unlocked by vault.unlocked.collectAsState()
    val items by vault.items.collectAsState()
    var opening by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<VaultOpen?>(null) }
    var adding by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<VaultItem?>(null) }
    var moving by remember { mutableStateOf<Pair<List<PhotoEntity>, List<VaultItem>>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    fun open() = scope.launch {
        opening = true
        problem = vault.unlock().takeIf { it.problem() != null }
        opening = false
    }

    /** Every vault action reports what went wrong instead of failing silently. */
    suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: VaultLockedException) {
            Toast.makeText(context, "The vault locked. Open it again.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "That didn't work: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    val deleteOriginals = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (photos, copies) = moving ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (result.resultCode == Activity.RESULT_OK) {
                app.db.photos().delete(photos.map { it.mediaId })
                app.ledger.add(LedgerEntry.PHOTOS_VAULTED, copies.size)
                note = if (copies.size == 1) "1 photo locked away, and gone from the gallery." else "${copies.size} photos locked away, and gone from the gallery."
            } else {
                // The originals stayed in the gallery, so don't keep a second copy either.
                guarded { vault.remove(copies.map { it.id }) }
                note = "Nothing moved: the originals are still in your gallery."
            }
            moving = null
        }
    }

    HandPage(
        onBack = onBack,
        topEnd = { if (unlocked) PillButton("lock", { vault.lock() }, Modifier.padding(end = 8.dp), leading = Doodle.LOCK) },
        bottom = if (unlocked) ({ InkButton("add photos", { adding = true }, Modifier.fillMaxWidth(), trailing = Doodle.PLUS) }) else null,
    ) {
        when {
            !unlocked -> {
                PageIntro("the vault", "Photos you don't want anyone scrolling past: IDs, cards, medical, intimate. Encrypted on this phone, never in a cloud backup.", doodle = Doodle.HEART)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DoodleIcon(Doodle.LOCK, null, size = 84.dp, stroke = 2.4.dp)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (vault.count == 0) "empty, and locked." else "${vault.count} photos, locked.",
                    Modifier.fillMaxWidth(), style = Hand.title, color = burrow.ink, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                if (!vault.available()) {
                    Body(VaultOpen.NoScreenLock.problem()!!, color = burrow.redPen)
                } else {
                    InkButton("open with your fingerprint", ::open, Modifier.fillMaxWidth(), busy = opening, trailing = Doodle.LOCK_OPEN)
                    problem?.problem()?.let { Body(it, Modifier.padding(top = 12.dp), color = burrow.redPen) }
                    if (problem == VaultOpen.KeyLost) PillButton("start a new, empty vault", { confirmReset = true }, Modifier.padding(top = 10.dp), color = burrow.redPen)
                }
                Spacer(Modifier.height(24.dp))
                Text("how it works", style = Hand.label, color = burrow.ink)
                Spacer(Modifier.height(6.dp))
                Steps(
                    listOf(
                        "Open it with your fingerprint or PIN",
                        "Add photos, or move them here from the private sweep",
                        "Android asks to delete the originals from the gallery",
                        "It locks again the moment you leave Burrow",
                    ),
                )
            }
            else -> {
                PageIntro("the vault", "Open until you leave Burrow. Tap a photo to see it, put it back in the gallery, or delete it for good.", mark = "vault")
                note?.let { DoneNote(it, Modifier.padding(bottom = 14.dp)) }
                if (items.isEmpty()) {
                    Body("Nothing in here yet. Tap “add photos” below, or use the private sweep on the photos page.", color = burrow.pencil)
                } else {
                    Meta(if (items.size == 1) "1 photo · blurred until you tap it" else "${items.size} photos · blurred until you tap one")
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items.sortedByDescending { it.addedAt }.forEach { item -> VaultTile(item) { viewing = item } }
                    }
                }
            }
        }
    }

    if (adding) {
        AddToVaultSheet(onDismiss = { adding = false }) { picked ->
            adding = false
            scope.launch {
                guarded {
                    val copies = vault.add(picked.map { Uri.parse(it.uri) })
                    moving = picked to copies
                    val request = MediaStore.createDeleteRequest(context.contentResolver, picked.map { Uri.parse(it.uri) })
                    deleteOriginals.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }
            }
        }
    }
    viewing?.let { item ->
        VaultViewer(
            item,
            onDismiss = { viewing = null },
            onExport = {
                viewing = null
                scope.launch { guarded { vault.export(item); note = "Put back in the gallery, in Pictures/Burrow." } }
            },
            onDelete = {
                viewing = null
                scope.launch { guarded { vault.remove(listOf(item.id)); note = "Deleted for good." } }
            },
        )
    }
    if (confirmReset) {
        ConfirmSheet(
            "start a new vault?",
            "The photos in the old vault are encrypted with a key Android has destroyed, so they can't be opened. This deletes them and makes a new, empty vault.",
            "start again",
            { confirmReset = false; vault.reset(); problem = null },
            { confirmReset = false },
            danger = true,
        )
    }
}

@Composable
private fun VaultTile(item: VaultItem, onOpen: () -> Unit) {
    val app = LocalContext.current.app
    val thumb by produceState<Bitmap?>(null, item.id) { value = app.vault.thumbnail(item.id) }
    Column(
        Modifier.width(100.dp).clickable(onClick = onOpen).semantics { contentDescription = "Private photo, tap to open" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Snapshot {
            Box(Modifier.size(92.dp).background(burrow.smudge)) {
                thumb?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize().blur(18.dp), contentScale = ContentScale.Crop) }
                Box(Modifier.matchParentSize().background(burrow.paper.copy(alpha = 0.5f)))
                DoodleIcon(Doodle.EYE, null, Modifier.align(Alignment.Center), size = 26.dp)
            }
        }
        Meta(item.kind ?: relativeDay(item.addedAt).lowercase(), Modifier.padding(top = 6.dp), maxLines = 1)
    }
}

@Composable
private fun VaultViewer(item: VaultItem, onDismiss: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val photo by produceState<Bitmap?>(null, item.id) { value = context.app.vault.photo(item.id) }
    var confirmDelete by remember { mutableStateOf(false) }
    HandSheet(onDismiss) {
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 480.dp), contentAlignment = Alignment.Center) {
            photo?.let { Image(it.asImageBitmap(), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                ?: Meta("opening…")
        }
        Text(item.kind ?: "private photo", style = Hand.door, color = burrow.ink)
        Meta("added ${relativeDay(item.addedAt).lowercase()} · ${formatBytes(context, item.bytes)}")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("back to gallery", onExport, Modifier.weight(1f), leading = Doodle.SHARE)
            PillButton("delete for good", { confirmDelete = true }, Modifier.weight(1f), leading = Doodle.TRASH, color = burrow.redPen)
        }
        HandLink("close", onDismiss, Modifier.align(Alignment.CenterHorizontally))
    }
    if (confirmDelete) {
        ConfirmSheet(
            "delete for good?",
            "This photo exists only in the vault. Once deleted, it can't be recovered — not from the gallery's trash, not from a backup.",
            "delete it", { confirmDelete = false; onDelete() }, { confirmDelete = false }, danger = true,
        )
    }
}

/** Recent photos from the gallery, to pick a few for the vault. Asks for photo access if needed. */
@Composable
private fun AddToVaultSheet(onDismiss: () -> Unit, onAdd: (List<PhotoEntity>) -> Unit) {
    val context = LocalContext.current
    fun allowed() = photoPermissions().any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(allowed()) }
    var photos by remember { mutableStateOf<List<PhotoEntity>?>(null) }
    var limit by remember { mutableStateOf(120) }
    val picked = remember { mutableStateListOf<Long>() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = allowed() }
    LaunchedEffect(granted) { if (granted) photos = PhotoLibrary.photos(context, PhotoScope(days = null)) }
    HandSheet(onDismiss) {
        Text("add to the vault", style = Hand.headline.copy(fontSize = 34.sp), color = burrow.ink)
        Steps(listOf("Tick the photos to lock away", "Burrow encrypts copies in the vault", "Android asks to delete the originals — say yes"))
        if (!granted) {
            Body("Burrow needs to see your photos to list them here.", color = burrow.pencil)
            InkButton("allow photo access", { ask.launch(photoPermissions()) }, Modifier.fillMaxWidth())
            return@HandSheet
        }
        val list = photos
        when {
            list == null -> Meta("loading your photos…")
            list.isEmpty() -> Body("No photos found on this phone.", color = burrow.pencil)
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.take(limit).forEach { p ->
                        val on = p.mediaId in picked
                        Box(Modifier.clickable { if (on) picked.remove(p.mediaId) else picked.add(p.mediaId) }) {
                            Thumbnail(p.uri, Modifier.size(76.dp), sizePx = 200)
                            if (on) Box(Modifier.matchParentSize().background(burrow.ink.copy(alpha = 0.25f)))
                            PickMark(on, Modifier.align(Alignment.TopEnd).padding(4.dp))
                        }
                    }
                }
                if (list.size > limit) HandLink("show more", { limit += 120 }, Modifier.align(Alignment.CenterHorizontally))
            }
        }
        InkButton(
            when (picked.size) { 0 -> "tick some photos"; 1 -> "lock 1 photo away"; else -> "lock ${picked.size} photos away" },
            { onAdd(list.orEmpty().filter { it.mediaId in picked }) },
            Modifier.fillMaxWidth(),
            enabled = picked.isNotEmpty(),
        )
    }
}
