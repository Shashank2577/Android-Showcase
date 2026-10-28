package com.showcase.desertant.meetings

import android.content.Context
import android.net.Uri
import ai.desertant.voz.Transcription
import ai.desertant.voz.Word
import com.showcase.desertant.app
import com.showcase.desertant.audio.AudioDecoder
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.VOZ_LANGUAGES
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.Moment
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.TopicScore
import com.showcase.desertant.data.Utterance
import com.showcase.desertant.data.VoiceSignature
import com.showcase.desertant.people.ProfileMatcher
import com.showcase.desertant.people.VoiceSignatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns a recording into a finished meeting: language, transcript, speakers,
 * who said what, topics, highlights, follow-ups and voice matches to people —
 * every step on the device.
 */
class MeetingProcessor(private val context: Context) {
    private val hub = context.app.hub
    private val db = context.app.db

    suspend fun process(meetingId: Long, report: suspend (String) -> Unit) {
        val meeting = db.meetings().get(meetingId) ?: return
        suspend fun step(text: String) {
            db.meetings().setStatus(meetingId, MeetingStatus.PROCESSING, text)
            report(text)
        }

        step("Reading the recording…")
        val audio = AudioDecoder.decode(context, Uri.fromFile(File(meeting.audioPath)), MAX_SECONDS, targetRate = RATE)
        val samples = audio.samples
        val rate = RATE.toDouble()

        step("Detecting the language…")
        hub.ensureReady(ModelKind.EAR)
        // Ear looks at up to three 30 s windows; handing it the first few minutes
        // avoids copying an hour of audio into its input.
        val language = hub.ear.identify(samples.copyOfRange(0, minOf(samples.size, RATE * 240)), rate)
        val lang = language.language
        val supported = lang == null || lang in VOZ_LANGUAGES

        step("Transcribing (getting the speech model ready the first time takes a while)…")
        hub.ensureReady(ModelKind.VOZ)
        // Long recordings go in ~10 minute pieces cut at pauses, so the SDK's input
        // copy stays small; word times are shifted back onto the full timeline.
        val pieces = quietChunks(samples, RATE)
        val words = ArrayList<Word>()
        val text = StringBuilder()
        pieces.forEachIndexed { i, (from, to) ->
            if (pieces.size > 1) step("Transcribing part ${i + 1} of ${pieces.size}…")
            val offset = from.toDouble() / RATE
            val part = hub.voz.transcribe(samples.copyOfRange(from, to), rate)
            part.words.forEach { words += Word(it.text, it.start + offset, it.end + offset) }
            if (part.text.isNotBlank()) text.append(part.text.trim()).append(' ')
        }
        val transcript = Transcription(text.toString().trim(), words, audio.durationSec)

        step("Working out who spoke when…")
        hub.ensureReady(ModelKind.WHO)
        val diarization = hub.who.diarize(samples, rate)
        val turns = diarization.turns
        val utterances = utterances(transcript.words, turns)
        val sentences = sentences(utterances)

        step("Finding topics and highlights…")
        hub.ensureReady(ModelKind.GIST)
        val gist = hub.gist
        val fullText = transcript.text
        val topics = if (fullText.split(' ').size >= 15) {
            gist.classify(fullText.take(GIST_CHARS), topK = 4).map { TopicScore(it.slug, it.name, it.score) }
        } else emptyList()
        val topicSlugs = topics.map { it.slug }
        val keyMoments = if (topicSlugs.isEmpty()) emptyList() else highlights(sentences) { text ->
            val scores = gist.scores(text)
            topicSlugs.maxOf { scores[it] ?: 0.0 }
        }
        val followUps = followUps(sentences)

        step("Recognizing voices…")
        val speakerIndices = (0 until diarization.speakerCount).filter { s -> turns.any { it.speaker == s } }
        val profiles = db.profiles().allNow()
        val signatures = speakerIndices.associateWith { s -> voiceOf(samples, turns.filter { it.speaker == s }) }
        val matcher = ProfileMatcher(profiles, signatures.values.filterNotNull())
        val speakers = speakerIndices.map { s ->
            val said = utterances.filter { it.speaker == s }
            val text = said.joinToString(" ") { it.text }
            val speakerTopics = if (text.split(' ').size >= 25) {
                gist.classify(text.take(GIST_CHARS), topK = 3).map { TopicScore(it.slug, it.name, it.score) }
            } else emptyList()
            val signature = signatures[s]
            val match = signature?.let { matcher.best(it) }
            MeetingSpeakerEntity(
                meetingId = meetingId,
                index = s,
                suggestedProfileId = match?.first,
                suggestionScore = match?.second,
                talkSec = turns.filter { it.speaker == s }.sumOf { it.end - it.start },
                words = text.split(' ').count { it.isNotBlank() },
                signatureJson = signature?.let { StoreJson.encodeToString(VoiceSignature.serializer(), it) },
                topicsJson = StoreJson.encodeToString(TOPICS, speakerTopics),
                language = lang,
                sampleQuote = quoteOf(said),
            )
        }

        val title = if (meeting.titleEdited) meeting.title else autoTitle(topics, meeting.createdAt)
        db.speakers().deleteForMeeting(meetingId)
        db.speakers().insert(speakers)
        db.meetings().update(
            meeting.copy(
                title = title,
                durationSec = audio.durationSec,
                status = MeetingStatus.READY,
                progress = null,
                error = null,
                language = lang,
                languageSupported = supported,
                transcriptText = fullText,
                utterancesJson = StoreJson.encodeToString(UTTERANCES, utterances),
                topicsJson = StoreJson.encodeToString(TOPICS, topics),
                highlightsJson = StoreJson.encodeToString(MOMENTS, keyMoments),
                followUpsJson = StoreJson.encodeToString(MOMENTS, followUps),
                speakerCount = speakers.size,
            ),
        )
    }

