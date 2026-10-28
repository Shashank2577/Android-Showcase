package com.showcase.desertant.meetings

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.showcase.desertant.R
import com.showcase.desertant.app
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.MeetingSource
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(
        val meetingId: Long,
        val elapsedMs: Long,
        val paused: Boolean,
        /** Recent input levels, 0..1, newest last. */
        val levels: List<Float>,
    ) : RecorderState
}

/**
 * Records a meeting as compact AAC in a foreground service, so recording keeps
 * going with the screen off or while you use other apps. Stopping queues the
 * meeting for on-device processing.
 */
class RecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recorder: MediaRecorder? = null
    private var meetingId = -1L
    private var file: File? = null
    private var ticker: Job? = null
    private var insert: Job? = null
    private var startedAt = 0L
    private var pausedTotal = 0L
    private var pausedAt = 0L
    private val levels = ArrayDeque<Float>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (recorder == null) start(intent.getStringExtra(EXTRA_TITLE))
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start(title: String?) {
        ServiceCompat.startForeground(this, Notifications.RECORDING_ID, notification(false), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        val dir = File(filesDir, "meetings").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val out = File(dir, "rec-$now.m4a")
        file = out
        @Suppress("DEPRECATION")
        val r = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(32_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(out.absolutePath)
        }
        try {
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            _state.value = RecorderState.Idle
            stopSelf()
            return
        }
        recorder = r
        startedAt = SystemClock.elapsedRealtime()
        pausedTotal = 0
        insert = scope.launch {
            meetingId = app.db.meetings().insert(
                MeetingEntity(
                    title = title?.takeIf { it.isNotBlank() } ?: "Recording…",
                    titleEdited = !title.isNullOrBlank(),
                    createdAt = now,
                    audioPath = out.absolutePath,
                    source = MeetingSource.RECORDED,
                    status = MeetingStatus.RECORDING,
                ),
            )
            publish()
        }
        ticker = scope.launch {
            var tick = 0
            while (isActive) {
                val level = if (pausedAt == 0L) ((recorder?.maxAmplitude ?: 0) / 32767f).coerceIn(0f, 1f) else 0f
                levels.addLast(level)
                while (levels.size > 120) levels.removeFirst()
                publish()
                if (++tick % 10 == 0) updateNotification()
                delay(100)
            }
        }
    }

    private fun elapsed(): Long {
        val now = if (pausedAt != 0L) pausedAt else SystemClock.elapsedRealtime()
        return now - startedAt - pausedTotal
    }

    private fun publish() {
        if (recorder == null || meetingId < 0) return
        _state.value = RecorderState.Recording(meetingId, elapsed(), pausedAt != 0L, levels.toList())
    }

    private fun pause() {
        if (recorder == null || pausedAt != 0L) return
        recorder?.pause()
        pausedAt = SystemClock.elapsedRealtime()
        publish(); updateNotification()
    }

    private fun resume() {
        if (recorder == null || pausedAt == 0L) return
        recorder?.resume()
        pausedTotal += SystemClock.elapsedRealtime() - pausedAt
        pausedAt = 0
        publish(); updateNotification()
    }

    private fun stop() {
        val r = recorder ?: run { stopSelf(); return }
        if (pausedAt != 0L) resume()
        val duration = elapsed() / 1000.0
        ticker?.cancel()
        runCatching { r.stop() }
        r.release()
        recorder = null
        _state.value = RecorderState.Idle
        scope.launch {
            insert?.join()
            val id = meetingId
            val dao = app.db.meetings()
            dao.get(id)?.let { m ->
                if (duration < 2) {
                    dao.delete(id)
                    file?.delete()
                } else {
                    dao.update(m.copy(durationSec = duration, status = MeetingStatus.QUEUED, progress = "Waiting to be processed"))
                    MeetingWorker.enqueue(this@RecorderService, id)
                }
            }
            ServiceCompat.stopForeground(this@RecorderService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(Notifications.RECORDING_ID, notification(pausedAt != 0L))
    }

    private fun notification(paused: Boolean) = NotificationCompat.Builder(this, Notifications.CHANNEL_RECORDING)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(if (paused) "Recording paused" else "Recording meeting")
        .setContentText("${formatClock(elapsed() / 1000.0)} · stays on this phone")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(Notifications.openApp(this, "record"))
        .addAction(0, if (paused) "Resume" else "Pause", action(if (paused) ACTION_RESUME else ACTION_PAUSE))
        .addAction(0, "Stop & save", action(ACTION_STOP))
        .build()

    private fun action(name: String) = PendingIntent.getService(
        this, name.hashCode(), Intent(this, RecorderService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        if (recorder != null) stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TITLE = "title"

        private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
        val state: StateFlow<RecorderState> = _state.asStateFlow()

        fun start(context: Context, title: String?) = send(context, ACTION_START) { putExtra(EXTRA_TITLE, title) }
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String, extras: Intent.() -> Unit = {}) {
            val intent = Intent(context, RecorderService::class.java).setAction(action).apply(extras)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }
    }
}
