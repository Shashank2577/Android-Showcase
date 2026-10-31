package com.showcase.desertant.meetings

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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

/** A recording another app made, e.g. Samsung Phone's call recordings. */
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
    suspend fun import(context: Context, uri: Uri, source: MeetingSource, name: String? = null, createdAt: Long? = null): Long =
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
            val callWith = if (source == MeetingSource.CALL) CallLinks.whoFromFileName(display) else null
            val callProfile = callWith?.let { Contacts.match(it, db.profiles().allNow()) }
            val id = db.meetings().insert(
                MeetingEntity(
                    title = callWith?.let { "call with $it" } ?: display.substringBeforeLast('.'),
                    createdAt = createdAt ?: System.currentTimeMillis(),
                    audioPath = out.absolutePath,
                    source = source,
                    status = MeetingStatus.QUEUED,
                    progress = "Waiting to be processed",
                    sourceKey = uri.toString(),
                    callWith = callWith,
                    callProfileId = callProfile?.id,
                ),
            )
            MeetingWorker.enqueue(context, id)
            id
        }

    /**
     * Audio in the phone's recording folders: Samsung's call recorder saves to
     * Recordings/Call, its Voice Recorder to Recordings/Voice Recorder.
     */
    suspend fun deviceRecordings(context: Context): List<ExternalRecording> = withContext(Dispatchers.IO) {
        val imported = context.app.db.meetings().importedKeys().toSet()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val out = ArrayList<ExternalRecording>()
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.RELATIVE_PATH,
            ),
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("Recordings/%", "%Call%", "%Voice Recorder%"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                val path = c.getString(5).orEmpty()
                out += ExternalRecording(
                    uri = uri,
                    name = c.getString(1) ?: "Recording",
                    dateMillis = c.getLong(2) * 1000,
                    durationSec = c.getLong(3) / 1000.0,
                    sizeBytes = c.getLong(4),
                    isCall = path.contains("Call", ignoreCase = true),
                    imported = uri.toString() in imported,
                )
            }
        }
        out
    }
}
