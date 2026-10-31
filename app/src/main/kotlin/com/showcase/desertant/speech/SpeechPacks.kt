package com.showcase.desertant.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.showcase.desertant.app
import com.showcase.desertant.core.VOZ_LANGUAGES
import com.showcase.desertant.meetings.quietChunks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Speech recognition beyond Voz's 25 European languages, as downloadable
 * packs run on the phone with ONNX Runtime:
 *
 * - **Indian languages** — AI4Bharat's IndicConformer (MIT), one int8 CTC model
 *   per language for all 22 scheduled languages, from the community sherpa-onnx
 *   export `parismitaglobalsolutions/indicconformer-sherpa-onnx` (Apache-2.0).
 *   On 60 FLEURS test clips each, with Burrow's own feature code: Hindi 11.8%
 *   WER, Bengali 16.5%, Tamil 31.0% (CER 14.8%).
 * - **Every other language** — Meta's Omnilingual ASR 300M CTC (Apache-2.0),
 *   1,600+ languages, int8 from k2-fsa's sherpa-onnx export. Much weaker on
 *   Indian languages (Hindi 33% WER), so it's only the fallback.
 *
 * Packs download once, only when the user asks, and never while "never go
 * online" is on. They live in the shared model store (catalog ids
 * `speech.indic.<code>` and `speech.omnilingual`), so another app of the vendor
 * on this phone that has a pack hands it over instead of downloading it, and
 * every download is resumable and checked against the catalog's SHA-256.
 */
object SpeechPacks {
    enum class Kind { INDIC, UNIVERSAL }

    data class Pack(
        /** Language code as Ear reports it; [UNIVERSAL_CODE] for the catch-all. */
        val code: String,
        val name: String,
        val native: String,
        val sizeMb: Int,
        val kind: Kind,
        val modelUrl: String,
        val tokensUrl: String,
        /** Other codes Ear or Android may use for the same language. */
        val aliases: Set<String> = emptySet(),
    )

    const val UNIVERSAL_CODE = "*"

    private const val INDIC = "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"
    private const val OMNI = "https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12/resolve/main"

    private fun indic(code: String, name: String, native: String, vararg aliases: String) =
        Pack(code, name, native, 190, Kind.INDIC, "$INDIC/$code/model.int8.onnx", "$INDIC/tokens.txt", aliases.toSet())

    /** The 22 languages of India's Eighth Schedule, most spoken first. */
    val INDIAN = listOf(
        indic("hi", "Hindi", "हिन्दी", "hin"),
        indic("bn", "Bengali", "বাংলা", "ben"),
        indic("mr", "Marathi", "मराठी", "mar"),
        indic("te", "Telugu", "తెలుగు", "tel"),
        indic("ta", "Tamil", "தமிழ்", "tam"),
        indic("gu", "Gujarati", "ગુજરાતી", "guj"),
        indic("ur", "Urdu", "اردو", "urd"),
        indic("kn", "Kannada", "ಕನ್ನಡ", "kan"),
        indic("or", "Odia", "ଓଡ଼ିଆ", "ori", "ory"),
        indic("ml", "Malayalam", "മലയാളം", "mal"),
        indic("pa", "Punjabi", "ਪੰਜਾਬੀ", "pan"),
        indic("as", "Assamese", "অসমীয়া", "asm"),
        indic("mai", "Maithili", "मैथिली"),
        indic("sat", "Santali", "ᱥᱟᱱᱛᱟᱲᱤ"),
        indic("ks", "Kashmiri", "کٲشُر", "kas"),
        indic("ne", "Nepali", "नेपाली", "nep", "npi"),
        indic("sd", "Sindhi", "سنڌي", "snd"),
        indic("kok", "Konkani", "कोंकणी", "gom"),
        indic("doi", "Dogri", "डोगरी"),
        indic("mni", "Manipuri", "ꯃꯤꯇꯩꯂꯣꯟ"),
        indic("brx", "Bodo", "बड़ो"),
        indic("sa", "Sanskrit", "संस्कृतम्", "san"),
    )

    val UNIVERSAL = Pack(
        UNIVERSAL_CODE, "Every other language", "1,600+ languages", 350, Kind.UNIVERSAL,
        "$OMNI/model.int8.onnx", "$OMNI/tokens.txt",
    )

    val ALL = INDIAN + UNIVERSAL

    /** The Indian-language pack for [code], if there is one. */
    fun indianFor(code: String?): Pack? {
        val c = code?.lowercase()?.substringBefore('-')?.substringBefore('_') ?: return null
        return INDIAN.firstOrNull { it.code == c || c in it.aliases }
    }

    fun packFor(code: String): Pack? = if (code == UNIVERSAL_CODE) UNIVERSAL else indianFor(code)

    /**
     * Which pack writes a talk in [language] (Ear's code), or null when Voz does.
     * Indian languages always get their own pack — it's far more accurate than
     * the catch-all; anything else Voz doesn't speak goes to the catch-all.
     */
    fun route(language: String?): Pack? = when {
        language == null -> null
        language.lowercase().substringBefore('-') in VOZ_LANGUAGES -> null
        else -> indianFor(language) ?: UNIVERSAL
    }

