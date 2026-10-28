package com.showcase.desertant.photos

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.showcase.desertant.data.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

data class Album(val id: String, val name: String, val count: Int, val cover: Uri)

/** Which photos a scan covers. */
@Serializable
data class PhotoScope(val albumId: String? = null, val albumName: String? = null, val days: Int? = 30) {
    val label: String
        get() = buildString {
            append(albumName ?: "All photos")
            append(" · ")
            append(if (days == null) "all time" else "last $days days")
        }
}

object PhotoLibrary {
    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun uriOf(id: Long): Uri = ContentUris.withAppendedId(collection, id)

    suspend fun albums(context: Context): List<Album> = withContext(Dispatchers.IO) {
        val byBucket = LinkedHashMap<String, Triple<String, Int, Long>>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val bucket = c.getString(bucketCol) ?: continue
                val existing = byBucket[bucket]
                byBucket[bucket] = if (existing == null) Triple(c.getString(nameCol) ?: "Photos", 1, c.getLong(idCol))
                    else existing.copy(second = existing.second + 1)
            }
        }
        byBucket.map { (id, v) -> Album(id, v.first, v.second, uriOf(v.third)) }.sortedByDescending { it.count }
    }

    /** Every photo in [scope], newest first, without analysis. */
    suspend fun photos(context: Context, scope: PhotoScope): List<PhotoEntity> = withContext(Dispatchers.IO) {
        val selection = mutableListOf<String>()
        val args = mutableListOf<String>()
        scope.albumId?.let { selection += "${MediaStore.Images.Media.BUCKET_ID} = ?"; args += it }
        scope.days?.let {
            selection += "${MediaStore.Images.Media.DATE_ADDED} >= ?"
            args += (System.currentTimeMillis() / 1000 - it * 86_400L).toString()
        }
        val out = ArrayList<PhotoEntity>()
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
            ),
            selection.joinToString(" AND ").ifEmpty { null },
            args.toTypedArray().ifEmpty { null },
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val taken = c.getLong(3).takeIf { it > 0 } ?: (c.getLong(4) * 1000)
                out += PhotoEntity(
                    mediaId = id,
                    uri = uriOf(id).toString(),
                    bucketId = c.getString(1) ?: "",
                    bucketName = c.getString(2) ?: "",
                    dateTaken = taken,
                    dateModified = c.getLong(5),
                    sizeBytes = c.getLong(6),
                    width = c.getInt(7),
                    height = c.getInt(8),
                )
            }
        }
        out
    }

    /** All image ids MediaStore still has (trashed ones are excluded by default). */
    suspend fun existingIds(context: Context): Set<Long> = withContext(Dispatchers.IO) {
        val ids = HashSet<Long>()
        context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { c ->
            while (c.moveToNext()) ids += c.getLong(0)
        }
        ids
    }
}
