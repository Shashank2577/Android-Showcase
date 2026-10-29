@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.photos

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import android.security.keystore.UserNotAuthenticatedException
import android.widget.Toast
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.showcase.desertant.app
import com.showcase.desertant.data.PhotoEntity
import com.showcase.desertant.photos.PrivateKind
import com.showcase.desertant.privacy.LedgerEntry
import com.showcase.desertant.privacy.VaultItem
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandEmpty
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.Snapshot
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

/** "Who could see this if someone scrolled your gallery?" — and one move: the vault. */
@Composable
fun SweepScreen(onBack: () -> Unit, openVault: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val insights by app.photoInsights.collectAsStateWithLifecycle()
    val found = insights?.private.orEmpty()
    // Everything found is picked to move; tap a photo to leave it where it is.
    val leave = remember { mutableStateMapOf<Long, Boolean>() }
    var busy by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Pair<List<PhotoEntity>, List<VaultItem>>?>(null) }

    val delete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (photos, copies) = moving ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (result.resultCode == Activity.RESULT_OK) {
                app.db.photos().delete(photos.map { it.mediaId })
                app.ledger.add(LedgerEntry.PHOTOS_VAULTED, copies.size)
                Toast.makeText(context, "${copies.size} photos are in the vault, and gone from the gallery.", Toast.LENGTH_LONG).show()
            } else {
                // The originals stayed in the gallery, so don't keep a second copy either.
                runCatching { app.vault.remove(copies.map { it.id }) }
            }
            moving = null
            busy = false
        }
    }

    val chosen = found.values.flatten().filter { leave[it.mediaId] != true }

    fun moveToVault() = scope.launch {
        busy = true
        try {
            if (!app.vault.unlock()) {
                busy = false
                return@launch
            }
            val copies = found.flatMap { (kind, photos) ->
                val picked = photos.filter { leave[it.mediaId] != true }
                if (picked.isEmpty()) emptyList() else app.vault.add(picked.map { Uri.parse(it.uri) }, kind.label)
            }
            moving = chosen to copies
            val request = MediaStore.createDeleteRequest(context.contentResolver, chosen.map { Uri.parse(it.uri) })
            delete.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (_: UserNotAuthenticatedException) {
            app.vault.lock()
            busy = false
            Toast.makeText(context, "The vault locked itself. Try again.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            busy = false
            Toast.makeText(context, "Couldn't move them: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val canVault = app.vault.available()
    HandPage(
        onBack = onBack,
        bottom = if (chosen.isEmpty()) null else ({
            InkButton(
                if (chosen.size == 1) "move it to the vault" else "move ${chosen.size} to the vault",
                ::moveToVault, Modifier.fillMaxWidth(), enabled = canVault, busy = busy, trailing = Doodle.LOCK,
            )
            Meta(
                if (canVault) "The vault is encrypted on this phone, opens with your fingerprint, and is never backed up to a cloud."
                else "Set a screen lock in Android's settings first: the vault's key is tied to it.",
                Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally),
                color = if (canVault) burrow.pencil else burrow.redPen,
            )
        }),
    ) {
        if (found.isEmpty()) {
            HandEmpty("nothing in plain sight.", "No IDs, cards, medical or intimate photos found in what Burrow has looked through.", doodle = Doodle.TICK)
            HandLink("open the vault", openVault, Modifier.align(Alignment.CenterHorizontally))
            return@HandPage
        }
        val n = found.values.sumOf { it.size }.toString()
        MarkedText("found $n things in plain sight.", mark = n, style = Hand.headline.copy(fontSize = 42.sp, lineHeight = 44.sp))
        Meta("Anyone scrolling your gallery could see these. They stay blurred here; tap one to leave it in the gallery.", Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))
        found.forEach { (kind, photos) ->
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("${kind.label} — ${photos.size}", style = Hand.door, color = burrow.ink)
                    Meta(kind.why)
                }
                HandLink("not private", {
                    scope.launch { app.db.photos().markNotPrivate(photos.map { it.mediaId }) }
                }, style = Hand.small)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                photos.forEachIndexed { i, p ->
                    val staying = leave[p.mediaId] == true
                    Box(
                        Modifier
                            .clickable { leave[p.mediaId] = !staying }
                            .semantics { contentDescription = if (staying) "Staying in the gallery" else "Moving to the vault" },
                    ) {
                        Snapshot(tilt = listOf(-2f, 1.5f, -1f, 2.2f)[i % 4], faded = staying) {
                            Box(Modifier.size(72.dp)) {
                                // Decoded tiny, then blurred: unreadable even where blur isn't supported.
                                Thumbnail(p.uri, Modifier.matchParentSize().blur(10.dp), sizePx = 24)
                                if (kind == PrivateKind.INTIMATE) {
                                    Box(Modifier.matchParentSize().background(burrow.smudge))
                                    DoodleIcon(Doodle.SQUIGGLE, null, Modifier.align(Alignment.Center), size = 56.dp, color = burrow.ink, stroke = 3.dp)
                                }
                            }
                        }
                        if (staying) DoodleIcon(Doodle.CROSS, null, Modifier.align(Alignment.Center), size = 30.dp, color = burrow.redPen, stroke = 3.dp)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}
