package com.showcase.desertant.meetings

import ai.desertant.voz.Transcription
import ai.desertant.voz.Word
import ai.desertant.who.Turn
import android.content.Context
import android.net.Uri
import com.showcase.desertant.app
import com.showcase.desertant.audio.AudioDecoder
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.languageName
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.Moment
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.TopicScore
import com.showcase.desertant.data.Utterance
import com.showcase.desertant.data.VoiceSignature
import com.showcase.desertant.people.ProfileMatcher
import com.showcase.desertant.people.VoiceSignatures
import com.showcase.desertant.privacy.LedgerEntry
import com.showcase.desertant.speech.SpeechPacks
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        val uri = Uri.fromFile(File(meeting.audioPath))
        val total = AudioDecoder.durationSec(context, uri).takeIf { it > 0 } ?: meeting.durationSec
        val rate = RATE.toDouble()
        hub.ensureReady(ModelKind.EAR)
        step("Getting the speech models ready (the first time downloads them)…")
        hub.ensureReady(ModelKind.WHO)

        // Work through the recording ~10 minutes at a time, cut at pauses, so memory
        // stays the same for a 5-minute call and a 2-hour meeting. Speakers found in
        // each window are linked to the same person across windows by voice.
        var language: String? = null
        var pack: SpeechPacks.Pack? = null
        val words = ArrayList<Word>()
        val text = StringBuilder()
        val turns = ArrayList<Turn>()
        val voices = ArrayList<VoiceSignature?>() // per global speaker
        var start = 0.0
        var window = 0
        val windows = kotlin.math.ceil(total / WINDOW_SEC).toInt().coerceAtLeast(1)
        while (start < total - 0.5 && start < MAX_SECONDS) {
            window++
            val label = if (windows > 1) " (part $window of ~$windows)" else ""
            step("Reading the recording$label…")
            val clip = AudioDecoder.decode(context, uri, maxSeconds = WINDOW_SEC + TAIL_SEC, targetRate = RATE, startSec = start)
            val last = start + clip.durationSec >= total - 0.5 || clip.durationSec < WINDOW_SEC
            val cut = if (last) clip.samples.size
                else quietCut(clip.samples, RATE, (WINDOW_SEC * RATE).toInt(), (TAIL_SEC * RATE).toInt())
            val piece = if (cut == clip.samples.size) clip.samples else clip.samples.copyOfRange(0, cut)
            if (piece.size < RATE / 2) break

            if (language == null) {
                // The user's own choice wins; otherwise Ear listens to up to three 30 s windows of the start.
                language = meeting.language?.takeIf { meeting.languageChosen } ?: run {
                    step("Detecting the language…")
                    hub.ear.identify(piece.copyOfRange(0, minOf(piece.size, RATE * 240)), rate).language
                }
                pack = SpeechPacks.route(language)
                pack?.let { p ->
                    if (!SpeechPacks.isReady(context, p)) {
                        db.meetings().get(meetingId)?.let { db.meetings().update(it.copy(language = language, languageSupported = false)) }
                        val name = if (p.kind == SpeechPacks.Kind.UNIVERSAL) languageName(language) else p.name
                        throw IllegalStateException(
                            "This talk is in $name. Download the ${p.name.lowercase()} speech pack (about ${p.sizeMb} MB, once) and Burrow can write its notes.",
                        )
                    }
                }
            }

            step("Transcribing$label…")
            val extra = pack
            if (extra != null) {
                // Voz doesn't speak this language: an IndicConformer or Omnilingual pack does, on the phone.
                val said = SpeechPacks.transcribe(context, extra, piece)
                said.forEach { words += Word(it.text, it.start + start, it.end + start) }
                if (said.isNotEmpty()) text.append(said.joinToString(" ") { it.text }).append(' ')
            } else {
                // Only now: a talk in another language never needs Voz on the phone.
                hub.ensureReady(ModelKind.VOZ)
                val part = hub.voz.transcribe(piece, rate)
                part.words.forEach { words += Word(it.text, it.start + start, it.end + start) }
                if (part.text.isNotBlank()) text.append(part.text.trim()).append(' ')
            }

            step("Working out who spoke when$label…")
            val local = hub.who.diarize(piece, rate)
            val mapping = linkSpeakers(local.turns, piece, voices)
            local.turns.forEach { t -> turns += Turn(mapping.getValue(t.speaker), t.start + start, t.end + start) }

            start += piece.size.toDouble() / RATE
            if (last) break
        }
        SpeechPacks.closeRecognizer()
        val lang = language
        // Topics and follow-ups are tuned for the languages Voz covers; skip them rather than guess.
        val european = pack == null
        val transcript = Transcription(text.toString().trim(), words, start)
        val durationSec = start
        val speakerCount = voices.size
        val utterances = utterances(transcript.words, turns)
        val sentences = sentences(utterances)

        step("Finding topics and highlights…")
        hub.ensureReady(ModelKind.GIST)
        val gist = hub.gist
        val fullText = transcript.text
        val topics = if (european && fullText.split(' ').size >= 15) {
            gist.classify(fullText.take(GIST_CHARS), topK = 4).map { TopicScore(it.slug, it.name, it.score) }
        } else emptyList()
        val topicSlugs = topics.map { it.slug }
        val keyMoments = if (topicSlugs.isEmpty()) emptyList() else highlights(sentences) { text ->
            val scores = gist.scores(text)
            topicSlugs.maxOf { scores[it] ?: 0.0 }
        }
        val followUps = if (european) followUps(sentences) else emptyList()

        step("Recognizing voices…")
        val speakerIndices = (0 until speakerCount).filter { s -> turns.any { it.speaker == s } }
        val profiles = db.profiles().allNow()
        val signatures = speakerIndices.associateWith { voices[it] }
        val matcher = ProfileMatcher(profiles, signatures.values.filterNotNull())
        // On "Process again", people the user already confirmed here are relinked when their voice matches.
        val confirmed = db.speakers().forMeetingNow(meetingId).mapNotNull { it.profileId }.toSet()
        val speakers = speakerIndices.map { s ->
            val said = utterances.filter { it.speaker == s }
            val text = said.joinToString(" ") { it.text }
            val speakerTopics = if (european && text.split(' ').size >= 25) {
                gist.classify(text.take(GIST_CHARS), topK = 3).map { TopicScore(it.slug, it.name, it.score) }
            } else emptyList()
            val signature = signatures[s]
            val match = signature?.let { matcher.best(it) }
            val relink = match?.first?.takeIf { it in confirmed }
            MeetingSpeakerEntity(
                meetingId = meetingId,
                index = s,
                profileId = relink,
                suggestedProfileId = if (relink == null) match?.first else null,
                suggestionScore = if (relink == null) match?.second else null,
                talkSec = turns.filter { it.speaker == s }.sumOf { it.end - it.start },
                words = text.split(' ').count { it.isNotBlank() },
                signatureJson = signature?.let { StoreJson.encodeToString(VoiceSignature.serializer(), it) },
                topicsJson = StoreJson.encodeToString(TOPICS, speakerTopics),
                language = lang,
                sampleQuote = quoteOf(said),
            )
        }

        val title = if (meeting.titleEdited) meeting.title else autoTitle(topics, meeting.createdAt, lang?.takeIf { pack != null }?.let { languageName(it).substringBefore(" (") })
        db.speakers().deleteForMeeting(meetingId)
        db.speakers().insert(speakers)
        db.meetings().update(
            meeting.copy(
                title = title,
                durationSec = durationSec,
                status = MeetingStatus.READY,
                progress = null,
                error = null,
                language = lang,
                // Every language that reaches this point had a model that speaks it.
                languageSupported = true,
                transcriptText = fullText,
                utterancesJson = StoreJson.encodeToString(UTTERANCES, utterances),
                topicsJson = StoreJson.encodeToString(TOPICS, topics),
                highlightsJson = StoreJson.encodeToString(MOMENTS, keyMoments),
                followUpsJson = StoreJson.encodeToString(MOMENTS, followUps),
                speakerCount = speakers.size,
            ),
        )
        context.app.ledger.add(LedgerEntry.TALKS_TRANSCRIBED)
    }

    /**
     * Map this window's speakers onto the meeting's speakers: each local voice
     * joins the most similar known voice not already taken in this window, or
     * becomes a new speaker. [voices] grows and is refined as it goes.
     */
    private suspend fun linkSpeakers(local: List<Turn>, piece: FloatArray, voices: MutableList<VoiceSignature?>): Map<Int, Int> {
        val ids = local.map { it.speaker }.distinct().sortedByDescending { s -> local.filter { it.speaker == s }.sumOf { it.end - it.start } }
        val mapping = HashMap<Int, Int>()
        val taken = HashSet<Int>()
        val center = VoiceSignatures.center(voices.filterNotNull())
        for (id in ids) {
            val sig = voiceOf(piece, local.filter { it.speaker == id })
            val best = if (sig == null) null else voices.indices
                .filter { it !in taken && voices[it] != null }
                .map { it to VoiceSignatures.similarity(sig, voices[it]!!, center) }
                .maxByOrNull { it.second }
                ?.takeIf { it.second >= LINK_THRESHOLD }
            val global = when {
                best != null -> best.first.also { voices[it] = VoiceSignatures.merge(voices[it], 1, sig!!) }
                // A new voice, or too little speech to fingerprint: its own speaker.
                else -> voices.size.also { voices += sig }
            }
            taken += global
            mapping[id] = global
        }
        return mapping
    }

    /** Signature from up to 90 s of a speaker's own turns within [samples]. */
    private suspend fun voiceOf(samples: FloatArray, turns: List<Turn>): VoiceSignature? =
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

    private fun autoTitle(topics: List<TopicScore>, createdAt: Long, language: String? = null): String {
        val day = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(createdAt))
        val topic = topics.firstOrNull()?.name
        return if (topic != null) "$topic · $day" else "${language?.let { "$it talk" } ?: "Conversation"} · $day"
    }

    companion object {
        const val RATE = 16_000
        /** Processing stops after three hours. */
        const val MAX_SECONDS = 3 * 60 * 60.0
        /** Each window is ~10 minutes (~38 MB of samples), plus slack to cut at a pause. */
        private const val WINDOW_SEC = 600.0
        private const val TAIL_SEC = 15.0
        /** Voices this alike across windows are the same speaker. */
        private const val LINK_THRESHOLD = 0.68
        private const val GIST_CHARS = 6000

        val UTTERANCES = kotlinx.serialization.builtins.ListSerializer(Utterance.serializer())
        val TOPICS = kotlinx.serialization.builtins.ListSerializer(TopicScore.serializer())
        val MOMENTS = kotlinx.serialization.builtins.ListSerializer(Moment.serializer())
    }
}
