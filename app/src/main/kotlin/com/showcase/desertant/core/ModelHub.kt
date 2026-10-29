package com.showcase.desertant.core

import ai.desertant.clear.Clear
import ai.desertant.core.DesertAnt
import ai.desertant.ear.Ear
import ai.desertant.emo.Emo
import ai.desertant.eye.Eye
import ai.desertant.gist.Gist
import ai.desertant.moderator.Moderator
import ai.desertant.redact.Redact
import ai.desertant.shapes.Shapes
import ai.desertant.tongue.Tongue
import ai.desertant.voz.Voz
import ai.desertant.who.Who
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A model was needed while "never go online" is on. */
class OfflineException(kind: ModelKind) :
    Exception("${kind.title} isn't on this phone yet, and \"never go online\" is on. Turn it off on the privacy page to download it once.")

sealed interface ModelStatus {
    data object Unknown : ModelStatus
    data object NotDownloaded : ModelStatus
    data object Downloading : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/**
 * Owns one instance of every model for the whole app, as the SDK recommends
 * ("create one and reuse it"), and tracks whether each model's weights are on
 * the device.
 *
 * Construction is cheap — the SDK does nothing until `download()` or the first
 * inference call — so instances are created lazily on first access. [unload]
 * closes a model to release its native memory; the next access recreates it
 * from the on-disk cache without downloading again.
 */
class ModelHub(private val context: Context, private val offline: () -> Boolean = { false }) {

    private val instances = HashMap<ModelKind, Any>()
    private val inflight = HashMap<ModelKind, Deferred<Unit>>()

    /** App-scoped, so a download keeps going when the user leaves the screen that started it. */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow<Map<ModelKind, ModelStatus>>(
        ModelKind.entries.associateWith { if (it.bundled) ModelStatus.Ready else ModelStatus.Unknown },
    )
    val status: StateFlow<Map<ModelKind, ModelStatus>> = _status.asStateFlow()

    private val prefs = context.getSharedPreferences("desertant", Context.MODE_PRIVATE)

    /** The publishable key sent with license activations; blank means unset. */
    var apiKey: String
        get() = DesertAnt.apiKey.orEmpty()
        set(value) {
            val trimmed = value.trim()
            prefs.edit().putString(KEY_API, trimmed).apply()
            DesertAnt.apiKey = trimmed.ifBlank { null }
        }

    fun applyStoredApiKey(buildTimeKey: String) {
        val stored = prefs.getString(KEY_API, null)
        DesertAnt.apiKey = (stored ?: buildTimeKey).ifBlank { null }
    }

    val clear: Clear get() = instance(ModelKind.CLEAR) { Clear(context) }
    val ear: Ear get() = instance(ModelKind.EAR) { Ear(context) }
    val emo: Emo get() = instance(ModelKind.EMO) { Emo(context) }
    val eye: Eye get() = instance(ModelKind.EYE) { Eye(context) }
    val gist: Gist get() = instance(ModelKind.GIST) { Gist(context) }
    val moderator: Moderator get() = instance(ModelKind.MODERATOR) { Moderator(context) }
    val redact: Redact get() = instance(ModelKind.REDACT) { Redact(context) }
    val shapes: Shapes get() = instance(ModelKind.SHAPES) { Shapes(context) }
    val tongue: Tongue get() = instance(ModelKind.TONGUE) { Tongue.bundled(context) }
    val voz: Voz get() = instance(ModelKind.VOZ) { Voz(context) }
    val who: Who get() = instance(ModelKind.WHO) { Who(context) }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    private fun <T : Any> instance(kind: ModelKind, create: () -> T): T =
        instances.getOrPut(kind, create) as T

    fun isLoaded(kind: ModelKind): Boolean = synchronized(this) { kind in instances }

    /** Re-read whether [kind]'s weights are on disk. */
    suspend fun refresh(kind: ModelKind) {
        if (kind.bundled) return
        if (_status.value[kind] == ModelStatus.Downloading) return
        val downloaded = withContext(Dispatchers.IO) { runCatching { isDownloaded(kind) }.getOrDefault(false) }
        set(kind, if (downloaded) ModelStatus.Ready else ModelStatus.NotDownloaded)
    }

