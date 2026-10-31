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
import dev.baseplatform.base_models.CancelSignal
import dev.baseplatform.base_models.DownloadCancelled
import dev.baseplatform.base_models.SharedModelStore
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
import kotlinx.coroutines.runBlocking
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
 * from the on-disk copy without downloading again.
 *
 * Weights live in the shared model store ([store]), in the folder
 * `desertant.<key>` that every app of the vendor uses for that model: a model
 * another app on this phone already has is copied from it, not downloaded.
 */
class ModelHub(
    private val context: Context,
    private val store: SharedModelStore,
    private val offline: () -> Boolean = { false },
) {

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

    val clear: Clear get() = instance(ModelKind.CLEAR) { Clear(context, dir(ModelKind.CLEAR)) }
    val ear: Ear get() = instance(ModelKind.EAR) { Ear(context, dir(ModelKind.EAR)) }
    val emo: Emo get() = instance(ModelKind.EMO) { Emo(context, dir(ModelKind.EMO)) }
    val eye: Eye get() = instance(ModelKind.EYE) { Eye(context, dir(ModelKind.EYE)) }
    val gist: Gist get() = instance(ModelKind.GIST) { Gist(context, dir(ModelKind.GIST)) }
    val moderator: Moderator get() = instance(ModelKind.MODERATOR) { Moderator(context, dir(ModelKind.MODERATOR)) }
    val redact: Redact get() = instance(ModelKind.REDACT) { Redact(context, dir(ModelKind.REDACT)) }
    val shapes: Shapes get() = instance(ModelKind.SHAPES) { Shapes(context, dir(ModelKind.SHAPES)) }
    val tongue: Tongue get() = instance(ModelKind.TONGUE) { Tongue.bundled(context) }
    val voz: Voz get() = instance(ModelKind.VOZ) { Voz(context, dir(ModelKind.VOZ)) }
    val who: Who get() = instance(ModelKind.WHO) { Who(context, dir(ModelKind.WHO)) }

    /** The model's catalog id in the shared store, `desertant.<key>`. */
    fun storeId(kind: ModelKind) = "desertant.${kind.name.lowercase()}"

    /**
     * Where the SDK keeps [kind]'s weights: its folder in the shared store, or
     * for a model downloaded before the store, the app cache where the SDK put
     * it then (the SDK's own layout there can't be moved safely). Those models
     * keep working from the cache; once Burrow fetches them again they move
     * into the store.
     */
    private fun dir(kind: ModelKind): String =
        if (kind in legacyKinds()) context.cacheDir.absolutePath
        else store.pathOf(storeId(kind)).apply { mkdirs() }.path

    private fun legacyKinds(): Set<ModelKind> =
        prefs.getStringSet(KEY_LEGACY, emptySet())!!.mapNotNull { n -> ModelKind.entries.firstOrNull { it.name == n } }.toSet()

    private fun setLegacy(kinds: Set<ModelKind>) = prefs.edit().putStringSet(KEY_LEGACY, kinds.map { it.name }.toSet()).apply()

    /**
     * Once, after the update to the shared store: which models the SDK already
     * has in the app cache (its old default folder). Blocking: call off the main thread.
     */
    fun detectPreStoreModels() {
        if (prefs.getBoolean(KEY_LEGACY_DETECTED, false)) return
        val found = ModelKind.entries.filter { !it.bundled }.filter { kind ->
            val old = runCatching { legacyInstance(kind) }.getOrNull() ?: return@filter false
            try {
                runCatching { isDownloadedOf(old) }.getOrDefault(false)
            } finally {
                (old as? AutoCloseable)?.runCatching { close() }
            }
        }.toSet()
        setLegacy(found)
        prefs.edit().putBoolean(KEY_LEGACY_DETECTED, true).apply()
        // Instances made before this ran point at the store; recreate them.
        synchronized(this) { found.forEach { k -> (instances.remove(k) as? AutoCloseable)?.runCatching { close() } } }
    }

    /** The SDK's own default folder (the app cache), as before the store. */
    private fun legacyInstance(kind: ModelKind): Any? = when (kind) {
        ModelKind.CLEAR -> Clear(context)
        ModelKind.EAR -> Ear(context)
        ModelKind.EMO -> Emo(context)
        ModelKind.EYE -> Eye(context)
        ModelKind.GIST -> Gist(context)
        ModelKind.MODERATOR -> Moderator(context)
        ModelKind.REDACT -> Redact(context)
        ModelKind.SHAPES -> Shapes(context)
        ModelKind.VOZ -> Voz(context)
        ModelKind.WHO -> Who(context)
        ModelKind.TONGUE -> null
    }

    /** Installs in progress, so a wipe can stop them before they register. */
    private val signals = HashMap<ModelKind, CancelSignal>()

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
        val id = storeId(kind)
        // Registry and sibling checks read files and call other apps: never on the main thread.
        val (downloaded, registered, inSibling) = withContext(Dispatchers.IO) {
            val d = runCatching { isDownloaded(kind) }.getOrDefault(false)
            Triple(d, store.isReady(id), !d && store.inSibling(id))
        }
        if (downloaded && kind in legacyKinds()) {
            set(kind, ModelStatus.Ready) // Still in the old cache folder; works as before.
            return
        }
        if (kind in legacyKinds()) {
            // The cache copy is gone (Android may clear the cache): from now on, the store.
            setLegacy(legacyKinds() - kind)
            unload(kind)
        }
        if (downloaded && registered) {
            withContext(Dispatchers.IO) { store.ensure(id) { _, _ -> } } // Claim it for Burrow.
            set(kind, ModelStatus.Ready)
            return
        }
        // "Never go online" is a promise: with it on, a missing model stays missing
        // (unless a sibling app hands it over, which is not going online).
        if (offline() && !downloaded && !inSibling) {
            set(kind, ModelStatus.NotDownloaded)
            throw OfflineException(kind)
        }
        set(kind, ModelStatus.Downloading)
        val signal = CancelSignal().also { synchronized(signals) { signals[kind] = it } }
        try {
            withContext(Dispatchers.IO) {
                // The store copies the folder from a sibling app when one has it,
                // then the SDK downloads only if its weights still aren't there.
                store.ensure(id, cancel = signal, sdkInstall = { _, _ ->
                    if (!isDownloaded(kind)) {
                        if (store.isOffline()) throw OfflineException(kind)
                        runBlocking { download(kind) }
                    }
                })
            }
            set(kind, ModelStatus.Ready)
        } catch (e: CancellationException) {
            set(kind, ModelStatus.NotDownloaded)
            throw e
        } catch (e: DownloadCancelled) {
            set(kind, ModelStatus.NotDownloaded)
            throw e
        } catch (e: Exception) {
            set(kind, ModelStatus.Failed(e.message ?: e.javaClass.simpleName))
            throw e
        } finally {
            synchronized(signals) { if (signals[kind] === signal) signals.remove(kind) }
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

    /** Bytes of the models in the shared store (Burrow's copies of every model, speech packs included). */
    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) { store.bytesOnDisk() }

    /**
     * Lets go of every model Burrow claimed (files no other app uses are
     * deleted) and of the ones still in the old cache folder. Installs in
     * progress stop first, so none registers itself after the wipe.
     */
    suspend fun releaseAll() = withContext(Dispatchers.IO) {
        synchronized(signals) { signals.values.forEach { it.cancel(discard = true) } }
        unloadAll()
        store.claimedByThisApp().forEach { store.release(it) }
        setLegacy(emptySet())
    }

    private fun set(kind: ModelKind, status: ModelStatus) = _status.update { it + (kind to status) }

    private fun isDownloadedOf(m: Any): Boolean = when (m) {
        is Clear -> m.isDownloaded()
        is Ear -> m.isDownloaded()
        is Emo -> m.isDownloaded()
        is Eye -> m.isDownloaded()
        is Gist -> m.isDownloaded()
        is Moderator -> m.isDownloaded()
        is Redact -> m.isDownloaded()
        is Shapes -> m.isDownloaded()
        is Voz -> m.isDownloaded()
        is Who -> m.isDownloaded()
        else -> true
    }

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
        const val KEY_LEGACY = "legacy_cache_models"
        const val KEY_LEGACY_DETECTED = "legacy_cache_detected"
    }
}
