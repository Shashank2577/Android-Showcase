package com.showcase.desertant.ui.models

import ai.desertant.gist.ChannelTopic
import ai.desertant.gist.PostTopics
import ai.desertant.gist.RollupOptions
import ai.desertant.gist.Topic
import ai.desertant.gist.channelTopics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.ui.components.LabeledSlider
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.components.ModelScreen
import com.showcase.desertant.ui.components.ScoreBar
import com.showcase.desertant.ui.components.SectionCard
import com.showcase.desertant.ui.components.rememberRunner

private val GIST_SAMPLES = listOf(
    "The central bank held interest rates steady on Thursday, citing cooling inflation and a resilient labour market.",
    "Our new sourdough recipe uses a 48-hour cold ferment for a more open crumb and deeper flavour.",
    "The striker scored twice in the second half as the home side came back to win the derby 3-2.",
    "Apple's new chip doubles neural engine throughput, making on-device models far more practical.",
    "Découvrez les plus belles randonnées des Alpes, entre lacs glaciaires et refuges d'altitude.",
    "Neue Studie: Regelmäßiger Schlaf senkt das Risiko für Herz-Kreislauf-Erkrankungen deutlich.",
)

private data class TaggedPost(val text: String, val topics: List<Topic>)

@Composable
fun GistScreen(onBack: () -> Unit) {
    val hub = LocalModelHub.current
    val runner = rememberRunner(ModelKind.GIST)
    var text by rememberSaveable { mutableStateOf(GIST_SAMPLES[0]) }
    var topK by rememberSaveable { mutableFloatStateOf(3f) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var tags by remember { mutableStateOf<List<Topic>>(emptyList()) }
    var all by remember { mutableStateOf<List<Topic>>(emptyList()) }
    val channel = remember { mutableStateListOf<TaggedPost>() }
    var rollup by remember { mutableStateOf<List<ChannelTopic>>(emptyList()) }

    val names = remember(channel.size, all) {
        (channel.flatMap { it.topics } + all).associate { it.slug to it.name }
    }

    fun recomputeRollup() {
        rollup = channelTopics(
            channel.map { post -> PostTopics(post.topics.associate { it.slug to it.score }) },
            RollupOptions(minPosts = 2),
        )
    }

    ModelScreen(ModelKind.GIST, onBack, runner) {
        SectionCard("Post or article") {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GIST_SAMPLES.indices.forEach { i -> TextButton(onClick = { text = GIST_SAMPLES[i] }) { Text("${i + 1}") } }
            }
            LabeledSlider("Max topics", topK, { topK = it }, 1f..8f, topK.toInt().toString(), steps = 6)
            Button(
                onClick = {
                    runner.run {
                        tags = hub.gist.classify(text, topK.toInt())
                        all = hub.gist.classify(text, topK = 36, threshold = 0.0)
                    }
                },
                enabled = !runner.busy && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Tag topics") }
        }
        if (tags.isNotEmpty()) {
            SectionCard("Topics") {
                tags.forEach { ScoreBar(it.name, it.score) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show all 36 scores", modifier = Modifier.weight(1f))
                    Switch(checked = showAll, onCheckedChange = { showAll = it })
                }
                if (showAll) all.forEach { ScoreBar(it.name, it.score, color = MaterialTheme.colorScheme.secondary) }
                OutlinedButton(
                    onClick = {
                        channel.add(TaggedPost(text, all))
                        recomputeRollup()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add this post to the channel") }
            }
        }
        SectionCard("Channel roll-up (${channel.size} posts)") {
            Text(
                "Gist's channelTopics() turns per-post scores into what a whole feed is about. Tag a few posts and add them.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (rollup.isEmpty()) {
                Text("Add at least two posts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rollup.forEach { t ->
                ScoreBar("${names[t.slug] ?: t.slug} · ${t.postCount} posts", t.share, color = MaterialTheme.colorScheme.tertiary)
            }
            channel.forEach { Text("• ${it.text}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
            if (channel.isNotEmpty()) {
                TextButton(onClick = { channel.clear(); rollup = emptyList() }) { Text("Clear channel") }
            }
        }
    }
}
