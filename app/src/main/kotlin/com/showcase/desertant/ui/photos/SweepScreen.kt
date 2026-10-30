@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.photos

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.showcase.desertant.app
import com.showcase.desertant.data.PhotoEntity
import com.showcase.desertant.photos.PrivateKind
import com.showcase.desertant.privacy.LedgerEntry
import com.showcase.desertant.privacy.VaultItem
import com.showcase.desertant.privacy.VaultOpen
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.DoneNote
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandEmpty
import com.showcase.desertant.ui.hand.HandPage
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

/** "Who could see this if someone scrolled your gallery?" — and one move: the vault. */
@Composable
fun SweepScreen(onBack: () -> Unit, openVault: () -> Unit) {
    SecureScreen()
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val insights by app.photoInsights.collectAsStateWithLifecycle()
    val found = insights?.private.orEmpty()
    // Everything found is ticked to move; untick a photo to leave it in the gallery.
    val leave = remember { mutableStateMapOf<Long, Boolean>() }
    var busy by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Pair<List<PhotoEntity>, List<VaultItem>>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }

    val delete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (photos, copies) = moving ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (result.resultCode == Activity.RESULT_OK) {
                app.db.photos().delete(photos.map { it.mediaId })
                app.ledger.add(LedgerEntry.PHOTOS_VAULTED, copies.size)
                done = "${copies.size} photos are in the vault, and gone from the gallery."
            } else {
                // The originals stayed in the gallery, so don't keep a second copy either.
                runCatching { app.vault.remove(copies.map { it.id }) }
                problem = "Nothing moved: Android's delete step was cancelled, so the photos are still in your gallery."
            }
            moving = null
            busy = false
        }
    }

    val chosen = found.values.flatten().filter { leave[it.mediaId] != true }

    fun moveToVault() = scope.launch {
        busy = true
        problem = null
        val open = app.vault.unlock()
        open.problem()?.let { problem = it }
        if (open != VaultOpen.Opened) {
            busy = false
            return@launch
        }
        try {
            val copies = found.flatMap { (kind, photos) ->
                val picked = photos.filter { leave[it.mediaId] != true }
                if (picked.isEmpty()) emptyList() else app.vault.add(picked.map { Uri.parse(it.uri) }, kind.label)
            }
            moving = chosen to copies
            val request = MediaStore.createDeleteRequest(context.contentResolver, chosen.map { Uri.parse(it.uri) })
            delete.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (e: Exception) {
            busy = false
            problem = "Couldn't move them: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    HandPage(
        onBack = onBack,
        bottom = if (chosen.isEmpty()) null else ({
            InkButton(
                if (chosen.size == 1) "move 1 photo to the vault" else "move ${chosen.size} photos to the vault",
                ::moveToVault, Modifier.fillMaxWidth(), enabled = app.vault.available(), busy = busy, trailing = Doodle.LOCK,
            )
        }),
    ) {
        if (found.isEmpty()) {
            done?.let { DoneNote(it, Modifier.padding(bottom = 12.dp), "open the vault", openVault) }
            HandEmpty("nothing in plain sight.", "No IDs, cards, medical or intimate photos in the photos Burrow has looked through.", doodle = Doodle.TICK) {
                PillButton("open the vault", openVault, leading = Doodle.LOCK)
            }
            return@HandPage
        }
        val n = found.values.sumOf { it.size }.toString()
        PageIntro("found $n private photos", "Anyone scrolling your gallery could see these. Move them into the vault, where only your fingerprint opens them.", mark = n, doodle = Doodle.EYE_OFF)
        Steps(listOf("Untick any photo that isn't private", "Confirm with your fingerprint", "Android asks to delete the originals — say yes"))
        Spacer(Modifier.height(12.dp))
        problem?.let { Body(it, Modifier.padding(bottom = 10.dp), color = burrow.redPen) }
        if (!app.vault.available()) Body(VaultOpen.NoScreenLock.problem()!!, Modifier.padding(bottom = 10.dp), color = burrow.redPen)
        found.forEach { (kind, photos) ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${kind.label} — ${photos.size}", style = Hand.door, color = burrow.ink)
                    Meta(kind.why)
                }
                PillButton("not private", { scope.launch { app.db.photos().markNotPrivate(photos.map { it.mediaId }) } })
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                photos.forEachIndexed { i, p ->
                    val moves = leave[p.mediaId] != true
                    Column(
                        Modifier
                            .width(84.dp)
                            .clickable(role = Role.Checkbox) { leave[p.mediaId] = moves }
                            .semantics { contentDescription = if (moves) "Moving to the vault" else "Staying in the gallery" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            Snapshot(tilt = listOf(-2f, 1.5f, -1f, 2.2f)[i % 4], faded = !moves) {
                                Box(Modifier.size(72.dp)) {
                                    // Decoded tiny, then blurred: unreadable even where blur isn't supported.
                                    Thumbnail(p.uri, Modifier.matchParentSize().blur(10.dp), sizePx = 24)
                                    if (kind == PrivateKind.INTIMATE) {
                                        Box(Modifier.matchParentSize().background(burrow.smudge))
                                        DoodleIcon(Doodle.SQUIGGLE, null, Modifier.align(Alignment.Center), size = 56.dp, stroke = 3.dp)
                                    }
                                }
                            }
                            PickMark(moves, Modifier.align(Alignment.TopEnd).padding(2.dp))
                        }
                        Meta(if (moves) "to the vault" else "stays", Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DoodleIcon(Doodle.LOCK, null, size = 16.dp, color = burrow.pencil)
            Spacer(Modifier.width(8.dp))
            Meta("The vault is encrypted on this phone and never goes into a cloud backup.")
        }
    }
}
