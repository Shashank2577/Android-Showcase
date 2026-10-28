package com.showcase.desertant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.serialization.json.Json

@Database(
    entities = [PhotoEntity::class, MeetingEntity::class, MeetingSpeakerEntity::class, ProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photos(): PhotoDao
    abstract fun meetings(): MeetingDao
    abstract fun speakers(): SpeakerDao
    abstract fun profiles(): ProfileDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "burrow.db").build()
    }
}

/** Lenient JSON for the blobs stored in columns. */
val StoreJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
