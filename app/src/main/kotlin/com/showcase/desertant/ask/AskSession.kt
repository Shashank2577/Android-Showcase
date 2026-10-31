package com.showcase.desertant.ask

import android.content.Context
import com.showcase.desertant.app
import com.showcase.desertant.privacy.LedgerEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the ask page shows. */
data class AskUi(
    val question: String = "",
    val phase: Phase = Phase.IDLE,
    val steps: List<AskStep> = emptyList(),
    val pending: Confirmation? = null,
    val answer: String? = null,
    val sources: List<AskSource> = emptyList(),
    val error: String? = null,
    val openRoute: String? = null,
) {
    enum class Phase { IDLE, LOADING, THINKING, DONE, FAILED }

    val busy get() = phase == Phase.LOADING || phase == Phase.THINKING
}

/**
 * One question at a time, answered on the phone. Lives in the app (not the
 * screen) so the answer is still there after opening a cited talk and coming
 * back. Questions and answers are held in memory only, never saved.
 */
class AskSession(private val context: Context) {
    private val _ui = MutableStateFlow(AskUi())
    val ui: StateFlow<AskUi> = _ui.asStateFlow()

    private var job: Job? = null
    private var decision: CompletableDeferred<Boolean>? = null

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _ui.value.busy) return
        val app = context.app
        _ui.value = AskUi(question = q, phase = AskUi.Phase.LOADING)
        job = app.scope.launch {
            try {
                val engine = AskModel.engine(context)
                _ui.update { it.copy(phase = AskUi.Phase.THINKING) }
                val tools = BurrowTools(app.db)
                val outcome = withContext(Dispatchers.Default) {
                    LiteRtChat(engine, systemPrompt(), tools.specs).use { chat ->
                        AskAgent(tools).run(
                            chat, q,
                            onStep = { steps -> _ui.update { it.copy(steps = steps) } },
                            confirm = { c ->
                                val d = CompletableDeferred<Boolean>()
                                decision = d
                                _ui.update { it.copy(pending = c) }
                                d.await().also { _ui.update { it.copy(pending = null) } }
                            },
                        )
                    }
                }
                app.ledger.add(LedgerEntry.QUESTIONS_ANSWERED)
                _ui.update { it.copy(phase = AskUi.Phase.DONE, answer = outcome.answer, sources = outcome.sources, openRoute = outcome.openRoute) }
            } catch (e: CancellationException) {
                _ui.update { it.copy(phase = AskUi.Phase.IDLE, pending = null) }
                throw e
            } catch (e: Throwable) {
                // Native errors from the model runtime included: show them, don't crash.
                _ui.update { it.copy(phase = AskUi.Phase.FAILED, pending = null, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** The user's answer to "Burrow wants to …". */
    fun decide(yes: Boolean) {
        decision?.complete(yes)
        decision = null
    }

    fun routeHandled() = _ui.update { it.copy(openRoute = null) }

    fun clear() {
        job?.cancel()
        decision?.complete(false)
        _ui.value = AskUi()
    }

    companion object {
        fun systemPrompt(today: LocalDate = LocalDate.now()): String = """
            You are Burrow, a private assistant that runs entirely on the user's phone. You answer questions about the user's recorded talks (meetings and calls), the people in them, and their photos.
            Today is ${today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH))}.
            Rules:
            - Always call a tool to look things up before answering. Never make up names, dates, numbers or quotes.
            - Use what people actually said, and say which talk it came from.
            - If the tools find nothing, say so plainly and suggest what the user could try.
            - Answer in one to three short sentences, in the user's language. No lists or headings.
            - Only call rename_talk or start_recording when the user asks for exactly that.
        """.trimIndent()
    }
}
