package com.showcase.desertant.ui.photos

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.showcase.desertant.data.PhotoEntity
import com.showcase.desertant.ui.common.Thumbnail
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.common.formatBytes
import com.showcase.desertant.ui.components.prettify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen look at one photo with what Eye thought of it. */
// Lint misreads the producer below: it always assigns `value`.
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun PhotoPreview(p: PhotoEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // A large thumbnail is plenty for judging a shot and loads fast.
    val large by produceState<Bitmap?>(null, p.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(Uri.parse(p.uri), Size(1600, 1600), null) }.getOrNull()
        }
    }
    val bitmap = large
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Thumbnail(p.uri, Modifier.fillMaxSize())
                }
            }
            Text("${dateTime(context, p.dateTaken)} · ${formatBytes(context, p.sizeBytes)} · ${p.width}×${p.height}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            p.keeper?.let {
                Text("Keeper score ${"%.0f".format(it * 100)} · aesthetic ${"%.0f".format((p.aesthetic ?: 0.0) * 100)}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                p.tags.split(' ').filter { it.isNotBlank() }.take(10).joinToString(" · ") { prettify(it) },
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}
