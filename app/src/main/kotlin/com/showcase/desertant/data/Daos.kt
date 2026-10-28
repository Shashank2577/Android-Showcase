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

    @Query("SELECT * FROM photos WHERE analysisJson IS NOT NULL ORDER BY dateTaken")
    suspend fun analyzed(): List<PhotoEntity>

    /** Analyzed photos without their (large) analysis JSON; see [analysisJson]. */
    @Query(
        "SELECT mediaId, uri, bucketId, bucketName, dateTaken, dateModified, sizeBytes, width, height, NULL AS analysisJson, " +
            "keeper, aesthetic, tags, junkReason, analyzedAt, failed, kept FROM photos WHERE analysisJson IS NOT NULL ORDER BY dateTaken",
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

    @Query("UPDATE meetings SET title = :title, titleEdited = 1 WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT sourceKey FROM meetings WHERE sourceKey IS NOT NULL")
    suspend fun importedKeys(): List<String>

    @Query("SELECT * FROM meetings WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<MeetingEntity>
}

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM meeting_speakers WHERE meetingId = :meetingId ORDER BY `index`")
    fun forMeeting(meetingId: Long): Flow<List<MeetingSpeakerEntity>>

    @Query("SELECT * FROM meeting_speakers WHERE meetingId = :meetingId ORDER BY `index`")
    suspend fun forMeetingNow(meetingId: Long): List<MeetingSpeakerEntity>

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
