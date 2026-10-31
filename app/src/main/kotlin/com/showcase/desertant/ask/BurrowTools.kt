package com.showcase.desertant.ask

import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.AppDatabase
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSpeakerEntity
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.ui.meetings.decodeMoments
import com.showcase.desertant.ui.meetings.decodeTopics
import com.showcase.desertant.ui.meetings.decodeUtterances
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Burrow's own data as tools a small on-device model can call. Everything here
 * reads (or, for the two that change things, writes) the local database;
 * nothing is sent anywhere.
 *
 * Results are kept small on purpose — a 2B model's context is precious — and
 * use plain words ("date": "Thu 30 Oct") rather than ids where it can.
 */
class BurrowTools(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ToolBox {

    override val specs = listOf(
        ToolSpec(
            "find_person", "find a person",
            spec(
                "find_person", "Look up people the user has named in their talks. Use it first when a question names someone.",
                "name" to "string" to "The person's name or first name, or \"me\" for the user.",
                required = listOf("name"),
            ),
        ),
        ToolSpec(
            "search_talks", "search talks",
            spec(
                "search_talks", "Search the user's recorded talks (meetings and calls). Returns matching talks with the lines that match.",
                "query" to "string" to "Words to look for in what was said, the title or the topics. Leave out to list recent talks.",
                "person" to "string" to "Only talks this person spoke in.",
                "days" to "integer" to "Only talks from the last this many days.",
            ),
        ),
        ToolSpec(
            "follow_ups", "find follow-ups",
            spec(
                "follow_ups", "Promises and to-dos said in talks, like \"I'll send the budget by Friday\".",
                "person" to "string" to "Only things this person said.",
                "talk_id" to "integer" to "Only this talk.",
                "days" to "integer" to "Only talks from the last this many days.",
            ),
        ),
        ToolSpec(
            "talk_details", "open a talk",
            spec(
                "talk_details", "Everything about one talk: who was there, topics, highlights, follow-ups and the start of the transcript.",
                "talk_id" to "integer" to "The talk's id from search_talks.",
                required = listOf("talk_id"),
            ),
        ),
        ToolSpec(
            "find_photos", "search photos",
            spec(
                "find_photos", "Find photos on the phone by what is in them, like \"dog\", \"beach\", \"receipt\" or \"smiling\".",
                "query" to "string" to "One or two words for what's in the photo.",
                required = listOf("query"),
            ),
        ),
        ToolSpec(
            "rename_talk", "rename a talk",
            spec(
                "rename_talk", "Give a talk a new title. Only when the user asks to rename it.",
                "talk_id" to "integer" to "The talk's id.",
                "title" to "string" to "The new title.",
                required = listOf("talk_id", "title"),
            ),
            changesThings = true,
        ),
        ToolSpec(
            "start_recording", "start recording",
            spec("start_recording", "Open the recorder to record a new talk. Only when the user asks to record."),
            changesThings = true,
        ),
    )

    override suspend fun describe(name: String, args: Map<String, Any?>): String = when (name) {
        "rename_talk" -> {
            val m = db.meetings().get(args.long("talk_id") ?: -1)
            "rename “${m?.title ?: "this talk"}” to “${args.str("title")}”"
        }
        "start_recording" -> "open the recorder"
        else -> name
    }

    override suspend fun run(name: String, args: Map<String, Any?>): ToolResult = when (name) {
        "find_person" -> findPerson(args.str("name") ?: throw IllegalArgumentException("which name?"))
        "search_talks" -> searchTalks(args.str("query"), args.str("person"), args.long("days"))
        "follow_ups" -> followUps(args.str("person"), args.long("talk_id"), args.long("days"))
        "talk_details" -> talkDetails(args.long("talk_id") ?: throw IllegalArgumentException("which talk?"))
        "find_photos" -> findPhotos(args.str("query") ?: throw IllegalArgumentException("photos of what?"))
        "rename_talk" -> renameTalk(args.long("talk_id") ?: throw IllegalArgumentException("which talk?"), args.str("title") ?: throw IllegalArgumentException("what title?"))
        "start_recording" -> ToolResult("""{"done":true,"note":"The recorder is open."}""", "recorder opened", openRoute = "record")
        else -> throw IllegalArgumentException("there is no tool called $name")
    }

    // ---- people ----

    private class Index(val profiles: List<ProfileEntity>, val talks: List<MeetingEntity>, val speakers: List<MeetingSpeakerEntity>) {
        val byId = profiles.associateBy { it.id }
        fun speakersOf(talk: Long) = speakers.filter { it.meetingId == talk }
        fun nameOf(talk: Long, index: Int?): String? {
            val s = speakers.firstOrNull { it.meetingId == talk && it.index == index } ?: return null
            return s.profileId?.let { byId[it]?.name }
        }
        fun peopleIn(talk: Long) = speakersOf(talk).mapNotNull { it.profileId?.let(byId::get) }.distinctBy { it.id }
    }

    private suspend fun index(): Index {
        val talks = db.meetings().byStatus(MeetingStatus.READY)
        return Index(db.profiles().allNow(), talks, db.speakers().forMeetingsNow(talks.map { it.id }))
    }

    private fun Index.match(name: String): List<ProfileEntity> {
        val q = name.trim().lowercase()
        if (q in setOf("me", "i", "myself", "my")) return profiles.filter { it.isMe }
        val exact = profiles.filter { it.name.lowercase() == q }
        if (exact.isNotEmpty()) return exact
        val words = q.split(' ').filter { it.isNotBlank() }
        return profiles.filter { p -> val n = p.name.lowercase().split(' '); words.all { w -> n.any { it.startsWith(w) } } }
    }

    private suspend fun findPerson(name: String): ToolResult {
        val ix = index()
        val people = ix.match(name)
        if (people.isEmpty()) {
            val known = ix.profiles.joinToString(", ") { it.name }
            return ToolResult(obj("people" to JsonArray(emptyList()), "note" to "No one called $name. People Burrow knows: ${known.ifEmpty { "none yet" }}."), "no one called $name")
        }
        val out = people.map { p ->
            val mine = ix.speakers.filter { it.profileId == p.id }
            val talks = ix.talks.filter { t -> mine.any { it.meetingId == t.id } }
            val topics = mine.flatMap { decodeTopics(it.topicsJson) }.groupBy { it.name }
                .entries.sortedByDescending { e -> e.value.sumOf { it.score } }.take(4).map { it.key.lowercase() }
            obj(
                "name" to p.name,
                "is_me" to p.isMe,
                "talks" to talks.size,
                "last_talk" to talks.maxByOrNull { it.createdAt }?.let { "${it.title}, ${day(it.createdAt)}" },
                "talks_about" to topics.joinToString(", ").ifEmpty { null },
            )
        }
        val summary = people.joinToString(", ") { p -> "${p.name} · ${talks(ix.speakers.filter { it.profileId == p.id }.map { it.meetingId }.distinct().size)}" }
        return result(objOf("people" to JsonArray(out.map(::parse))), summary, people.map { AskSource.Person(it.id, it.name) })
    }

    // ---- talks ----

    private fun Index.inScope(person: String?, days: Long?): Pair<List<MeetingEntity>, List<ProfileEntity>?> {
        val who = person?.takeIf { it.isNotBlank() }?.let { match(it) }
        val since = days?.takeIf { it > 0 }?.let { now() - it * 86_400_000L }
        val talks = talks.filter { t ->
            (since == null || t.createdAt >= since) &&
                (who == null || speakersOf(t.id).any { s -> who.any { it.id == s.profileId } })
        }.sortedByDescending { it.createdAt }
        return talks to who
    }

    private suspend fun searchTalks(query: String?, person: String?, days: Long?): ToolResult {
        val ix = index()
        val (scope, who) = ix.inScope(person, days)
        if (who != null && who.isEmpty()) return result(objOf("talks" to JsonArray(emptyList()), "note" to JsonPrimitive("No one called $person.")), "no one called $person")
        val words = words(query)
        val sources = mutableListOf<AskSource>()
        val hits = scope.mapNotNull { t ->
            val lines = decodeUtterances(t.utterancesJson)
            val topics = decodeTopics(t.topicsJson).map { it.name.lowercase() }
            val matching = if (words.isEmpty()) emptyList() else lines.filter { u -> words.any { it in u.text.lowercase() } }
            val titleHit = words.any { w -> w in t.title.lowercase() || topics.any { w in it } }
            if (words.isNotEmpty() && matching.isEmpty() && !titleHit) return@mapNotNull null
            val score = matching.size + if (titleHit) 2 else 0
            Triple(t, matching, score)
        }.sortedWith(compareByDescending<Triple<MeetingEntity, *, Int>> { it.third }.thenByDescending { it.first.createdAt }).take(5)

        val out = hits.map { (t, matching, _) ->
            sources += AskSource.Talk(t.id, t.title)
            matching.take(3).forEach { sources += AskSource.Talk(t.id, t.title, it.start, it.text) }
            objOf(
                "talk_id" to JsonPrimitive(t.id),
                "title" to JsonPrimitive(t.title),
                "date" to JsonPrimitive(day(t.createdAt)),
                "minutes" to JsonPrimitive((t.durationSec / 60).toInt()),
                "people" to JsonPrimitive(ix.peopleIn(t.id).joinToString(", ") { it.name }),
                "topics" to JsonPrimitive(decodeTopics(t.topicsJson).take(4).joinToString(", ") { it.name.lowercase() }),
                "lines" to JsonArray(matching.take(3).map { u -> line(formatClock(u.start), ix.nameOf(t.id, u.speaker), u.text) }),
            )
        }
        val summary = when (hits.size) {
            0 -> "no talks found"
            1 -> hits[0].first.title
            else -> "${hits.size} talks: " + hits.take(2).joinToString(", ") { it.first.title } + if (hits.size > 2) "…" else ""
        }
        return result(objOf("talks" to JsonArray(out)), summary, sources)
    }

    private suspend fun followUps(person: String?, talkId: Long?, days: Long?): ToolResult {
        val ix = index()
        val (scope, who) = ix.inScope(person, days)
        if (who != null && who.isEmpty()) return result(objOf("follow_ups" to JsonArray(emptyList()), "note" to JsonPrimitive("No one called $person.")), "no one called $person")
        val sources = mutableListOf<AskSource>()
        val items = scope.filter { talkId == null || it.id == talkId }.flatMap { t ->
            decodeMoments(t.followUpsJson).filter { m ->
                who == null || ix.speakersOf(t.id).firstOrNull { it.index == m.speaker }?.profileId in who.map { it.id }
            }.map { m -> t to m }
        }.take(8)
        val out = items.map { (t, m) ->
            sources += AskSource.Talk(t.id, t.title, m.start, m.text)
            objOf(
                "talk_id" to JsonPrimitive(t.id),
                "talk" to JsonPrimitive("${t.title}, ${day(t.createdAt)}"),
                "at" to JsonPrimitive(formatClock(m.start)),
                "who" to JsonPrimitive(ix.nameOf(t.id, m.speaker) ?: "someone not named yet"),
                "text" to JsonPrimitive(m.text),
            )
        }
        return result(objOf("follow_ups" to JsonArray(out)), if (items.size == 1) "1 follow-up" else "${items.size} follow-ups", sources)
    }

    private suspend fun talkDetails(id: Long): ToolResult {
        val ix = index()
        val t = db.meetings().get(id) ?: throw IllegalArgumentException("there's no talk $id")
        if (t.status != MeetingStatus.READY) {
            return result(objOf("title" to JsonPrimitive(t.title), "note" to JsonPrimitive("This talk's notes aren't ready yet.")), "notes not ready", listOf(AskSource.Talk(t.id, t.title)))
        }
        val total = ix.speakersOf(id).sumOf { it.talkSec }.coerceAtLeast(1.0)
        val people = ix.speakersOf(id).sortedByDescending { it.talkSec }.map { s ->
            val n = s.profileId?.let { ix.byId[it]?.name } ?: "speaker ${s.index + 1} (not named)"
            "$n ${(s.talkSec * 100 / total).toInt()}%"
        }
        val lines = decodeUtterances(t.utterancesJson)
        val out = objOf(
            "talk_id" to JsonPrimitive(t.id),
            "title" to JsonPrimitive(t.title),
            "date" to JsonPrimitive(day(t.createdAt)),
            "minutes" to JsonPrimitive((t.durationSec / 60).toInt()),
            "language" to JsonPrimitive(t.language ?: "unknown"),
            "who_talked" to JsonPrimitive(people.joinToString(", ")),
            "topics" to JsonPrimitive(decodeTopics(t.topicsJson).joinToString(", ") { it.name.lowercase() }),
            "highlights" to JsonArray(decodeMoments(t.highlightsJson).take(4).map { line(formatClock(it.start), ix.nameOf(id, it.speaker), it.text) }),
            "follow_ups" to JsonArray(decodeMoments(t.followUpsJson).take(5).map { line(formatClock(it.start), ix.nameOf(id, it.speaker), it.text) }),
            "transcript_start" to JsonArray(lines.take(6).map { line(formatClock(it.start), ix.nameOf(id, it.speaker), it.text) }),
        )
        return result(out, "${t.title} · ${people.size} people", listOf(AskSource.Talk(t.id, t.title)))
    }

    // ---- photos ----

    private suspend fun findPhotos(query: String): ToolResult {
        val words = query.lowercase().split(' ', ',').map { it.trim() }.filter { it.length > 1 }
        // Intimate photos never come back from a question, even a matching one.
        val photos = db.photos().analyzed().filter { p ->
            p.intimate != true && words.isNotEmpty() &&
                words.all { w -> p.tags.split(' ').any { it.startsWith(w) || it.contains("_$w") } }
        }.sortedByDescending { it.dateTaken }
        val out = objOf(
            "count" to JsonPrimitive(photos.size),
            "newest" to JsonPrimitive(photos.firstOrNull()?.let { day(it.dateTaken) } ?: "none"),
            "oldest" to JsonPrimitive(photos.lastOrNull()?.let { day(it.dateTaken) } ?: "none"),
            "albums" to JsonPrimitive(photos.groupBy { it.bucketName }.entries.sortedByDescending { it.value.size }.take(3).joinToString(", ") { "${it.key} (${it.value.size})" }),
            "note" to JsonPrimitive(if (photos.isEmpty()) "None found. Only photos Burrow has scanned (photos page → scan) can be searched." else "Burrow shows these photos under the answer."),
        )
        val sources = if (photos.isEmpty()) emptyList() else listOf(AskSource.Photos(query, photos.size, photos.take(6).map { it.uri }))
        return result(out, if (photos.size == 1) "1 photo" else "${photos.size} photos", sources)
    }

    // ---- changes (confirmed on screen first) ----

    private suspend fun renameTalk(id: Long, title: String): ToolResult {
        val t = db.meetings().get(id) ?: throw IllegalArgumentException("there's no talk $id")
        val clean = title.trim().take(80)
        require(clean.isNotEmpty()) { "the new title is empty" }
        db.meetings().rename(id, clean)
        return result(objOf("done" to JsonPrimitive(true), "old_title" to JsonPrimitive(t.title), "new_title" to JsonPrimitive(clean)), "renamed to “$clean”", listOf(AskSource.Talk(id, clean)))
    }

    // ---- helpers ----

    private fun day(millis: Long): String {
        val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        val base = d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
        return when (d) {
            today -> "today ($base)"
            today.minusDays(1) -> "yesterday ($base)"
            else -> base
        }
    }

    private fun talks(n: Int) = if (n == 1) "1 talk" else "$n talks"

    private fun line(at: String, who: String?, text: String) = objOf("at" to JsonPrimitive(at), "who" to JsonPrimitive(who ?: "someone not named yet"), "text" to JsonPrimitive(text))

    private fun words(q: String?): List<String> =
        q.orEmpty().lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 && it !in STOP }.map { it.removeSuffix("s").ifEmpty { it } }

    companion object {
        private val STOP = setOf("the", "and", "about", "what", "when", "who", "did", "does", "with", "for", "that", "this", "talk", "talks", "said", "say")

        /** A compact OpenAPI-style function description, the shape LiteRT-LM expects. */
        private fun spec(name: String, description: String, vararg params: Pair<Pair<String, String>, String>, required: List<String> = emptyList()): String {
            val props = params.joinToString(",") { (nt, d) -> "${jsonString(nt.first)}:{\"type\":${jsonString(nt.second)},\"description\":${jsonString(d)}}" }
            val req = required.joinToString(",") { jsonString(it) }
            return """{"name":${jsonString(name)},"description":${jsonString(description)},"parameters":{"type":"object","properties":{$props},"required":[$req]}}"""
        }

        private fun objOf(vararg pairs: Pair<String, JsonElement>) = JsonObject(pairs.toMap())

        private fun result(json: JsonObject, summary: String, sources: List<AskSource> = emptyList()) = ToolResult(json.toString(), summary, sources)

        private fun obj(vararg pairs: Pair<String, Any?>) = JsonObject(
            pairs.filter { it.second != null }.associate { (k, v) ->
                k to when (v) {
                    is JsonElement -> v
                    is Number -> JsonPrimitive(v)
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                }
            },
        ).toString()

        private fun parse(s: String): JsonElement = kotlinx.serialization.json.Json.parseToJsonElement(s)

        private fun Map<String, Any?>.str(k: String) = (this[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        /** Models send numbers as numbers or as strings; take either. */
        private fun Map<String, Any?>.long(k: String): Long? = when (val v = this[k]) {
            is Number -> v.toLong()
            is String -> v.trim().toDoubleOrNull()?.toLong()
            else -> null
        }
    }
}
