package com.showcase.desertant.meetings

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.showcase.desertant.app
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.notify.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Processes one meeting in the background, so the user can lock the phone and walk away. */
class MeetingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val dao = applicationContext.app.db.meetings()
        if (dao.get(id) == null) return Result.success()
        // One meeting at a time: each needs the speech models' full memory. Each
        // meeting is its own work, so one failure never blocks the others.
        return oneAtATime.withLock {
            try {
                promote("Starting…")
                MeetingProcessor(applicationContext).process(id) { promote(it) }
                Notifications.done(
                    applicationContext, Notifications.MEETING_DONE_ID + id.toInt(),
                    "Meeting notes ready", "Transcript, speakers and highlights for “${dao.get(id)?.title ?: "your meeting"}” are ready.",
                    route = "meeting/$id",
                )
                SummaryWorker.enqueueIfWanted(applicationContext, id)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    dao.setStatus(id, MeetingStatus.FAILED, null, "Stopped before it finished. Tap Try again to continue.")
                }
                throw e
            } catch (e: OutOfMemoryError) {
                dao.setStatus(id, MeetingStatus.FAILED, null, "The phone ran out of memory while processing this recording.")
            } catch (e: Throwable) {
                dao.setStatus(id, MeetingStatus.FAILED, null, e.message ?: e.javaClass.simpleName)
            }
            // The failure is recorded on the meeting, with a retry button; the work itself is done.
            Result.success()
        }
    }

    /** Needed for expedited work on Android 11. */
    override suspend fun getForegroundInfo() = ForegroundInfo(
        Notifications.MEETING_WORK_ID,
        Notifications.progress(applicationContext, "Preparing meeting notes", "Starting…", 0, 0, id),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    /** Show progress as a foreground notification; if Android won't allow that right now, carry on without it. */
    private suspend fun promote(text: String) {
        try {
            setForeground(
                ForegroundInfo(
                    Notifications.MEETING_WORK_ID,
                    Notifications.progress(applicationContext, "Preparing meeting notes", text, 0, 0, id),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Background-start restrictions (ForegroundServiceStartNotAllowedException).
        }
    }

    companion object {
        private const val KEY_ID = "meeting"
        /** Shared with summaries: one heavy model job at a time. */
        internal val oneAtATime = Mutex()

        fun enqueue(context: Context, meetingId: Long) {
            val request = OneTimeWorkRequestBuilder<MeetingWorker>()
                .setInputData(workDataOf(KEY_ID to meetingId))
                // Expedited work may start a foreground service even from the background.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("meeting-$meetingId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
