package com.showcase.desertant.ui.meetings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.Moment
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.TopicScore
import com.showcase.desertant.data.Utterance
import com.showcase.desertant.meetings.MeetingProcessor
import com.showcase.desertant.ui.theme.seriesColor

fun decodeTopics(json: String?): List<TopicScore> =
    json?.let { runCatching { StoreJson.decodeFromString(MeetingProcessor.TOPICS, it) }.getOrNull() }.orEmpty()

fun decodeMoments(json: String?): List<Moment> =
    json?.let { runCatching { StoreJson.decodeFromString(MeetingProcessor.MOMENTS, it) }.getOrNull() }.orEmpty()

fun decodeUtterances(json: String?): List<Utterance> =
    json?.let { runCatching { StoreJson.decodeFromString(MeetingProcessor.UTTERANCES, it) }.getOrNull() }.orEmpty()

/** How a meeting's speaker is shown: their person if linked, else "Speaker N". */
data class SpeakerLook(val name: String, val color: Color, val profile: ProfileEntity?, val speaker: MeetingSpeakerEntity?)

fun lookFor(index: Int?, speakers: List<MeetingSpeakerEntity>, profiles: List<ProfileEntity>): SpeakerLook {
    if (index == null) return SpeakerLook("Transcript", seriesColor(0), null, null)
    val s = speakers.firstOrNull { it.index == index }
    val p = s?.profileId?.let { id -> profiles.firstOrNull { it.id == id } }
    return if (p != null) SpeakerLook(if (p.isMe) "${p.name} (you)" else p.name, seriesColor(p.colorIndex), p, s)
    else SpeakerLook("Speaker ${index + 1}", seriesColor(index + 4), null, s)
}

@Composable
fun TopicPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
