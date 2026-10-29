package com.showcase.desertant.photos

import ai.desertant.eye.Analysis
import ai.desertant.eye.Level
import ai.desertant.eye.Options
import ai.desertant.eye.Part
import ai.desertant.eye.Tag
import ai.desertant.moderator.Options as ModerationOptions
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.showcase.desertant.ShowcaseApp
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.data.PhotoEntity
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.notify.Notifications
import com.showcase.desertant.privacy.LedgerEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans a [PhotoScope] in the background: every photo that is new or changed
 * since the last scan goes through Eye, and the result is stored so grouping,
 * search and cleanup never need the model again.
 */
class PhotoScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ShowcaseApp
        val dao = app.db.photos()
        val scope = inputData.getString(KEY_SCOPE)?.let { StoreJson.decodeFromString<PhotoScope>(it) } ?: PhotoScope()

        promote("Preparing photo scan…", 0, 0)

        // Forget photos that were deleted elsewhere.
        val existing = PhotoLibrary.existingIds(applicationContext)
        dao.allIds().filter { it !in existing }.takeIf { it.isNotEmpty() }?.let { gone -> gone.chunked(500).forEach { dao.delete(it) } }

        val inScope = PhotoLibrary.photos(applicationContext, scope)
        val known = inScope.chunked(500).flatMap { dao.byIds(it.map(PhotoEntity::mediaId)) }.associateBy { it.mediaId }
        val todo = inScope.filter { p ->
            val k = known[p.mediaId]
            // New, edited, or not analyzed yet (including earlier failures, which get another try).
            k == null || k.dateModified != p.dateModified || k.analysisJson == null
        }
        if (todo.isEmpty()) return Result.success(workDataOf(KEY_DONE to 0, KEY_TOTAL to 0))

        promote("Getting the photo model ready…", 0, todo.size)
        app.hub.ensureReady(ModelKind.EYE)

        val batch = ArrayList<PhotoEntity>()
        todo.forEachIndexed { i, photo ->
            if (isStopped) return Result.success()
            val updated = try {
                val bitmap = thumbnail(Uri.parse(photo.uri))
                var intimate: Boolean? = null
                val analysis = try {
                    // Read the model each time: Settings can unload and recreate it mid-scan.
                    val a = app.hub.eye.analyze(bitmap, Options(parts = setOf(Part.PHOTO, Part.FACES, Part.SHARPNESS), maxFaces = 8))
                    // A second, specialised opinion before the private sweep calls anything intimate.
                    if (mightBeIntimate(a)) {
                        intimate = runCatching {
                            app.hub.ensureReady(ModelKind.MODERATOR)
                            app.hub.moderator.analyze(bitmap, ModerationOptions()).isNSFW
                        }.getOrNull()
                    }
                    a
                } finally {
                    bitmap.recycle()
                }
                photo.withAnalysis(analysis).copy(intimate = intimate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                photo.copy(failed = true, analyzedAt = System.currentTimeMillis())
            }
            batch += updated.copy(kept = known[photo.mediaId]?.kept ?: false, privateDismissed = known[photo.mediaId]?.privateDismissed ?: false)
            if (batch.size >= 12 || i == todo.lastIndex) {
                dao.upsert(batch)
                app.ledger.add(LedgerEntry.PHOTOS_CHECKED, batch.size)
                batch.clear()
                setProgress(workDataOf(KEY_DONE to i + 1, KEY_TOTAL to todo.size))
                promote("Analyzed ${i + 1} of ${todo.size} photos", i + 1, todo.size)
            }
        }
        Notifications.done(applicationContext, Notifications.PHOTO_DONE_ID, "Photo scan finished", "${todo.size} photos analyzed on device. Review what you can clean up.")
        return Result.success(workDataOf(KEY_DONE to todo.size, KEY_TOTAL to todo.size))
    }

    private suspend fun thumbnail(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val b = applicationContext.contentResolver.loadThumbnail(uri, Size(768, 768), null)
        if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false).also { b.recycle() }
    }

    /** Needed for expedited work on Android 11. */
    override suspend fun getForegroundInfo() = ForegroundInfo(
        Notifications.PHOTO_SCAN_ID,
        Notifications.progress(applicationContext, "Scanning photos", "Preparing…", 0, 0, id),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    /** Progress notification; if Android won't allow a foreground start right now, keep scanning without it. */
    private suspend fun promote(text: String, done: Int, total: Int) {
        try {
            setForeground(
                ForegroundInfo(
                    Notifications.PHOTO_SCAN_ID,
                    Notifications.progress(applicationContext, "Scanning photos", text, done, total, id),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    companion object {
        const val UNIQUE = "photo-scan"
        const val KEY_SCOPE = "scope"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"

        fun start(context: Context, scope: PhotoScope) {
            val request = OneTimeWorkRequestBuilder<PhotoScanWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(KEY_SCOPE to StoreJson.encodeToString(PhotoScope.serializer(), scope)))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
    }
}

/** Throwaway-shot reasons, strongest first, with the confidence each needs. */
private val JUNK_RULES = listOf(
    "unusable" to "Unusable",
    "accidental" to "Accidental shot",
    "blurry" to "Blurry",
    "test_or_duplicate_shot" to "Test shot",
    "technical_flaw" to "Technical flaw",
    "low_content" to "Nothing in it",
)

fun PhotoEntity.withAnalysis(a: Analysis): PhotoEntity {
    val junk = JUNK_RULES.firstOrNull { (tag, _) -> (a.confidence(Tag(tag)) ?: 0.0) >= 0.6 }?.second
    return copy(
        analysisJson = a.toJson(),
        keeper = a.scores?.keeper,
        aesthetic = a.scores?.aesthetic,
        tags = a.tags(Level.RECOMMENDED).joinToString(" ") { it.id },
        junkReason = junk,
        analyzedAt = System.currentTimeMillis(),
        failed = false,
    )
}
