package com.showcase.desertant.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One photo from MediaStore and, once scanned, what Eye saw in it. */
@Entity(tableName = "photos", indices = [Index("bucketId"), Index("dateTaken")])
data class PhotoEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val bucketId: String,
    val bucketName: String,
    /** Capture time in epoch millis (falls back to date added). */
    val dateTaken: Long,
    /** MediaStore DATE_MODIFIED, seconds; a change means re-analyze. */
    val dateModified: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** Eye's `Analysis.toJson()`, which keeps the embedding for grouping later. */
    val analysisJson: String? = null,
    val keeper: Double? = null,
    val aesthetic: Double? = null,
    /** Space-separated tags at the recommended cutoff, for search. */
    val tags: String = "",
    /** Why this looks like a throwaway shot, or null. */
    val junkReason: String? = null,
    val analyzedAt: Long? = null,
    val failed: Boolean = false,
    /** The user chose to keep it; don't suggest deleting it again. */
    val kept: Boolean = false,
    /** Moderator's verdict, checked only for photos Eye thought might be intimate; null if not checked. */
    val intimate: Boolean? = null,
    /** The user said this isn't private; the sweep leaves it alone. */
    val privateDismissed: Boolean = false,
)

enum class MeetingSource { RECORDED, IMPORTED, CALL }

enum class MeetingStatus { RECORDING, QUEUED, PROCESSING, READY, FAILED }

@Entity(tableName = "meetings", indices = [Index("createdAt")])
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val durationSec: Double = 0.0,
    /** The recording, in app storage. */
    val audioPath: String,
    val source: MeetingSource = MeetingSource.RECORDED,
    val status: MeetingStatus = MeetingStatus.QUEUED,
    val progress: String? = null,
    val error: String? = null,
    val language: String? = null,
    val languageSupported: Boolean? = null,
    /** Plain transcript, for search. */
    val transcriptText: String = "",
    val utterancesJson: String? = null,
    val topicsJson: String? = null,
    val highlightsJson: String? = null,
    val followUpsJson: String? = null,
    val speakerCount: Int = 0,
    val titleEdited: Boolean = false,
    /** Where an imported recording came from, so it isn't imported twice. */
    val sourceKey: String? = null,
    /** The user set [language] by hand; processing uses it instead of asking Ear. */
    val languageChosen: Boolean = false,
)

/** One diarized voice in one meeting, optionally linked to a person. */
@Entity(
    tableName = "meeting_speakers",
    foreignKeys = [
        ForeignKey(MeetingEntity::class, ["id"], ["meetingId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("meetingId"), Index("profileId")],
)
data class MeetingSpeakerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    /** The speaker index Who assigned within this meeting. */
    val index: Int,
    val profileId: Long? = null,
    /** Best matching person by voice, awaiting confirmation. */
    val suggestedProfileId: Long? = null,
    val suggestionScore: Double? = null,
    val talkSec: Double = 0.0,
    val words: Int = 0,
    val signatureJson: String? = null,
    val topicsJson: String? = null,
    val language: String? = null,
    val sampleQuote: String? = null,
    /** The user said the suggestion was wrong. */
    val dismissed: Boolean = false,
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int,
    val isMe: Boolean = false,
    /** Running average of confirmed voice signatures. */
    val signatureJson: String? = null,
    val signatureCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = "",
)

@Serializable
data class Utterance(val speaker: Int?, val start: Double, val end: Double, val text: String)

@Serializable
data class TopicScore(val slug: String, val name: String, val score: Double)

@Serializable
data class Moment(val speaker: Int?, val start: Double, val text: String)

@Serializable
data class VoiceSignature(val values: List<Float>, val pitchHz: Float, val seconds: Double)
