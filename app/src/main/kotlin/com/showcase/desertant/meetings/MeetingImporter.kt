package com.showcase.desertant.meetings

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.showcase.desertant.app
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSource
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.people.CallLinks
import com.showcase.desertant.people.Contacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A recording another app made: a phone app's call recording or a voice memo. */
data class ExternalRecording(
    val uri: Uri,
    val name: String,
    val dateMillis: Long,
    val durationSec: Double,
    val sizeBytes: Long,
    val isCall: Boolean,
    val imported: Boolean,
)

object MeetingImporter {

    /** Copy [uri] into app storage, create the meeting and queue it for processing. */
    suspend fun import(
        context: Context,
        uri: Uri,
        source: MeetingSource,
        name: String? = null,
        createdAt: Long? = null,
        /** For a call the user placed by hand: who it was with. Otherwise read from the file name. */
        callWith: String? = null,
        callProfileId: Long? = null,
    ): Long =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val display = name ?: resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "Recording"
            val ext = display.substringAfterLast('.', "m4a").take(5)
            val dir = File(context.filesDir, "meetings").apply { mkdirs() }
            val out = File(dir, "imp-${System.currentTimeMillis()}.$ext")
            resolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                ?: error("Couldn't open that file.")
            val db = context.app.db
            // A call recording's file name says who it was with ("Call recording Priya Shah_241030_181205").
            val who = if (source == MeetingSource.CALL) callWith ?: CallLinks.whoFromFileName(display) else null
            val callProfile = callProfileId ?: who?.let { Contacts.match(it, db.profiles().allNow())?.id }
            val id = db.meetings().insert(
                MeetingEntity(
                    title = who?.let { "call with $it" } ?: display.substringBeforeLast('.'),
                    createdAt = createdAt ?: System.currentTimeMillis(),
                    audioPath = out.absolutePath,
                    source = source,
                    status = MeetingStatus.QUEUED,
                    progress = "Waiting to be processed",
                    sourceKey = uri.toString(),
                    callWith = who,
                    callProfileId = callProfile,
                ),
            )
            MeetingWorker.enqueue(context, id)
            id
        }

    /** Folders recording apps use, across phone makers. */
    private val RECORDER_PATHS = listOf(
        "Recordings/%", "%Call%", "%call%", "%Record%", "%record%", "MIUI/sound_recorder/%", "Sounds/%", "Voice Recorder/%", "%PhoneRecord%",
    )

    /** Chat apps keep voice notes in folders that also match; those aren't recordings to bring in. */
    private val NOT_RECORDINGS = listOf("whatsapp", "telegram", "signal", "viber", "line/", "wechat", "messenger", "notifications", "ringtones", "alarms")

    /**
     * Recordings other apps saved on the phone: anything Android marks as a
     * recording (Android 12+), plus the folders call and voice recorders use
     * on Samsung, Xiaomi, OnePlus, Oppo, Realme, Vivo, Honor, Huawei and others.
     * Each is labelled a call or a voice memo from its folder and name.
     */
    suspend fun deviceRecordings(context: Context): List<ExternalRecording> = withContext(Dispatchers.IO) {
        val imported = context.app.db.meetings().importedKeys().toSet()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val out = ArrayList<ExternalRecording>()
        val byPath = RECORDER_PATHS.joinToString(" OR ") { "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" }
        val selection = if (Build.VERSION.SDK_INT >= 31) "(${MediaStore.Audio.Media.IS_RECORDING} = 1 OR $byPath)" else "($byPath)"
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.RELATIVE_PATH,
            ),
            "$selection AND ${MediaStore.Audio.Media.IS_MUSIC} = 0 AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0",
            RECORDER_PATHS.toTypedArray(),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(5).orEmpty()
                if (NOT_RECORDINGS.any { path.lowercase().contains(it) }) continue
                val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                val name = c.getString(1) ?: "Recording"
                out += ExternalRecording(
                    uri = uri,
                    name = name,
                    dateMillis = c.getLong(2) * 1000,
                    durationSec = c.getLong(3) / 1000.0,
                    sizeBytes = c.getLong(4),
                    isCall = CallLinks.isCallRecording(path, name),
                    imported = uri.toString() in imported,
                )
            }
        }
        out
    }
}
