package com.showcase.desertant.photos

import ai.desertant.eye.Analysis
import ai.desertant.eye.Eye
import ai.desertant.eye.Tag
import ai.desertant.eye.Tags
import com.showcase.desertant.data.PhotoDao
import com.showcase.desertant.data.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Near-duplicates of one moment, best shot first. */
data class SimilarGroup(
    val photos: List<PhotoEntity>,
    val bestReason: String,
) {
    val best: PhotoEntity get() = photos.first()
    val extras: List<PhotoEntity> get() = photos.drop(1)
    val key: String get() = best.mediaId.toString()
}

/** Things that shouldn't sit in a gallery anyone can scroll, strongest first. */
enum class PrivateKind(val label: String, val why: String) {
    INTIMATE("intimate", "covered until you look"),
    CARDS("ID & bank cards", "card numbers readable at full size"),
    MEDICAL("medical", "prescriptions, scans, results"),
    RECEIPTS("receipts", "names, card digits, addresses"),
    DOCUMENTS("documents", "letters, forms, contracts"),
}

data class PhotoInsights(
    val analyzed: Int,
    val groups: List<SimilarGroup>,
    val junk: List<PhotoEntity>,
    val screenshots: List<PhotoEntity>,
    val all: List<PhotoEntity>,
    val topTags: List<Pair<String, Int>>,
    val private: Map<PrivateKind, List<PhotoEntity>> = emptyMap(),
) {
    val privateCount: Int get() = private.values.sumOf { it.size }

    val duplicateBytes: Long get() = groups.sumOf { g -> g.extras.filterNot { it.kept }.sumOf { it.sizeBytes } }
    val duplicateCount: Int get() = groups.sumOf { g -> g.extras.count { !it.kept } }
    val junkBytes: Long get() = junk.sumOf { it.sizeBytes }
    val screenshotBytes: Long get() = screenshots.sumOf { it.sizeBytes }

    /** Photos whose tags match every word of [query] ("dog beach", "smiling"). */
    fun search(query: String): List<PhotoEntity> {
        val words = query.lowercase().split(' ', ',').map { it.trim().replace(' ', '_') }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return all.filter { p ->
            val tags = p.tags.split(' ')
            words.all { w -> tags.any { it.startsWith(w) || it.contains("_$w") } }
        }.sortedByDescending { it.dateTaken }
    }

    companion object {
        val EMPTY = PhotoInsights(0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Which private kind a photo is, if any. Moderator's verdict wins over Eye's guess for intimate photos. */
fun privateKind(p: PhotoEntity, a: Analysis): PrivateKind? = when {
    p.privateDismissed -> null
    p.intimate == true || (p.intimate == null && a.has(Tags.NSFW_EXPLICIT)) -> PrivateKind.INTIMATE
    a.has(Tags.ID_CARD) || a.has(Tags.IDENTITY_DOCUMENT) -> PrivateKind.CARDS
    a.has(Tags.MEDICAL_REFERENCE) -> PrivateKind.MEDICAL
    a.has(Tags.RECEIPT) -> PrivateKind.RECEIPTS
    a.has(Tags.DOCUMENT) && a.has(Tags.SENSITIVE) -> PrivateKind.DOCUMENTS
    else -> null
}

/** Eye thinks this might be intimate: worth a second look by Moderator. */
fun mightBeIntimate(a: Analysis): Boolean =
    (a.confidence(Tags.NSFW_EXPLICIT) ?: 0.0) >= 0.2 || (a.confidence(Tags.NSFW_SUGGESTIVE) ?: 0.0) >= 0.35

private val EYES_CLOSED = Tag("eyes_closed")
private val BLURRY = Tag("blurry")
private val SCREENSHOT = Tag("screenshot")

/**
 * Turns stored analyses into cleanup suggestions. Parsed analyses are cached by
 * photo and analysis time, so recomputing while a scan streams in stays cheap.
 */
class PhotoInsightsEngine(private val dao: PhotoDao) {
    private val cache = HashMap<Long, Pair<Long, Analysis>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val insights: Flow<PhotoInsights> = dao.analyzedFlow()
        .conflate()
        .map { compute(it) }
        .flowOn(Dispatchers.Default)

    /** Parse only analyses that are new or changed since the last pass. */
    private suspend fun load(rows: List<PhotoEntity>) {
        val stale = rows.filter { p -> cache[p.mediaId]?.first != (p.analyzedAt ?: 0L) }
        stale.chunked(200).forEach { chunk ->
            val stamps = chunk.associate { it.mediaId to (it.analyzedAt ?: 0L) }
            dao.analysisJson(chunk.map { it.mediaId }).forEach { row ->
                val a = row.analysisJson?.let { runCatching { Analysis.fromJson(it) }.getOrNull() } ?: return@forEach
                cache[row.mediaId] = stamps.getValue(row.mediaId) to a
            }
        }
    }

    private suspend fun compute(rows: List<PhotoEntity>): PhotoInsights {
        load(rows)
        val usable = rows.mapNotNull { p -> cache[p.mediaId]?.second?.let { p to it } }
        if (usable.isEmpty()) return PhotoInsights.EMPTY
        val photos = usable.map { it.first }
        val analyses = usable.map { it.second }

        // With capture times, only shots taken close together count as one moment.
        val groups = Eye.duplicates(analyses, photos.map { it.dateTaken / 1000.0 })
            .map { idx -> SimilarGroup(idx.map { photos[it] }, bestReason(idx.map { analyses[it] })) }
            .filter { g -> g.extras.any { !it.kept } }
            .sortedByDescending { it.best.dateTaken }

        val grouped = groups.flatMap { g -> g.photos.map { it.mediaId } }.toSet()
        val screenshots = usable.filter { (p, a) -> !p.kept && a.has(SCREENSHOT) }.map { it.first }.sortedByDescending { it.dateTaken }
        val shotIds = screenshots.map { it.mediaId }.toSet()
        val junk = photos.filter { !it.kept && it.junkReason != null && it.mediaId !in grouped && it.mediaId !in shotIds }
            .sortedByDescending { it.dateTaken }

        val tagCounts = HashMap<String, Int>()
        photos.forEach { p -> p.tags.split(' ').filter { it.isNotBlank() && it !in BORING_TAGS }.forEach { tagCounts[it] = (tagCounts[it] ?: 0) + 1 } }

        val private = usable.mapNotNull { (p, a) -> privateKind(p, a)?.let { it to p } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sortedByDescending { it.dateTaken } }
            .toSortedMap(compareBy { it.ordinal })

        return PhotoInsights(
            private = private,
            analyzed = photos.size,
            groups = groups,
            junk = junk,
            screenshots = screenshots,
            all = photos,
            topTags = tagCounts.entries.sortedByDescending { it.value }.take(24).map { it.key to it.value },
        )
    }

    /** Why Eye's ranking put the first photo first (eyes open, then faces, then keeper score). */
    private fun bestReason(group: List<Analysis>): String {
        val best = group.first()
        val others = group.drop(1)
        return when {
            !best.has(EYES_CLOSED) && others.any { it.has(EYES_CLOSED) } -> "Everyone's eyes are open"
            !best.has(BLURRY) && others.any { it.has(BLURRY) } -> "The sharp one"
            (best.faces?.isNotEmpty() == true) -> "Best faces in the set"
            else -> "Highest keeper score"
        }
    }

    private companion object {
        val BORING_TAGS = setOf("photo", "keeper", "sharp", "other", "reference", "forgettable", "people")
    }
}
