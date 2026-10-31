package com.showcase.desertant.privacy

import android.content.Intent
import androidx.work.WorkManager
import com.showcase.desertant.ShowcaseApp
import com.showcase.desertant.meetings.RecorderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** "Delete means delete": the two places Burrow throws things away for good. */
object Housekeeping {
    private const val AUDIO_DAYS = 30L

    /** With "delete talk audio after 30 days" on: remove old recordings, keep their notes. Returns how many went. */
    suspend fun deleteOldAudio(app: ShowcaseApp): Int = withContext(Dispatchers.IO) {
        if (!app.privacy.autoDeleteAudio.value) return@withContext 0
        val cutoff = System.currentTimeMillis() - AUDIO_DAYS * 86_400_000L
        app.db.meetings().olderThan(cutoff).count { m -> File(m.audioPath).let { it.exists() && it.delete() } }
    }

    /**
     * Wipe everything Burrow knows: photo analyses, talks and their audio,
     * people and voices, the vault, the ledger and every setting. Photos in the
     * gallery are untouched. With [models], the downloaded models go too.
     */
    suspend fun wipeEverything(app: ShowcaseApp, models: Boolean) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(app).cancelAllWork()
        runCatching { app.stopService(Intent(app, RecorderService::class.java)) }
        app.db.clearAllTables()
        File(app.filesDir, "meetings").deleteRecursively()
        File(app.cacheDir, "exports").deleteRecursively()
        app.vault.wipe()
        app.mask.clear()
        app.ledger.clear()
        app.privacy.clear()
        if (models) {
            // Burrow lets go of its models; files another app still uses stay for it.
            com.showcase.desertant.speech.SpeechPacks.closeRecognizer()
            app.hub.releaseAll()
            app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            app.hub.refreshAll()
            com.showcase.desertant.speech.SpeechPacks.refresh(app)
        }
    }

    /**
     * Before the shared model store, the SDK kept its weights loose in the app
     * cache and speech packs lived in `filesDir/speech`. Both now live in the
     * store, so the old copies (often over a gigabyte) are deleted once.
     */
    fun dropPreStoreModelCache(app: ShowcaseApp) {
        val prefs = app.getSharedPreferences("housekeeping", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("pre_store_cache_dropped", false)) return
        app.cacheDir.listFiles()
            ?.filter { it.name != "exports" && it.name != "image_manager_disk_cache" }
            ?.forEach { it.deleteRecursively() }
        File(app.filesDir, "speech").deleteRecursively()
        prefs.edit().putBoolean("pre_store_cache_dropped", true).apply()
    }
}
