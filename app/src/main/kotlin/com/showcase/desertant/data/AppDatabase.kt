package com.showcase.desertant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json

@Database(
    entities = [
        PhotoEntity::class, MeetingEntity::class, MeetingSpeakerEntity::class, ProfileEntity::class,
        ChatEntity::class, ChatMessageEntity::class, ChatPersonEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photos(): PhotoDao
    abstract fun meetings(): MeetingDao
    abstract fun speakers(): SpeakerDao
    abstract fun profiles(): ProfileDao
    abstract fun chats(): ChatDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "burrow.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

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

        /** 3.5: people linked to a contact, call recordings linked to people, and shared chats. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN contactUri TEXT")
                db.execSQL("ALTER TABLE profiles ADD COLUMN phones TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN aliases TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE meetings ADD COLUMN callWith TEXT")
                db.execSQL("ALTER TABLE meetings ADD COLUMN callProfileId INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `chats` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `source` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, `firstAt` INTEGER NOT NULL, `lastAt` INTEGER NOT NULL, `messageCount` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chatId` INTEGER NOT NULL, `at` INTEGER NOT NULL, `sender` TEXT NOT NULL, `text` TEXT NOT NULL, FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_chatId` ON `chat_messages` (`chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_at` ON `chat_messages` (`at`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_people` (`chatId` INTEGER NOT NULL, `sender` TEXT NOT NULL, `profileId` INTEGER, `isMe` INTEGER NOT NULL, `messages` INTEGER NOT NULL, PRIMARY KEY(`chatId`, `sender`), FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_people_profileId` ON `chat_people` (`profileId`)")
            }
        }
    }
}

/** Lenient JSON for the blobs stored in columns. */
val StoreJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
