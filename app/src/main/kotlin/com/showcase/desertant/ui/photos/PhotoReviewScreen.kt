@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.photos

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.showcase.desertant.app
import com.showcase.desertant.data.PhotoEntity
import com.showcase.desertant.photos.SimilarGroup
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.common.relativeDay
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

private val TILTS = listOf(-2.5f, 1.8f, -1.2f, 2.6f, -1.8f, 1.2f)

/** Review one kind of cleanup: moments (keep the best), throwaway shots, or screenshots. */
@Composable
fun PhotoReviewScreen(kind: ReviewKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = context.app.db.photos()
    val scope = rememberCoroutineScope()
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
                Toast.makeText(context, "Let go of ${removed.size} photos. They're in the trash for 30 days.", Toast.LENGTH_LONG).show()
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

    HandPage(
        onBack = onBack,
        bottom = if (chosen.isEmpty()) null else ({
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${chosen.size} to let go", style = Hand.door, color = burrow.ink)
                    Meta("${formatBytes(context, chosen.sumOf { it.sizeBytes })} · recoverable for 30 days")
                }
                InkButton("let them go", { moveToTrash(chosen) })
            }
        }),
    ) {
        when {
            i == null -> Unit
            kind == ReviewKind.SIMILAR && groups.isEmpty() || kind != ReviewKind.SIMILAR && list.isEmpty() ->
                HandEmpty("all tidy.", "Nothing left to look at here.", doodle = Doodle.TICK)
            kind == ReviewKind.SIMILAR -> {
                val n = groups.size.toString()
                MarkedText("keep the best of $n moments", mark = n, style = Hand.headline.copy(fontSize = 40.sp, lineHeight = 42.sp))
                Meta("Burrow starred one shot in each — eyes open, sharpest, best faces. Tap any photo to change; hold to look closer.", Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(20.dp))
                groups.forEach { g ->
                    Moment(
                        group = g,
                        selected = ::selected,
                        onToggle = { p -> overrides[p.mediaId] = !selected(p) },
                        onPreview = { preview = it },
                        onKeepAll = {
                            g.extras.forEach { overrides.remove(it.mediaId) }
                            scope.launch { dao.markKept(g.extras.map { it.mediaId }) }
                        },
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }
            else -> {
                Text(kind.title, style = Hand.headline, color = burrow.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Meta(
                        if (kind == ReviewKind.JUNK) "Blurry, accidental and empty frames. All picked; tap to keep one."
                        else "Tickets, maps and codes you probably don't need. Tap the ones to let go.",
                        Modifier.weight(1f),
                    )
                    val all = list.all(::selected)
                    HandLink(if (all) "none" else "all", { list.forEach { overrides[it.mediaId] = !all } })
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    list.forEachIndexed { n, p ->
                        Column(Modifier.width(96.dp)) {
                            Tile(p, selected(p), best = false, tilt = TILTS[n % TILTS.size], size = 88.dp, onToggle = { overrides[p.mediaId] = !selected(p) }, onPreview = { preview = p })
                            Meta(p.junkReason?.lowercase() ?: relativeDay(p.dateTaken), Modifier.padding(top = 6.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    preview?.let { PhotoPreview(it) { preview = null } }
}

@Composable
private fun Moment(
    group: SimilarGroup,
    selected: (PhotoEntity) -> Boolean,
    onToggle: (PhotoEntity) -> Unit,
    onPreview: (PhotoEntity) -> Unit,
    onKeepAll: () -> Unit,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("${relativeDay(group.best.dateTaken).lowercase()} — ${group.photos.size} shots", style = Hand.door, color = burrow.ink)
            Meta("keep: ${group.bestReason.lowercase()} · frees ${formatBytes(context, group.extras.filter(selected).sumOf { it.sizeBytes })}")
        }
        HandLink("keep all", onKeepAll)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        group.photos.forEachIndexed { n, p ->
            val best = p.mediaId == group.best.mediaId
            Tile(p, selected(p), best, TILTS[n % TILTS.size], if (best) 128.dp else 96.dp, { onToggle(p) }, { onPreview(p) })
        }
    }
}

/** A snapshot: faded with a red cross when it's going, starred when it's the keeper. */
@Composable
private fun Tile(p: PhotoEntity, selected: Boolean, best: Boolean, tilt: Float, size: Dp, onToggle: () -> Unit, onPreview: () -> Unit) {
    Box(
        Modifier
            .combinedClickable(onClick = onToggle, onLongClick = onPreview)
            .semantics { contentDescription = if (selected) "Letting go" else if (best) "Best shot, keeping" else "Keeping" },
    ) {
        Snapshot(tilt = tilt, faded = selected) { Thumbnail(p.uri, Modifier.size(size), sizePx = 320) }
        if (selected) DoodleIcon(Doodle.CROSS, null, Modifier.align(Alignment.Center), size = size * 0.42f, color = burrow.redPen, stroke = 3.dp)
        if (best && !selected) {
            DoodleIcon(Doodle.STAR, null, Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 2.dp), size = 30.dp, color = burrow.marker, fill = true)
            DoodleIcon(Doodle.STAR, null, Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 2.dp), size = 30.dp, color = burrow.ink, stroke = 1.6.dp)
        }
    }
}
