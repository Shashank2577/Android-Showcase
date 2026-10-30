package com.showcase.desertant.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.showcase.desertant.app
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Speech recognition for languages Voz doesn't cover, with AI4Bharat's
 * IndicConformer (MIT), run on the phone with ONNX Runtime.
 *
 * The models are the int8, CTC-only exports of IndicConformer for sherpa-onnx
 * (one ONNX graph per language plus its `tokens.txt`). They're downloaded once
 * from Hugging Face, only when the user asks, and never while "never go online"
 * is on.
 */
object IndicSpeech {
    data class Language(
        val code: String,
        val name: String,
        val native: String,
        val sizeMb: Int,
        /** Hugging Face repo and file paths of the int8 sherpa-onnx export. */
        val repo: String,
        val model: String,
        val tokens: String,
    )

    val LANGUAGES = listOf(
        Language("hi", "Hindi", "हिन्दी", 140, "ngiftsun/indicconformer-hi-int8-sherpa", "model.int8.onnx", "tokens.txt"),
    )

    /** Ear may report a language by its two- or three-letter code. */
    private val ALIASES = mapOf("hin" to "hi")

    sealed interface State {
        data object Unknown : State
        data object Missing : State
        data class Downloading(val percent: Int) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<Map<String, State>>(emptyMap())
    val state: StateFlow<Map<String, State>> = _state.asStateFlow()

    fun languageFor(code: String?): Language? {
        val c = code?.lowercase()?.substringBefore('-') ?: return null
        val norm = ALIASES[c] ?: c
        return LANGUAGES.firstOrNull { it.code == norm }
    }

    private fun dir(context: Context, code: String) = File(context.filesDir, "speech/$code")
    private fun modelFile(context: Context, lang: Language) = File(dir(context, lang.code), "model.onnx")
    private fun tokensFile(context: Context, lang: Language) = File(dir(context, lang.code), "tokens.txt")

    fun isReady(context: Context, code: String): Boolean {
        val lang = languageFor(code) ?: return false
        return modelFile(context, lang).length() > 1_000_000 && tokensFile(context, lang).length() > 0
    }

    fun refresh(context: Context) {
        _state.update { old ->
            LANGUAGES.associate { l ->
                l.code to when {
                    old[l.code] is State.Downloading -> old.getValue(l.code)
                    isReady(context, l.code) -> State.Ready
                    else -> old[l.code] as? State.Failed ?: State.Missing
                }
            }
        }
    }

    private fun set(code: String, s: State) = _state.update { it + (code to s) }

    /** Fetch a language's model in the background; progress shows in [state]. */
    fun download(context: Context, code: String) {
        val app = context.app
        val lang = languageFor(code) ?: return
        if (_state.value[lang.code] is State.Downloading) return
        if (app.privacy.neverOnline.value) {
            set(lang.code, State.Failed("“never go online” is on"))
            return
        }
        set(lang.code, State.Downloading(0))
        app.scope.launch(Dispatchers.IO) {
            try {
                val d = dir(context, lang.code).apply { mkdirs() }
                fetch("https://huggingface.co/${lang.repo}/resolve/main/${lang.tokens}", File(d, "tokens.txt")) {}
                fetch("https://huggingface.co/${lang.repo}/resolve/main/${lang.model}", File(d, "model.onnx")) { p -> set(lang.code, State.Downloading(p)) }
                set(lang.code, State.Ready)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dir(context, lang.code).deleteRecursively()
                set(lang.code, State.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun remove(context: Context, code: String) {
        val lang = languageFor(code) ?: return
        closeRecognizer()
        dir(context, lang.code).deleteRecursively()
        set(lang.code, State.Missing)
    }

    private fun fetch(url: String, out: File, progress: (Int) -> Unit) {
        val tmp = File(out.path + ".part")
        var conn = URL(url).openConnection() as HttpURLConnection
        // Hugging Face answers with a redirect to its CDN.
        var hops = 0
        while (conn.responseCode in 300..399 && hops++ < 5) {
            val next = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = URL(URL(url), next).openConnection() as HttpURLConnection
        }
        if (conn.responseCode != 200) throw IllegalStateException("the model files weren't found (HTTP ${conn.responseCode})")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(1 shl 16)
                var read = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    read += n
                    if (total > 0) {
                        val p = (read * 100 / total).toInt()
                        if (p != last) { last = p; progress(p) }
                    }
                }
            }
        }
        if (!tmp.renameTo(out)) throw IllegalStateException("couldn't save ${out.name}")
    }

    // One recognizer at a time: the model is a few hundred MB in memory.
    private var recognizer: Pair<String, Recognizer>? = null

    @Synchronized
    private fun recognizer(context: Context, lang: Language): Recognizer {
        recognizer?.takeIf { it.first == lang.code }?.let { return it.second }
        closeRecognizer()
        return Recognizer(modelFile(context, lang), tokensFile(context, lang)).also { recognizer = lang.code to it }
    }

    @Synchronized
    fun closeRecognizer() {
        recognizer?.second?.close()
        recognizer = null
    }

    /** Transcribe 16 kHz mono [samples] in [code]; times are relative to the clip. */
    suspend fun transcribe(context: Context, code: String, samples: FloatArray): List<TimedWord> = withContext(Dispatchers.Default) {
        val lang = languageFor(code) ?: error("No speech model for $code")
        val r = recognizer(context, lang)
        // Conformer attention grows with the square of the length, so feed it short stretches cut at pauses.
        quietChunks(samples, MelFeatures.RATE, targetSec = 20.0, searchSec = 4.0).flatMap { (from, to) ->
            r.transcribe(samples.copyOfRange(from, to), from.toDouble() / MelFeatures.RATE)
        }
    }

    private class Recognizer(model: File, tokens: File) : AutoCloseable {
        private val env = OrtEnvironment.getEnvironment()
        private val session: OrtSession = env.createSession(
            model.path,
            OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            },
        )
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

        fun transcribe(samples: FloatArray, offsetSec: Double): List<TimedWord> {
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
                val feeds = HashMap<String, OnnxTensor>()
                feeds[featuresInput] = x
                val len = lengthInput?.let { OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(t.toLong())), longArrayOf(1)) }
                if (len != null) feeds[lengthInput!!] = len
                try {
                    session.run(feeds).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val logits = (result[0].value as Array<Array<FloatArray>>)[0]
                        // Each output frame covers (input frames / output frames) × 10 ms; 80 ms for IndicConformer.
                        val frameSec = t * MelFeatures.HOP.toDouble() / MelFeatures.RATE / logits.size.coerceAtLeast(1)
                        return decoder.decode(logits, frameSec, offsetSec)
                    }
                } finally {
                    len?.close()
                }
            }
        }

        override fun close() = session.close()
    }
}
