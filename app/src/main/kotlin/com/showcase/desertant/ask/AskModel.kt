package com.showcase.desertant.ask

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import com.showcase.desertant.app
import com.showcase.desertant.core.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The language model behind "ask Burrow": Google's Gemma 4 E2B (Apache-2.0),
 * run on the phone by LiteRT-LM. It's a 2.6 GB download, fetched only when the
 * user asks and never while "never go online" is on, and it's loaded only
 * while the ask page is open (it takes about 1.5 GB of memory).
 */
object AskModel {
    const val NAME = "Gemma 4 E2B"
    const val SIZE_MB = 2_588
    private const val URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    private const val FILE = "gemma-4-E2B-it.litertlm"

    /** The model's window: room for a ~9,000-character part of a transcript plus its notes. */
    const val MAX_TOKENS = 8192

    /** Phones with less memory than this can still try, with a warning. */
    const val COMFORTABLE_RAM_GB = 8

    sealed interface State {
        data object Missing : State
        data class Downloading(val percent: Int) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    private fun dir(context: Context) = File(context.filesDir, "ask")
    fun file(context: Context) = File(dir(context), FILE)

    fun isReady(context: Context) = file(context).length() > 1_000_000_000L

    fun refresh(context: Context) {
        if (_state.value is State.Downloading) return
        _state.value = if (isReady(context)) State.Ready else (_state.value as? State.Failed ?: State.Missing)
    }

    fun ramGb(context: Context): Int {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return Math.round(info.totalMem / 1e9).toInt()
    }

    fun download(context: Context) {
        val app = context.app
        if (_state.value is State.Downloading) return
        if (app.privacy.neverOnline.value) {
            _state.value = State.Failed("“never go online” is on")
            return
        }
        dir(context).mkdirs()
        val free = StatFs(dir(context).path).availableBytes
        val have = File(file(context).path + ".part").length()
        if (free + have < SIZE_MB * 1_000_000L + 300_000_000L) {
            _state.value = State.Failed("not enough space: it needs ${SIZE_MB / 1000.0} GB free")
            return
        }
        _state.value = State.Downloading(0)
        app.scope.launch(Dispatchers.IO) {
            try {
                fetch(URL, file(context)) { _state.value = State.Downloading(it) }
                _state.value = State.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The .part file stays, so trying again carries on from here.
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun remove(context: Context) {
        release()
        dir(context).deleteRecursively()
        _state.value = State.Missing
    }

    // ---- the engine ----

    private val lock = Mutex()
    private var engine: Engine? = null

    /** Load the model (about 5–10 s the first time), on the GPU if the phone allows it. */
    suspend fun engine(context: Context): Engine = lock.withLock {
        engine ?: withContext(Dispatchers.Default) {
            val prefs = context.getSharedPreferences("ask", Context.MODE_PRIVATE)
            val path = file(context).path
            fun load(backend: Backend) = Engine(EngineConfig(modelPath = path, backend = backend, maxNumTokens = MAX_TOKENS, cacheDir = context.cacheDir.path)).also { it.initialize() }
            val gpuOk = prefs.getBoolean("gpu_ok", true)
            val e = if (gpuOk) {
                try {
                    load(Backend.GPU())
                } catch (t: Exception) {
                    prefs.edit().putBoolean("gpu_ok", false).apply()
                    load(Backend.CPU())
                }
            } else load(Backend.CPU())
            engine = e
            e
        }
    }

    private var releaseJob: Job? = null
    private var holders = 0

    /** Something needs the model (the ask page, a summary): keep it loaded. */
    @Synchronized
    fun keep() {
        holders++
        releaseJob?.cancel()
        releaseJob = null
    }

    /**
     * That something is done. When nothing holds the model any more, its
     * memory is freed a minute later, unless something needs it again first
     * (say, after opening a talk an answer cited).
     */
    @Synchronized
    fun releaseSoon(scope: CoroutineScope) {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders > 0) return
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(60_000)
            lock.withLock { if (holders == 0) release() }
        }
    }

    /** Free the model's memory; the next question loads it again. */
    fun release() {
        engine?.close()
        engine = null
    }
}

/** One-shot prompts for summaries: a fresh conversation each time, so parts never leak into each other. */
class LiteRtText(private val engine: Engine) : TextModel {
    override fun complete(system: String, prompt: String): String =
        engine.createConversation(
            ConversationConfig(systemInstruction = Contents.of(system), samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2)),
        ).use { c -> c.sendMessage(prompt).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text } }
}

/** A conversation with Gemma that hands tool calls back to Burrow instead of running them itself. */
class LiteRtChat(engine: Engine, system: String, specs: List<ToolSpec>) : ToolChat {
    private val conversation: Conversation = engine.createConversation(
        ConversationConfig(
            systemInstruction = Contents.of(system),
            tools = specs.map { s ->
                tool(
                    object : OpenApiTool {
                        override fun getToolDescriptionJsonString() = s.json
                        override fun execute(paramsJsonString: String) = error("Burrow runs its tools itself")
                    },
                )
            },
            // Low temperature: answers should stick to what the tools returned.
            samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
            automaticToolCalling = false,
        ),
    )

    override fun send(question: String) = reply(conversation.sendMessage(question))

    override fun sendResults(results: List<Pair<String, String>>) = reply(
        conversation.sendMessage(Message.tool(Contents.of(results.map { (name, json) -> Content.ToolResponse(name, json) }))),
    )

    private fun reply(m: Message) = ModelReply(
        text = m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text },
        calls = m.toolCalls.map { ToolCallRequest(it.name, it.arguments) },
    )

    override fun close() = conversation.close()
}
