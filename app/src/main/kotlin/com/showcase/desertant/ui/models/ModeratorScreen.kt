package com.showcase.desertant.ui.models

import ai.desertant.moderator.Moderation
import ai.desertant.moderator.Options
import ai.desertant.moderator.Policy
import ai.desertant.moderator.Quality
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.ui.components.ChoiceChips
import com.showcase.desertant.ui.components.LabeledSlider
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.PickImageButton
import com.showcase.desertant.ui.components.ScoreBar
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.prettify
import com.showcase.desertant.ui.components.rememberRunner

@Composable
fun ModeratorScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val runner = rememberRunner(ModelKind.MODERATOR)
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var threshold by rememberSaveable { mutableFloatStateOf(0.5f) }
    var policy by rememberSaveable { mutableStateOf(Policy.STANDARD) }
    var quality by rememberSaveable { mutableStateOf(Quality.ACCURATE) }
    var result by remember { mutableStateOf<Moderation?>(null) }
    var revealed by remember { mutableStateOf(false) }

    fun analyze(image: Bitmap) = runner.run {
        result = null
        revealed = false
        result = hub.moderator.analyze(image, Options(threshold.toDouble(), policy, quality))
    }

    ModelScreen(ModelKind.MODERATOR, onBack, runner) {
        SectionCard("Options") {
            LabeledSlider("Threshold", threshold, { threshold = it }, 0.1f..0.95f, "%.2f".format(threshold))
            Text("Policy", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(Policy.entries, policy, { policy = it }) { prettify(it.name.lowercase()) }
            Text("Quality", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(Quality.entries, quality, { quality = it }) { prettify(it.name.lowercase()) }
        }
        SectionCard("Image") {
            PickImageButton(enabled = !runner.busy, onError = { runner.error = it }) {
                bitmap = it
                analyze(it)
            }
            bitmap?.let { b ->
                // Until checked — and while flagged — the preview stays blurred; tap to reveal.
                val hide = !revealed && (result == null || result?.isNSFW == true)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clickable { revealed = !revealed },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        b.asImageBitmap(), null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (hide && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(40.dp) else Modifier),
                    )
                    if (hide) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.92f)))
                        }
                        Text(
                            if (result == null) "Checking…" else "Flagged — tap to reveal",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Button(enabled = !runner.busy, onClick = { analyze(b) }, modifier = Modifier.fillMaxWidth()) { Text("Re-run with these options") }
            }
        }
        result?.let { m ->
            SectionCard(if (m.isNSFW) "Flagged as NSFW" else "Safe") {
                ScoreBar("Overall score", m.score, color = if (m.isNSFW) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                ScoreBar("Nudity", m.regions.nude)
                ScoreBar("Nipples", m.regions.nipples)
                ScoreBar("Genitals", m.regions.genitals)
                ScoreBar("Buttocks", m.regions.buttocks)
                ScoreBar("Sex act", m.regions.sexAct)
            }
        }
    }
}
