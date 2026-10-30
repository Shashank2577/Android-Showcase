package com.showcase.desertant.ui.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.privacy.ShareCheck
import com.showcase.desertant.privacy.ShareReport
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.HandSwitch
import com.showcase.desertant.ui.hand.handCircle
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.Snapshot
import com.showcase.desertant.ui.hand.DoodleButton
import com.showcase.desertant.ui.theme.AppTheme
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

/**
 * "Share safely": Burrow appears in Android's share sheet for photos. Before
 * a photo goes anywhere it says what's in it, offers fixes, and passes a clean
 * copy on to the app you were sharing to.
 */
class ShareCheckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = sharedUri(intent)
        if (uri == null) {
            finish()
            return
        }
        setContent {
            AppTheme {
                ShareCheckScreen(
                    uri,
                    onClose = ::finish,
                    onSend = { out, granted ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, out)
                            clipData = ClipData.newRawUri(null, out)
                            if (granted) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(send, "Send with").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        finish()
                    },
                )
            }
        }
    }

    private fun sharedUri(intent: Intent?): Uri? = when {
        intent == null -> null
        Build.VERSION.SDK_INT >= 33 -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else -> @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
    } ?: intent?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
}

/** [preloaded] skips the on-device check; screenshot tests use it, as they can't run the models. */
@Composable
internal fun ShareCheckScreen(uri: Uri, onClose: () -> Unit, onSend: (Uri, Boolean) -> Unit, preloaded: ShareReport? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf(preloaded) }
    var error by remember { mutableStateOf<String?>(null) }
    var crop by remember { mutableStateOf(preloaded?.let { it.sensitive != null && it.crop != null } ?: false) }
    var blur by remember { mutableStateOf(false) }
    var strip by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (preloaded != null) return@LaunchedEffect
        runCatching { ShareCheck.check(context, uri) }
            .onSuccess { r ->
                report = r
                crop = r.sensitive != null && r.crop != null
                strip = true
            }
            .onFailure { error = it.message ?: "Couldn't read this photo" }
    }

    val r = report
    HandPage(
        topEnd = { DoodleButton(Doodle.CLOSE, "Cancel", onClose) },
        bottom = if (r == null) null else ({
            val changed = crop || blur || strip
            InkButton(
                if (changed) "share clean copy" else "share it",
                {
                    busy = true
                    scope.launch {
                        val out = if (changed) ShareCheck.cleanCopy(context, r, crop, blur) else uri
                        onSend(out, true)
                    }
                },
                Modifier.fillMaxWidth(), busy = busy, trailing = Doodle.ARROW,
            )
            HandLink("send the original", { onSend(uri, true) }, Modifier.align(Alignment.CenterHorizontally))
        }),
    ) {
        MarkedText("before you send this…", mark = "before you send", style = Hand.headline.copy(fontSize = 40.sp, lineHeight = 42.sp))
        Meta("Checked on this phone. Nothing about it was uploaded.", Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(18.dp))
        when {
            error != null -> Body(error!!, color = burrow.redPen)
            r == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = burrow.ink, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Body("looking closely…", color = burrow.pencil)
            }
            else -> {
                Polaroid(r, showCrop = crop)
                Spacer(Modifier.height(20.dp))
                var n = 0
                if (r.intimate) Finding(++n, "this looks intimate", "think twice about who'll see it", null, false) {}
                if (r.sensitive != null) {
                    Finding(++n, "${r.sensitive} in the picture", if (r.crop != null) "crop to the main subject to leave it out" else "readable at full size", if (r.crop != null) "crop it out" else null, crop) { crop = it }
                }
                if (r.faces.isNotEmpty()) {
                    Finding(++n, if (r.faces.size == 1) "1 face" else "${r.faces.size} faces", "you can blur them before sending", "blur faces", blur) { blur = it }
                }
                if (r.hasLocation || r.cameraData != null) {
                    Finding(
                        ++n,
                        if (r.hasLocation) "where it was taken, in the photo's data" else "camera details in the photo's data",
                        listOfNotNull(if (r.hasLocation) "to a few metres" else null, r.cameraData).joinToString(" · "),
                        "remove it", strip,
                    ) { strip = it }
                }
                if (n == 0) {
                    MarkedText("looks fine to send.", mark = "fine", style = Hand.title)
                    Meta("No faces, cards, documents or location data found.")
                }
            }
        }
    }
}

/** The photo as a polaroid, with faces circled and the suggested crop drawn on it. */
@Composable
private fun Polaroid(r: ShareReport, showCrop: Boolean) {
    val ink = burrow.ink
    val red = burrow.redPen
    val marker = burrow.marker
    Snapshot(Modifier.fillMaxWidth(), tilt = -1.2f) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(r.bitmap.width.toFloat() / r.bitmap.height)) {
                Image(r.bitmap.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Fit)
                Canvas(Modifier.matchParentSize()) {
                    r.faces.forEach { f ->
                        val c = Offset((f.left + f.right) / 2 * size.width, (f.top + f.bottom) / 2 * size.height)
                        val rad = maxOf((f.right - f.left) * size.width, (f.bottom - f.top) * size.height) * 0.62f
                        drawCircle(marker, rad, c, style = Stroke(3.dp.toPx()))
                    }
                    val c = r.crop
                    if (showCrop && c != null) {
                        drawRect(
                            ink, Offset(c.left * size.width, c.top * size.height),
                            Size((c.right - c.left) * size.width, (c.bottom - c.top) * size.height),
                            style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                        )
                    }
                    if (r.sensitive != null && !showCrop) drawCircle(red, size.minDimension * 0.06f, Offset(size.width * 0.92f, size.height * 0.1f), style = Stroke(2.dp.toPx()))
                }
            }
            Meta("checked on this phone", Modifier.padding(top = 8.dp, start = 4.dp, bottom = 4.dp))
        }
    }
}

@Composable
private fun Finding(n: Int, title: String, body: String, fix: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Text("$n", Modifier.handCircle(burrow.redPen, seed = n), style = Hand.label, color = burrow.redPen)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Hand.door.copy(fontSize = 24.sp), color = burrow.ink)
            if (body.isNotBlank()) Meta(body)
        }
        if (fix != null) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(fix, style = Hand.small, color = burrow.pencil)
                HandSwitch(on, onChange)
            }
        }
    }
}
