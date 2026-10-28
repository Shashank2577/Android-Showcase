@file:OptIn(ExperimentalLayoutApi::class)

package com.showcase.desertant.ui.models

import ai.desertant.eye.Analysis
import ai.desertant.eye.Box as EyeBox
import ai.desertant.eye.Eye
import ai.desertant.eye.Level
import ai.desertant.eye.Options
import ai.desertant.eye.Part
import ai.desertant.eye.Tag
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.image.ImageLoader
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.MultiChips
import com.showcase.desertant.ui.components.PickImageButton
import com.showcase.desertant.ui.components.ScoreBar
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.Stat
import com.showcase.desertant.ui.components.prettify
import com.showcase.desertant.ui.components.rememberRunner
import com.showcase.desertant.ui.theme.seriesColor

private const val LIBRARY_LIMIT = 30

@Composable
fun EyeScreen(onBack: () -> Unit) {
    val runner = rememberRunner(ModelKind.EYE)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ModelScreen(ModelKind.EYE, onBack, runner) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("One photo") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Photo library") })
        }
        if (tab == 0) SinglePhoto(runner) else Library(runner)
    }
}

@Composable
private fun SinglePhoto(runner: com.showcase.desertant.ui.components.Runner) {
    val hub = LocalModelHub.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var parts by remember { mutableStateOf(Part.entries.toSet()) }
    var strict by rememberSaveable { mutableStateOf(false) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    var showOverlays by rememberSaveable { mutableStateOf(true) }

    fun analyze(image: Bitmap) = runner.run {
        analysis = null
        analysis = hub.eye.analyze(image, Options(parts = parts))
    }

    SectionCard("What to compute") {
        MultiChips(
            Part.entries.toList(), parts,
            onToggle = { p -> parts = if (p in parts && parts.size > 1) parts - p else parts + p },
            label = { prettify(it.name.lowercase()) },
        )
        PickImageButton(enabled = !runner.busy, onError = { runner.error = it }) {
            bitmap = it
            analyze(it)
        }
    }
    val b = bitmap ?: return
    val a = analysis
    SectionCard("Photo") {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(b.width.toFloat() / b.height),
        ) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            if (a != null && showOverlays) {
                Canvas(Modifier.fillMaxSize()) { drawOverlays(a) }
            }
        }
        Column {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Boxes: faces · people · pets · best square crop", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = showOverlays, onCheckedChange = { showOverlays = it })
            }
        }
        Button(enabled = !runner.busy, onClick = { analyze(b) }, modifier = Modifier.fillMaxWidth()) { Text("Re-analyze") }
    }
    if (a == null) return

    SectionCard("Summary") {
        a.kind?.let { Stat("Kind", prettify(it.id)) }
        a.rotation?.let { if (it != 0) Stat("Needs rotation", "$it° clockwise") else Stat("Orientation", "Upright") }
        a.people?.let { Stat("People", it.size.toString()) }
        a.pets?.let { Stat("Pets", it.size.toString()) }
        a.faces?.let { Stat("Faces", it.size.toString()) }
        a.scores?.let { s ->
            ScoreBar("Aesthetic (better than this share of photos)", s.aesthetic)
            ScoreBar("Keeper", s.keeper)
            ScoreBar("Cover-worthy", s.cover)
        }
        a.details?.sharpness?.let { ScoreBar("Sharpness", it.score) }
    }
    SectionCard("Tags") {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Strict cutoff (0.9) for acting without asking", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(checked = strict, onCheckedChange = { strict = it })
        }
        val tags = a.tags(if (strict) Level.STRICT else Level.RECOMMENDED)
        if (tags.isEmpty()) Text("No tags above the cutoff.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { t ->
                AssistChip(onClick = {}, label = { Text("${prettify(t.id)} ${"%.0f".format((a.confidence(t) ?: 0.0) * 100)}%") })
            }
        }
    }
    a.faces?.takeIf { it.isNotEmpty() }?.let { faces ->
        SectionCard("Faces") {
            faces.forEachIndexed { i, f ->
                Text("Face ${i + 1}", style = MaterialTheme.typography.titleSmall, color = seriesColor(i))
                ScoreBar("Face quality", f.quality, color = seriesColor(i))
                Stat("Pose (yaw / pitch / roll)", "%.0f° / %.0f° / %.0f°".format(f.pose.yaw, f.pose.pitch, f.pose.roll))
                val faceTags = f.confidences.entries.filter { it.value >= 0.5 }.sortedByDescending { it.value }
                if (faceTags.isNotEmpty()) {
                    Text(faceTags.joinToString(" · ") { prettify(it.key.id) }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun DrawScope.drawOverlays(a: Analysis) {
    val stroke = Stroke(width = 3.dp.toPx())
    fun rect(box: EyeBox, color: Color) {
        drawRect(
            color,
            Offset((box.x0 * size.width).toFloat(), (box.y0 * size.height).toFloat()),
            Size((box.width * size.width).toFloat(), (box.height * size.height).toFloat()),
            style = stroke,
        )
    }
    a.people?.forEach { rect(it.box, Color(0xFF2F7DD1)) }
    a.pets?.forEach { rect(it.box, Color(0xFF2FA36B)) }
    a.faces?.forEachIndexed { i, f ->
        rect(f.box, seriesColor(i))
        f.landmarks.forEach { p ->
            drawCircle(seriesColor(i), 3.dp.toPx(), Offset((p.x * size.width).toFloat(), (p.y * size.height).toFloat()))
        }
    }
    a.bestCrop(1.0)?.let { crop ->
        drawRect(
            Color.White,
            Offset((crop.x0 * size.width).toFloat(), (crop.y0 * size.height).toFloat()),
            Size((crop.width * size.width).toFloat(), (crop.height * size.height).toFloat()),
            style = Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(16f, 10f))),
        )
    }
}

private class LibraryPhoto(val bitmap: Bitmap, val analysis: Analysis)

@Composable
private fun Library(runner: com.showcase.desertant.ui.components.Runner) {
    val hub = LocalModelHub.current
    val context = LocalContext.current
    val photos = remember { mutableStateListOf<LibraryPhoto>() }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(LIBRARY_LIMIT)) { uris ->
        if (uris.isNotEmpty()) runner.run {
            photos.clear()
            selected = null
            uris.forEachIndexed { i, uri ->
                progress = i to uris.size
                val bitmap = ImageLoader.load(context, uri, maxDimension = 768)
                // Photo (embedding, scores) and faces are what best/duplicates rank by.
                val analysis = hub.eye.analyze(bitmap, Options(parts = setOf(Part.PHOTO, Part.FACES)))
                photos.add(LibraryPhoto(bitmap, analysis))
            }
            progress = null
        }
    }

    val analyses = photos.map { it.analysis }
    val best = remember(photos.size) { if (analyses.isEmpty()) null else Eye.best(analyses) }
    val groups = remember(photos.size) { if (analyses.size < 2) emptyList() else Eye.duplicates(analyses) }
    val groupOf = remember(groups) { groups.flatMapIndexed { g, members -> members.map { it to g } }.toMap() }
    val tag = query.trim().lowercase().replace(' ', '_')
    val matches = remember(tag, photos.size) {
        if (tag.isBlank()) null else photos.indices.filter { photos[it].analysis.has(Tag(tag)) }.toSet()
    }
    val similar = remember(selected, photos.size) {
        selected?.let { s -> Eye.similar(analyses[s], analyses, limit = 6).filter { it.first != s } }
    }

    SectionCard("Your photos") {
        Text(
            "Pick up to $LIBRARY_LIMIT photos (a burst works best). Eye finds the best shot, groups near-duplicates, lets you search by tag and finds similar photos — all on the device.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            enabled = !runner.busy,
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        ) {
            Icon(Icons.Outlined.PhotoLibrary, null)
            Text("  Pick photos")
        }
        progress?.let { (i, n) ->
            Text("Analyzing ${i + 1} of $n…", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { (i + 1f) / n }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (photos.isEmpty()) return

    SectionCard("Library (${photos.size})") {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search by tag — e.g. dog, beach, screenshot, smiling") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(
            "★ best shot · colored frames = near-duplicate groups · tap a photo for similar ones",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            photos.forEachIndexed { i, p ->
                if (matches == null || i in matches) {
                    val group = groupOf[i]
                    Box(
                        Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                when {
                                    selected == i -> Modifier.border(4.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    group != null -> Modifier.border(3.dp, seriesColor(group), RoundedCornerShape(8.dp))
                                    else -> Modifier
                                },
                            )
                            .clickable { selected = if (selected == i) null else i },
                    ) {
                        Image(p.bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (best == i) {
                            Text("★", color = Color(0xFFFFC107), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }
        if (matches != null) Text("${matches.size} photo(s) tagged \"${prettify(tag)}\"", style = MaterialTheme.typography.labelLarge)
        Stat("Near-duplicate groups", groups.size.toString())
    }
    selected?.let { s ->
        val a = photos[s].analysis
        SectionCard("Selected photo") {
            a.scores?.let { ScoreBar("Keeper", it.keeper) }
            Text(
                a.tags().take(8).joinToString(" · ") { prettify(it.id) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Most similar", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                similar.orEmpty().forEach { (j, score) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            photos[j].bitmap.asImageBitmap(), null,
                            Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)).clickable { selected = j },
                            contentScale = ContentScale.Crop,
                        )
                        Text("%.2f".format(score), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
