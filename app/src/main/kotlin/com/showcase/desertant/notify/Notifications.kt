package com.showcase.desertant.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.showcase.desertant.MainActivity
import com.showcase.desertant.R
import java.util.UUID

object Notifications {
    const val CHANNEL_WORK = "work"
    const val CHANNEL_DONE = "done"
    const val CHANNEL_RECORDING = "recording"

    const val PHOTO_SCAN_ID = 1001
    const val PHOTO_DONE_ID = 1002
    const val RECORDING_ID = 1003
    const val MEETING_WORK_ID = 1004
    const val MEETING_DONE_ID = 1005

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_WORK, "Background analysis", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_DONE, "Results ready", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_RECORDING, "Recording", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    fun openApp(context: Context, route: String? = null): PendingIntent = PendingIntent.getActivity(
        context,
        route.hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(context: Context, title: String, text: String, done: Int, total: Int, workId: UUID?): Notification =
        NotificationCompat.Builder(context, CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .setProgress(total, done, total == 0)
            .apply {
                if (workId != null) {
                    addAction(0, "Stop", WorkManager.getInstance(context).createCancelPendingIntent(workId))
                }
            }
            .build()

    fun done(context: Context, id: Int, title: String, text: String, route: String? = null) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(context, route))
                .build(),
        )
    }
}