    suspend fun refreshAll() = ModelKind.entries.forEach { refresh(it) }

    /**
     * Make sure [kind] is on the device, downloading it if needed, and report
     * progress through [status]. Call before inference so the UI can show the
     * download rather than a long silent first call.
     *
     * The download runs as one shared app-scoped job: a caller that is
     * cancelled (the user typed again, or left the screen) stops waiting, but
     * the download carries on and later callers join it.
     */
    suspend fun ensureReady(kind: ModelKind) {
        if (kind.bundled) return
        val job = synchronized(inflight) {
            inflight[kind]?.takeIf { it.isActive }
                ?: backgroundScope.async { prepare(kind) }.also { inflight[kind] = it }
        }
        job.await()
    }

    private suspend fun prepare(kind: ModelKind) {
        val downloaded = withContext(Dispatchers.IO) { runCatching { isDownloaded(kind) }.getOrDefault(false) }
        if (downloaded) {
            set(kind, ModelStatus.Ready)
            return
        }
        // "Never go online" is a promise: with it on, a missing model stays missing.
        if (offline()) {
            set(kind, ModelStatus.NotDownloaded)
            throw OfflineException(kind)
        }
        set(kind, ModelStatus.Downloading)
        try {
            download(kind)
            set(kind, ModelStatus.Ready)
        } catch (e: CancellationException) {
            set(kind, ModelStatus.NotDownloaded)
            throw e
        } catch (e: Exception) {
            set(kind, ModelStatus.Failed(e.message ?: e.javaClass.simpleName))
            throw e
        }
    }

    /** Start downloading [kinds] without waiting; progress shows up in [status]. */
    fun downloadInBackground(vararg kinds: ModelKind) {
        backgroundScope.launch {
            // One at a time, so a big model doesn't starve the small ones of bandwidth.
            kinds.forEach { runCatching { ensureReady(it) } }
        }
    }

    /**
     * Release a model's native memory. Its weights stay cached on disk. Closing
     * waits for any inference in progress, so it runs off the main thread.
     */
    suspend fun unload(kind: ModelKind) {
        val removed = synchronized(this) { instances.remove(kind) }
        withContext(Dispatchers.IO) { (removed as? AutoCloseable)?.close() }
    }

    suspend fun unloadAll() = ModelKind.entries.forEach { unload(it) }

    /** Bytes the SDK keeps in the app cache for downloaded models. */
    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles().orEmpty()
            .filter { it.name != "exports" && it.name != "image_manager_disk_cache" }
            .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }

    private fun set(kind: ModelKind, status: ModelStatus) = _status.update { it + (kind to status) }

    private fun isDownloaded(kind: ModelKind): Boolean = when (kind) {
        ModelKind.CLEAR -> clear.isDownloaded()
        ModelKind.EAR -> ear.isDownloaded()
        ModelKind.EMO -> emo.isDownloaded()
        ModelKind.EYE -> eye.isDownloaded()
        ModelKind.GIST -> gist.isDownloaded()
        ModelKind.MODERATOR -> moderator.isDownloaded()
        ModelKind.REDACT -> redact.isDownloaded()
        ModelKind.SHAPES -> shapes.isDownloaded()
        ModelKind.TONGUE -> true
        ModelKind.VOZ -> voz.isDownloaded()
        ModelKind.WHO -> who.isDownloaded()
    }

    private suspend fun download(kind: ModelKind) {
        when (kind) {
            ModelKind.CLEAR -> clear.download()
            ModelKind.EAR -> ear.download()
            ModelKind.EMO -> emo.download()
            ModelKind.EYE -> eye.download()
            ModelKind.GIST -> gist.download()
            ModelKind.MODERATOR -> moderator.download()
            ModelKind.REDACT -> redact.download()
            ModelKind.SHAPES -> shapes.download()
            ModelKind.TONGUE -> Unit
            ModelKind.VOZ -> voz.download()
            ModelKind.WHO -> who.download()
        }
    }

    private companion object {
        const val KEY_API = "api_key"
    }
}