    /** Signature from up to 90 s of a speaker's own turns. */
    private suspend fun voiceOf(samples: FloatArray, turns: List<ai.desertant.who.Turn>): VoiceSignature? =
        withContext(Dispatchers.Default) {
            val pieces = ArrayList<FloatArray>()
            var total = 0
            for (t in turns.sortedByDescending { it.end - it.start }) {
                if (t.end - t.start < 0.6) continue
                val from = (t.start * RATE).toInt().coerceIn(0, samples.size)
                val to = (t.end * RATE).toInt().coerceIn(from, samples.size)
                pieces += samples.copyOfRange(from, to)
                total += to - from
                if (total > RATE * 90) break
            }
            if (total < RATE * 2) return@withContext null
            val joined = FloatArray(total)
            var at = 0
            pieces.forEach { it.copyInto(joined, at); at += it.size }
            VoiceSignatures.compute(joined)
        }

    private fun quoteOf(said: List<Utterance>): String? =
        sentences(said).map { it.text }.filter { it.split(' ').size in 6..30 }.maxByOrNull { it.length }

    private fun autoTitle(topics: List<TopicScore>, createdAt: Long): String {
        val day = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(createdAt))
        val topic = topics.firstOrNull()?.name
        return if (topic != null) "$topic · $day" else "Conversation · $day"
    }

    companion object {
        const val RATE = 16_000
        /** Two hours at 16 kHz is ~460 MB of floats; longer recordings are cut. */
        const val MAX_SECONDS = 2 * 60 * 60.0
        private const val GIST_CHARS = 6000

        val UTTERANCES = kotlinx.serialization.builtins.ListSerializer(Utterance.serializer())
        val TOPICS = kotlinx.serialization.builtins.ListSerializer(TopicScore.serializer())
        val MOMENTS = kotlinx.serialization.builtins.ListSerializer(Moment.serializer())
    }
}
