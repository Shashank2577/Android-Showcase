package com.showcase.desertant.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.showcase.desertant.core.ModelHub
import com.showcase.desertant.core.ModelKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val LocalModelHub = compositionLocalOf<ModelHub> { error("No ModelHub provided") }

/**
 * Runs inference for a screen: makes sure the models are downloaded first, then
 * tracks busy state, the last error and how long the call took.
 */
class Runner(
    private val hub: ModelHub,
    private val kinds: List<ModelKind>,
    private val scope: CoroutineScope,
) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var lastMillis by mutableStateOf<Long?>(null)
        private set

    private var job: Job? = null

    /** Run [block] unless a run is already in flight. */
    fun run(block: suspend () -> Unit) {
        if (busy) return
        launch(block)
    }

    /** Cancel any in-flight run and start [block] — for live, as-you-type inference. */
    fun runLatest(block: suspend () -> Unit) {
        job?.cancel()
        launch(block)
    }

    private fun launch(block: suspend () -> Unit) {
        job = scope.launch {
            busy = true
            error = null
            try {
                kinds.forEach { hub.ensureReady(it) }
                val started = SystemClock.elapsedRealtime()
                block()
                lastMillis = SystemClock.elapsedRealtime() - started
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberRunner(vararg kinds: ModelKind): Runner {
    val hub = LocalModelHub.current
    val scope = rememberCoroutineScope()
    return remember(hub, scope) { Runner(hub, kinds.toList(), scope) }
}
