package dev.micr0.localmathy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifications {
    private const val CHANNEL_PROGRESS = "solving"
    private const val CHANNEL_RESULTS = "results"
    const val ONGOING_ID = 1
    const val RESULT_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "Solving progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shown while a question is being solved" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULTS,
                "Results",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Answer ready" },
        )
    }

    fun ongoing(context: Context): Notification =
        Notification.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Solving…")
            .setContentText("VibeThinker is working on your question")
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .build()

    fun postResult(context: Context, success: Boolean) {
        val notification = Notification.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(if (success) "Answer ready" else "Solving failed")
            .setContentText(
                if (success) "Tap to see the solution" else "Tap to see what went wrong",
            )
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(RESULT_ID, notification)
    }

    fun cancelResult(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(RESULT_ID)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
