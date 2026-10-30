package com.showcase.desertant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json

@Database(
    entities = [PhotoEntity::class, MeetingEntity::class, MeetingSpeakerEntity::class, ProfileEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photos(): PhotoDao
    abstract fun meetings(): MeetingDao
    abstract fun speakers(): SpeakerDao
    abstract fun profiles(): ProfileDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "burrow.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        /** v3: what the private sweep needs to remember about each photo. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN intimate INTEGER")
                db.execSQL("ALTER TABLE photos ADD COLUMN privateDismissed INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 3.2: a talk's language can be chosen by hand. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN languageChosen INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

/** Lenient JSON for the blobs stored in columns. */
val StoreJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