    sealed interface State {
        data object Missing : State
        data class Downloading(val percent: Int) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<Map<String, State>>(emptyMap())
    val state: StateFlow<Map<String, State>> = _state.asStateFlow()

    /** The pack's id in the shared model catalog. */
    fun storeId(pack: Pack) = if (pack.kind == Kind.UNIVERSAL) "speech.omnilingual" else "speech.indic.${pack.code}"

    private fun modelFile(context: Context, pack: Pack) = context.app.models.pathOf(storeId(pack), "model.int8.onnx")
    private fun tokensFile(context: Context, pack: Pack) = context.app.models.pathOf(storeId(pack), "tokens.txt")

    fun isReady(context: Context, pack: Pack): Boolean = context.app.models.isReady(storeId(pack))

    /** Another app on this phone has the pack: getting it is a copy, not a download. */
    fun inSibling(context: Context, pack: Pack): Boolean = context.app.models.inSibling(storeId(pack))

    /**
     * Packs downloaded before the shared store (`filesDir/speech/<dir>`, model
     * renamed to model.onnx) move into it when they match the catalog, so the
     * update doesn't download them again.
     */
    fun adoptPreStorePacks(context: Context) {
        for (pack in ALL) {
            val old = preStoreDir(context, pack)
            if (!old.isDirectory) continue
            val adopted = runCatching {
                context.app.models.adopt(storeId(pack), mapOf("model.int8.onnx" to File(old, "model.onnx"), "tokens.txt" to File(old, "tokens.txt")))
            }.getOrDefault(false)
            // Only an adopted pack's old folder goes; one that doesn't match the
            // catalog stays until the pack is fetched again.
            if (adopted) old.deleteRecursively()
            context.app.log.add("speech", "${pack.name}: ${if (adopted) "moved into the shared store" else "old copy doesn't match the catalog; kept until fetched again"}")
        }
        File(context.filesDir, "speech").takeIf { it.list()?.isEmpty() == true }?.delete()
    }

    private fun preStoreDir(context: Context, pack: Pack) =
        File(context.filesDir, "speech/${if (pack.kind == Kind.UNIVERSAL) "universal" else pack.code}")

    /** Downloads in progress, so a wipe can stop them before they register. */
    private val signals = java.util.concurrent.ConcurrentHashMap<String, dev.baseplatform.base_models.CancelSignal>()

    /** Stops every pack download and drops its partial files (wipe). */
    fun cancelAll() = signals.values.forEach { it.cancel(discard = true) }

    fun refresh(context: Context) {
        _state.update { old ->
            ALL.associate { p ->
                p.code to when {
                    old[p.code] is State.Downloading -> old.getValue(p.code)
                    isReady(context, p) -> State.Ready
                    else -> old[p.code] as? State.Failed ?: State.Missing
                }
            }
        }
    }

    private fun set(code: String, s: State) = _state.update { it + (code to s) }

    /** Fetch a pack in the background; progress shows in [state]. */
    fun download(context: Context, pack: Pack) {
        val app = context.app
        if (_state.value[pack.code] is State.Downloading) return
        set(pack.code, State.Downloading(0))
        app.scope.launch(Dispatchers.IO) {
            // Asking sibling apps is a cross-process call: here, off the main thread.
            if (app.privacy.neverOnline.value && !inSibling(context, pack)) {
                app.log.add("speech", "${pack.name}: not downloaded, \"never go online\" is on")
                set(pack.code, State.Failed("“never go online” is on"))
                return@launch
            }
            val signal = dev.baseplatform.base_models.CancelSignal().also { signals[pack.code] = it }
            try {
                // A sibling app's copy if one has it, else a resumable, verified download.
                app.models.ensure(storeId(pack), progress = { p -> set(pack.code, State.Downloading((p * 100).toInt())) }, cancel = signal)
                preStoreDir(context, pack).deleteRecursively() // A stale copy from before the store, if one was left.
                set(pack.code, State.Ready)
            } catch (e: CancellationException) {
                throw e
            } catch (e: dev.baseplatform.base_models.DownloadCancelled) {
                set(pack.code, State.Missing)
            } catch (e: Exception) {
                app.log.add("speech", "${pack.name}: FAILED: ${e.message ?: e.javaClass.simpleName}")
                set(pack.code, State.Failed(e.message ?: e.javaClass.simpleName))
            } finally {
                signals.remove(pack.code, signal)
            }
        }
    }

    /** Burrow stops using the pack; the files go unless another app still uses them. */
    fun remove(context: Context, pack: Pack) {
        closeRecognizer()
        context.app.models.release(storeId(pack))
        set(pack.code, State.Missing)
    }

    // One recognizer at a time: a pack is a few hundred MB in memory.
    private var recognizer: Pair<String, Recognizer>? = null

    @Synchronized
    private fun recognizer(context: Context, pack: Pack): Recognizer {
        recognizer?.takeIf { it.first == pack.code }?.let { return it.second }
        closeRecognizer()
        val r = if (pack.kind == Kind.UNIVERSAL) WaveformCtc(modelFile(context, pack), tokensFile(context, pack))
            else MelCtc(modelFile(context, pack), tokensFile(context, pack))
        return r.also { recognizer = pack.code to it }
    }

    @Synchronized
    fun closeRecognizer() {
        recognizer?.second?.close()
        recognizer = null
    }

    /** Transcribe 16 kHz mono [samples] with [pack]; times are relative to the clip. */
    suspend fun transcribe(context: Context, pack: Pack, samples: FloatArray): List<TimedWord> = withContext(Dispatchers.Default) {
        val r = recognizer(context, pack)
        // Attention grows with the square of the length (and Omnilingual takes at most 40 s),
        // so feed short stretches cut at pauses.
        quietChunks(samples, MelFeatures.RATE, targetSec = 20.0, searchSec = 4.0).flatMap { (from, to) ->
            r.transcribe(samples.copyOfRange(from, to), from.toDouble() / MelFeatures.RATE)
        }
    }

    private abstract class Recognizer(model: File) : AutoCloseable {
        protected val env: OrtEnvironment = OrtEnvironment.getEnvironment()
        protected val session: OrtSession = env.createSession(
            model.path,
            OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            },
        )

        abstract fun transcribe(samples: FloatArray, offsetSec: Double): List<TimedWord>

        protected fun logits(feeds: Map<String, OnnxTensor>): Array<FloatArray> =
            session.run(feeds).use { result ->
                @Suppress("UNCHECKED_CAST")
                (result[0].value as Array<Array<FloatArray>>)[0]
            }

        override fun close() = session.close()
    }

