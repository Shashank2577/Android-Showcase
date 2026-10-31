package com.showcase.desertant.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.showcase.desertant.app
import com.showcase.desertant.core.VOZ_LANGUAGES
import com.showcase.desertant.core.fetch
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
 * online" is on.
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

    private fun dir(context: Context, pack: Pack) = File(context.filesDir, "speech/${if (pack.kind == Kind.UNIVERSAL) "universal" else pack.code}")
    private fun modelFile(context: Context, pack: Pack) = File(dir(context, pack), "model.onnx")
    private fun tokensFile(context: Context, pack: Pack) = File(dir(context, pack), "tokens.txt")

    fun isReady(context: Context, pack: Pack): Boolean =
        modelFile(context, pack).length() > 1_000_000 && tokensFile(context, pack).length() > 0

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
        if (app.privacy.neverOnline.value) {
            set(pack.code, State.Failed("“never go online” is on"))
            return
        }
        set(pack.code, State.Downloading(0))
        app.scope.launch(Dispatchers.IO) {
            try {
                dir(context, pack).mkdirs()
                fetch(pack.tokensUrl, tokensFile(context, pack)) {}
                fetch(pack.modelUrl, modelFile(context, pack)) { p -> set(pack.code, State.Downloading(p)) }
                set(pack.code, State.Ready)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dir(context, pack).deleteRecursively()
                set(pack.code, State.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun remove(context: Context, pack: Pack) {
        closeRecognizer()
        dir(context, pack).deleteRecursively()
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
