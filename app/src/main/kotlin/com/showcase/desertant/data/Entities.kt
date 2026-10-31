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
    /** For call recordings: who the call was with, as the phone app named the file (a name or a number). */
    val callWith: String? = null,
    /** The person [callWith] is, once Burrow knows. */
    val callProfileId: Long? = null,
    /** A [TalkSummary] written on the phone by the ask model, if there is one. */
    val summaryJson: String? = null,
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
    /** Their phone contact, picked by the user (a lookup URI); Burrow never reads the address book. */
    val contactUri: String? = null,
    /** Phone numbers from the picked contact, digits only, comma-separated. */
    val phones: String = "",
    /** Other names they go by in calls and chats ("Mum", "Priya 🌸"), comma-separated. */
    val aliases: String = "",
)

/** A chat the user shared into Burrow (e.g. WhatsApp's "Export chat"). Nothing is read from other apps. */
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** "whatsapp" or "text". */
    val source: String,
    val importedAt: Long,
    val firstAt: Long,
    val lastAt: Long,
    val messageCount: Int,
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chatId"), Index("at")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val at: Long,
    val sender: String,
    val text: String,
)

/** Who each name in a chat is: a person, the user (isMe), or nobody yet. */
@Entity(
    tableName = "chat_people",
    primaryKeys = ["chatId", "sender"],
    foreignKeys = [ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class ChatPersonEntity(
    val chatId: Long,
    val sender: String,
    val profileId: Long? = null,
    val isMe: Boolean = false,
    val messages: Int = 0,
)

/** Notes a language model wrote for a talk: what it was about, what was decided, who does what. */
@Serializable
data class TalkSummary(
    val gist: String,
    val points: List<SummaryPoint> = emptyList(),
    val decisions: List<SummaryPoint> = emptyList(),
    val todos: List<SummaryTodo> = emptyList(),
    /** The language it's written in, in English ("Hindi"). */
    val language: String,
    val model: String,
    val writtenAt: Long,
)

/** A line of a summary and, when the model said, the moment in the talk it comes from (seconds). */
@Serializable
data class SummaryPoint(val text: String, val at: Double? = null)

@Serializable
data class SummaryTodo(
    val what: String,
    val who: String? = null,
    /** yyyy-MM-dd, worked out by Burrow from [dueWords] and the talk's date. */
    val due: String? = null,
    val at: Double? = null,
    /** How the talk said it ("by Friday", "अगले मंगलवार"). */
    val dueWords: String? = null,
)

@Serializable
data class Utterance(val speaker: Int?, val start: Double, val end: Double, val text: String)

@Serializable
data class TopicScore(val slug: String, val name: String, val score: Double)

@Serializable
data class Moment(val speaker: Int?, val start: Double, val text: String)

@Serializable
data class VoiceSignature(val values: List<Float>, val pitchHz: Float, val seconds: Double)