    /** IndicConformer: NeMo log-mel features in, BPE CTC out. */
    private class MelCtc(model: File, tokens: File) : Recognizer(model) {
        private val featuresInput: String
        private val lengthInput: String?
        private val bandsFirst: Boolean
        private val decoder: CtcDecoder

        init {
            val inputs = session.inputInfo
            val features = inputs.entries.first { (it.value.info as? TensorInfo)?.shape?.size == 3 }
            featuresInput = features.key
            val shape = (features.value.info as TensorInfo).shape
            bandsFirst = shape[1] == MelFeatures.BANDS.toLong() || shape[2] != MelFeatures.BANDS.toLong()
            lengthInput = inputs.keys.firstOrNull { it != featuresInput }
            val out = session.outputInfo.values.first().info as TensorInfo
            decoder = CtcDecoder.fromTokensFile(tokens, out.shape.last().takeIf { it > 0 }?.toInt())
        }

        override fun transcribe(samples: FloatArray, offsetSec: Double): List<TimedWord> {
            if (samples.size < MelFeatures.RATE / 4) return emptyList()
            val mel = MelFeatures.compute(samples)
            val t = mel.size
            val bands = MelFeatures.BANDS
            val flat = FloatArray(t * bands)
            for (f in 0 until t) for (b in 0 until bands) {
                if (bandsFirst) flat[b * t + f] = mel[f][b] else flat[f * bands + b] = mel[f][b]
            }
            val shape = if (bandsFirst) longArrayOf(1, bands.toLong(), t.toLong()) else longArrayOf(1, t.toLong(), bands.toLong())
            OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), shape).use { x ->
                val len = lengthInput?.let { OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(t.toLong())), longArrayOf(1)) }
                try {
                    val feeds = buildMap { put(featuresInput, x); if (len != null) put(lengthInput!!, len) }
                    val out = logits(feeds)
                    // Each output frame covers (input frames / output frames) × 10 ms.
                    val frameSec = t * MelFeatures.HOP.toDouble() / MelFeatures.RATE / out.size.coerceAtLeast(1)
                    return decoder.decode(out, frameSec, offsetSec)
                } finally {
                    len?.close()
                }
            }
        }
    }

    /** Omnilingual: the normalised waveform in, character CTC out (blank 0, a space between words). */
    private class WaveformCtc(model: File, tokens: File) : Recognizer(model) {
        private val input = session.inputNames.first()
        private val decoder = CtcDecoder.fromTokensFile(tokens, blankId = 0)

        override fun transcribe(samples: FloatArray, offsetSec: Double): List<TimedWord> {
            if (samples.size < MelFeatures.RATE / 4) return emptyList()
            var mean = 0.0
            for (s in samples) mean += s
            mean /= samples.size
            var v = 0.0
            for (s in samples) { val d = s - mean; v += d * d }
            val std = sqrt(v / samples.size + 1e-7)
            val x = FloatArray(samples.size) { ((samples[it] - mean) / std).toFloat() }
            OnnxTensor.createTensor(env, FloatBuffer.wrap(x), longArrayOf(1, x.size.toLong())).use { t ->
                val out = logits(mapOf(input to t))
                val frameSec = samples.size.toDouble() / MelFeatures.RATE / out.size.coerceAtLeast(1)
                return decoder.decode(out, frameSec, offsetSec)
            }
        }
    }
}
