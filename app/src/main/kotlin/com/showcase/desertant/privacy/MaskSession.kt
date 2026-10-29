package com.showcase.desertant.privacy

import ai.desertant.redact.Options
import ai.desertant.redact.Redaction
import androidx.annotation.VisibleForTesting
import com.showcase.desertant.core.ModelHub
import com.showcase.desertant.core.ModelKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One masked text: what went in, what may leave, and how to put the details back. */
data class Masked(val original: String, val redaction: Redaction, val language: String?, val at: Long = System.currentTimeMillis()) {
    val text: String get() = redaction.redactedText
    val secrets: Int get() = redaction.items.size
}

/**
 * The text being masked, held in memory only, so a reply pasted back later can
 * have its placeholders restored. Nothing is written to disk; it's gone after
 * [TTL_MS], when the app process ends, or on "wipe everything".
 */
class MaskSession(private val hub: ModelHub, private val ledger: Ledger) {
    private val _current = MutableStateFlow<Masked?>(null)
    val current: StateFlow<Masked?> = _current.asStateFlow()

    fun active(): Masked? = _current.value?.takeIf { System.currentTimeMillis() - it.at < TTL_MS }.also { if (it == null) _current.value = null }

    suspend fun mask(text: String): Masked {
        hub.ensureReady(ModelKind.REDACT)
        val redaction = hub.redact.redaction(text, Options(minimumConfidence = 0.5))
        // Tongue is bundled, so naming the language never needs a download.
        val language = runCatching { hub.tongue.detect(text).language }.getOrNull()
        return Masked(text, redaction, language).also {
            _current.value = it
            ledger.add(LedgerEntry.TEXTS_MASKED)
        }
    }

    fun restore(reply: String): String = active()?.redaction?.restore(reply) ?: reply

    fun clear() {
        _current.value = null
    }

    /** For screenshot tests, which can't run the native model. */
    @VisibleForTesting
    internal fun preload(masked: Masked) {
        _current.value = masked
    }

    private companion object {
        const val TTL_MS = 30 * 60 * 1000L
    }
}
