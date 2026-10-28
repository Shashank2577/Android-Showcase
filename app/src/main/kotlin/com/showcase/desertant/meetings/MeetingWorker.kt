package com.showcase.desertant.meetings

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.showcase.desertant.app
import com.showcase.desertant.data.MeetingStatus
import com.showcase.desertant.notify.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Processes one meeting in the background, so the user can lock the phone and walk away. */
class MeetingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val dao = applicationContext.app.db.meetings()
        val meeting = dao.get(id) ?: return Result.success()
        setForeground(info("Starting…"))
        return try {
            MeetingProcessor(applicationContext).process(id) { setForeground(info(it)) }
            Notifications.done(
                applicationContext, Notifications.MEETING_DONE_ID + id.toInt(),
                "Meeting notes ready", "Transcript, speakers and highlights for “${dao.get(id)?.title ?: meeting.title}” are ready.",
                route = "meeting/$id",
            )
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { dao.setStatus(id, MeetingStatus.QUEUED, "Paused — will resume", null) }
            throw e
        } catch (e: OutOfMemoryError) {
            dao.setStatus(id, MeetingStatus.FAILED, null, "This recording is too long for the phone's memory. Try a recording under an hour.")
            Result.failure()
        } catch (e: Throwable) {
            dao.setStatus(id, MeetingStatus.FAILED, null, e.message ?: e.javaClass.simpleName)
            Result.failure()
        }
    }

    private fun info(text: String) = ForegroundInfo(
        Notifications.MEETING_WORK_ID,
        Notifications.progress(applicationContext, "Preparing meeting notes", text, 0, 0, id),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    companion object {
        private const val KEY_ID = "meeting"
        private const val CHAIN = "meetings"

        /** Meetings are processed one at a time: each needs the speech models' full memory. */
        fun enqueue(context: Context, meetingId: Long) {
            val request = OneTimeWorkRequestBuilder<MeetingWorker>()
                .setInputData(workDataOf(KEY_ID to meetingId))
                .addTag("meeting-$meetingId")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(CHAIN, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
