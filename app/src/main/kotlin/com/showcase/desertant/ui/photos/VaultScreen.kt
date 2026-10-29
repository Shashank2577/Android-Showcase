@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.showcase.desertant.ui.photos

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.security.keystore.UserNotAuthenticatedException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.app
import com.showcase.desertant.data.PhotoEntity
import com.showcase.desertant.photos.PhotoLibrary
import com.showcase.desertant.photos.PhotoScope
import com.showcase.desertant.privacy.LedgerEntry
import com.showcase.desertant.privacy.VaultItem
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.common.relativeDay
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.ConfirmSheet
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandEmpty
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandSheet
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.Snapshot
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val vault = app.vault
    val scope = rememberCoroutineScope()
    val unlocked by vault.unlocked.collectAsState()
    val items by vault.items.collectAsState()
    var opening by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<VaultItem?>(null) }
    val revealed = remember { mutableStateListOf<String>() }
    var moving by remember { mutableStateOf<Pair<List<PhotoEntity>, List<VaultItem>>?>(null) }

    fun open() = scope.launch {
        opening = true
        vault.unlock()
        opening = false
    }

    /** Vault calls fail once the key's minute is up; close it and say so. */
    suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: UserNotAuthenticatedException) {
            vault.lock()
            Toast.makeText(context, "The vault locked itself. Open it again.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
        }
    }

    val deleteOriginals = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (photos, copies) = moving ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (result.resultCode == Activity.RESULT_OK) {
                app.db.photos().delete(photos.map { it.mediaId })
                app.ledger.add(LedgerEntry.PHOTOS_VAULTED, copies.size)
            } else {
                guarded { vault.remove(copies.map { it.id }) }
            }
            moving = null
        }
    }

    HandPage(onBack = onBack, topEnd = { if (unlocked) HandLink("lock now", { vault.lock() }) }) {
        when {
            !vault.available() -> {
                Spacer(Modifier.height(40.dp))
                DoodleIcon(Doodle.LOCK, null, Modifier.align(Alignment.CenterHorizontally), size = 72.dp, stroke = 2.4.dp)
                Spacer(Modifier.height(16.dp))
                Text("the vault needs a screen lock", style = Hand.title, color = burrow.ink)
                Body("Its key is tied to your fingerprint or PIN, so set a screen lock in Android's settings first.", color = burrow.pencil)
            }
            !unlocked -> {
                Spacer(Modifier.height(40.dp))
                DoodleIcon(Doodle.LOCK, null, Modifier.align(Alignment.CenterHorizontally), size = 84.dp, stroke = 2.4.dp)
                Spacer(Modifier.height(20.dp))
                Text("the vault", style = Hand.display, color = burrow.ink, modifier = Modifier.align(Alignment.CenterHorizontally))
                Meta("${vault.count} photos · encrypted on this phone", Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(28.dp))
                InkButton("open with your fingerprint", ::open, Modifier.fillMaxWidth(), busy = opening)
                Spacer(Modifier.height(14.dp))
                Meta(
                    "Not visible in the gallery, Google Photos or other apps, and never in a cloud backup. It locks again the moment you leave Burrow.",
                    Modifier.align(Alignment.CenterHorizontally),
                )
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DoodleIcon(Doodle.LOCK_OPEN, null, size = 34.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("the vault", style = Hand.headline, color = burrow.ink)
                }
                Meta("encrypted on this phone · locks again when you leave")
                Spacer(Modifier.height(18.dp))
                val n = "${items.size} photos"
                MarkedText(n, mark = n, style = Hand.title)
                Spacer(Modifier.height(12.dp))
                if (items.isEmpty()) {
                    HandEmpty("nothing in here yet.", "Add photos you don't want anyone scrolling past: IDs, cards, medical, intimate.", doodle = Doodle.LOCK)
                } else {
                    Meta("Tap to reveal, hold to open.")
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items.sortedByDescending { it.addedAt }.forEach { item ->
                            VaultTile(item, item.id in revealed, onToggle = { if (item.id in revealed) revealed.remove(item.id) else revealed.add(item.id) }, onOpen = { viewing = item })
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HandLink("add photos", { adding = true })
                    HandLink("lock now", { vault.lock() })
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
                scope.launch { guarded { vault.export(item); Toast.makeText(context, "Back in the gallery, in Pictures/Burrow.", Toast.LENGTH_SHORT).show() } }
            },
            onDelete = {
                viewing = null
                scope.launch { guarded { vault.remove(listOf(item.id)) } }
            },
        )
    }
}

@Composable
private fun VaultTile(item: VaultItem, revealed: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val app = LocalContext.current.app
    val thumb by produceState<Bitmap?>(null, item.id) { value = app.vault.thumbnail(item.id) }
    Box(
        Modifier
            .combinedClickable(onClick = onToggle, onLongClick = onOpen)
            .semantics { contentDescription = if (revealed) "Shown: ${item.name}" else "Hidden photo" },
    ) {
        Snapshot {
            Box(Modifier.size(96.dp).background(burrow.smudge)) {
                thumb?.let {
                    Image(
                        it.asImageBitmap(), null,
                        Modifier.matchParentSize().then(if (revealed) Modifier else Modifier.blur(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (!revealed) {
                    Box(Modifier.matchParentSize().background(burrow.paper.copy(alpha = 0.55f)))
                    DoodleIcon(Doodle.EYE, null, Modifier.align(Alignment.Center), size = 26.dp)
                }
            }
        }
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
        }
        Text(item.kind ?: "private photo", style = Hand.door, color = burrow.ink)
        Meta("added ${relativeDay(item.addedAt).lowercase()} · ${formatBytes(context, item.bytes)}")
        InkButton("put it back in the gallery", onExport, Modifier.fillMaxWidth())
        HandLink("delete for good", { confirmDelete = true }, Modifier.align(Alignment.CenterHorizontally), color = burrow.redPen)
    }
    if (confirmDelete) {
        ConfirmSheet(
            "delete for good?",
            "This photo exists only in the vault. Once deleted, it can't be recovered — not from the gallery's trash, not from a backup.",
            "delete it", { confirmDelete = false; onDelete() }, { confirmDelete = false }, danger = true,
        )
    }
}

/** Recent photos from the gallery, to pick a few for the vault. */
@Composable
private fun AddToVaultSheet(onDismiss: () -> Unit, onAdd: (List<PhotoEntity>) -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<PhotoEntity>>(emptyList()) }
    val picked = remember { mutableStateListOf<Long>() }
    LaunchedEffect(Unit) { photos = PhotoLibrary.photos(context, PhotoScope(days = null)).take(150) }
    HandSheet(onDismiss) {
        Text("add to the vault", style = Hand.headline.copy(fontSize = 34.sp), color = burrow.ink)
        Meta("Picked photos are encrypted here, then Android asks you to delete the originals from the gallery.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            photos.forEach { p ->
                val on = p.mediaId in picked
                Box(Modifier.clickable { if (on) picked.remove(p.mediaId) else picked.add(p.mediaId) }) {
                    Thumbnail(p.uri, Modifier.size(76.dp), sizePx = 200)
                    if (on) {
                        Box(Modifier.matchParentSize().background(burrow.ink.copy(alpha = 0.35f)))
                        DoodleIcon(Doodle.TICK, null, Modifier.align(Alignment.Center), size = 32.dp, color = burrow.paper, stroke = 3.dp)
                    }
                }
            }
        }
        InkButton(
            if (picked.isEmpty()) "pick some photos" else "lock ${picked.size} away",
            { onAdd(photos.filter { it.mediaId in picked }) },
            Modifier.fillMaxWidth(),
            enabled = picked.isNotEmpty(),
        )
    }
}
