package com.showcase.desertant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class PhotoJson(val mediaId: Long, val analysisJson: String?)

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE mediaId IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE mediaId = :id")
    suspend fun byId(id: Long): PhotoEntity?

    @Upsert
    suspend fun upsert(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE mediaId IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE photos SET kept = 1 WHERE mediaId IN (:ids)")
    suspend fun markKept(ids: List<Long>)

    @Query("UPDATE photos SET privateDismissed = 1 WHERE mediaId IN (:ids)")
    suspend fun markNotPrivate(ids: List<Long>)

    @Query("DELETE FROM photos")
    suspend fun clear()

    @Query("SELECT * FROM photos WHERE analysisJson IS NOT NULL ORDER BY dateTaken")
    suspend fun analyzed(): List<PhotoEntity>

    /** Analyzed photos without their (large) analysis JSON; see [analysisJson]. */
    @Query(
        "SELECT mediaId, uri, bucketId, bucketName, dateTaken, dateModified, sizeBytes, width, height, NULL AS analysisJson, " +
            "keeper, aesthetic, tags, junkReason, analyzedAt, failed, kept, intimate, privateDismissed FROM photos WHERE analysisJson IS NOT NULL ORDER BY dateTaken",
    )
    fun analyzedFlow(): Flow<List<PhotoEntity>>

    @Query("SELECT mediaId, analysisJson FROM photos WHERE mediaId IN (:ids)")
    suspend fun analysisJson(ids: List<Long>): List<PhotoJson>

    @Query("SELECT COUNT(*) FROM photos WHERE analysisJson IS NOT NULL")
    fun analyzedCount(): Flow<Int>

    @Query("SELECT mediaId FROM photos")
    suspend fun allIds(): List<Long>
}

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    fun all(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE transcriptText LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun search(q: String): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun observe(id: Long): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun get(id: Long): MeetingEntity?

    @Insert
    suspend fun insert(meeting: MeetingEntity): Long

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Query("UPDATE meetings SET status = :status, progress = :progress, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: MeetingStatus, progress: String?, error: String? = null)

    @Query("UPDATE meetings SET language = :language, languageChosen = 1 WHERE id = :id")
    suspend fun chooseLanguage(id: Long, language: String)

    @Query("UPDATE meetings SET title = :title, titleEdited = 1 WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT sourceKey FROM meetings WHERE sourceKey IS NOT NULL")
    suspend fun importedKeys(): List<String>

    @Query("SELECT * FROM meetings WHERE createdAt < :before")
    suspend fun olderThan(before: Long): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE status = :status ORDER BY createdAt DESC")
    suspend fun byStatus(status: MeetingStatus): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE callWith IS NOT NULL")
    suspend fun calls(): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE callProfileId = :profileId ORDER BY createdAt DESC")
    fun callsWith(profileId: Long): Flow<List<MeetingEntity>>

    @Query("UPDATE meetings SET callProfileId = :profileId WHERE id = :id")
    suspend fun linkCall(id: Long, profileId: Long?)

    @Query("UPDATE meetings SET summaryJson = :json WHERE id = :id")
    suspend fun setSummary(id: Long, json: String?)

    @Query("SELECT * FROM meetings WHERE status = 'READY' AND createdAt >= :since ORDER BY createdAt DESC")
    fun readySince(since: Long): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE summaryJson IS NOT NULL ORDER BY createdAt DESC LIMIT :limit")
    fun summarized(limit: Int): Flow<List<MeetingEntity>>

    @Query("UPDATE meetings SET callProfileId = NULL WHERE callProfileId = :profileId")
    suspend fun unlinkCalls(profileId: Long)
}

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(chat: ChatEntity): Long

    @Insert
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertPeople(people: List<ChatPersonEntity>)

    @Query("SELECT * FROM chats ORDER BY lastAt DESC")
    fun all(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY lastAt DESC")
    suspend fun allNow(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observe(id: Long): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun get(id: Long): ChatEntity?

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM chat_people WHERE chatId = :chatId ORDER BY messages DESC")
    fun people(chatId: Long): Flow<List<ChatPersonEntity>>

    @Query("SELECT * FROM chat_people")
    suspend fun allPeopleNow(): List<ChatPersonEntity>

    @Query("SELECT * FROM chat_people")
    fun allPeople(): Flow<List<ChatPersonEntity>>

    @Query("SELECT * FROM chat_people WHERE profileId = :profileId")
    fun forProfile(profileId: Long): Flow<List<ChatPersonEntity>>

    @Query("UPDATE chat_people SET profileId = NULL WHERE profileId = :profileId")
    suspend fun unlinkPerson(profileId: Long)

    @Query("UPDATE chat_people SET profileId = :profileId, isMe = :isMe WHERE chatId = :chatId AND sender = :sender")
    suspend fun setPerson(chatId: Long, sender: String, profileId: Long?, isMe: Boolean)

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY at DESC, id DESC LIMIT :limit")
    fun latest(chatId: Long, limit: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND text LIKE '%' || :q || '%' ORDER BY at DESC LIMIT 200")
    fun search(chatId: Long, q: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE text LIKE '%' || :q || '%' AND at >= :since ORDER BY at DESC LIMIT :limit")
    suspend fun searchAll(q: String, since: Long, limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE chatId IN (:chatIds) AND at >= :since ORDER BY at DESC LIMIT :limit")
    suspend fun recentIn(chatIds: List<Long>, since: Long, limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND sender = :sender ORDER BY at DESC LIMIT 1")
    suspend fun lastFrom(chatId: Long, sender: String): ChatMessageEntity?
}

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM meeting_speakers WHERE meetingId = :meetingId ORDER BY `index`")
    fun forMeeting(meetingId: Long): Flow<List<MeetingSpeakerEntity>>

    @Query("SELECT * FROM meeting_speakers WHERE meetingId = :meetingId ORDER BY `index`")
    suspend fun forMeetingNow(meetingId: Long): List<MeetingSpeakerEntity>

    @Query("SELECT * FROM meeting_speakers WHERE meetingId IN (:meetingIds)")
    suspend fun forMeetingsNow(meetingIds: List<Long>): List<MeetingSpeakerEntity>

    @Query("SELECT * FROM meeting_speakers WHERE profileId = :profileId")
    fun forProfile(profileId: Long): Flow<List<MeetingSpeakerEntity>>

    @Query("SELECT * FROM meeting_speakers WHERE profileId = :profileId")
    suspend fun forProfileNow(profileId: Long): List<MeetingSpeakerEntity>

    @Query("SELECT * FROM meeting_speakers")
    fun all(): Flow<List<MeetingSpeakerEntity>>

    @Query("SELECT * FROM meeting_speakers WHERE id = :id")
    suspend fun get(id: Long): MeetingSpeakerEntity?

    @Insert
    suspend fun insert(speakers: List<MeetingSpeakerEntity>)

    @Query("DELETE FROM meeting_speakers WHERE meetingId = :meetingId")
    suspend fun deleteForMeeting(meetingId: Long)

    @Query("UPDATE meeting_speakers SET profileId = :profileId, suggestedProfileId = NULL, suggestionScore = NULL WHERE id = :id")
    suspend fun assign(id: Long, profileId: Long?)

    @Query("UPDATE meeting_speakers SET suggestedProfileId = NULL, suggestionScore = NULL, dismissed = 1 WHERE id = :id")
    suspend fun dismissSuggestion(id: Long)

    @Query("SELECT * FROM meeting_speakers WHERE profileId IS NULL")
    suspend fun unlinked(): List<MeetingSpeakerEntity>

    @Query("UPDATE meeting_speakers SET suggestedProfileId = :profileId, suggestionScore = :score WHERE id = :id")
    suspend fun suggest(id: Long, profileId: Long?, score: Double?)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY isMe DESC, name COLLATE NOCASE")
    fun all(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles")
    suspend fun allNow(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}
