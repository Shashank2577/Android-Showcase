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
import com.showcase.desertant.ask.AskModel
import com.showcase.desertant.ask.TalkSummaries
import com.showcase.desertant.notify.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/** Writes a talk's summary in the background with the on-device model. */
class SummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val language = inputData.getString(KEY_LANGUAGE)
        if (!AskModel.isReady(applicationContext)) return Result.success()
        return MeetingWorker.oneAtATime.withLock {
            try {
                promote()
                TalkSummaries.summarize(applicationContext, id, language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                TalkSummaries.failed(id, e.message ?: e.javaClass.simpleName)
            }
            Result.success()
        }
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(
        Notifications.MEETING_WORK_ID,
        Notifications.progress(applicationContext, "Summarising a talk", "on this phone", 0, 0, id),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    private suspend fun promote() {
        try {
            setForeground(getForegroundInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val KEY_ID = "meeting"
        private const val KEY_LANGUAGE = "language"

        /** Summarise [meetingId] now, in [language] (default: the setting). */
        fun enqueue(context: Context, meetingId: Long, language: String? = null) {
            TalkSummaries.queued(meetingId)
            val request = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(workDataOf(KEY_ID to meetingId, KEY_LANGUAGE to language))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("summary-$meetingId", ExistingWorkPolicy.REPLACE, request)
        }

        /** After a talk's notes are ready: summarise it if the model is here and the user wants that. */
        fun enqueueIfWanted(context: Context, meetingId: Long) {
            if (context.app.summaries.auto.value && AskModel.isReady(context)) enqueue(context, meetingId)
        }
    }
}
