package dev.ririxi.bunnylock.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.ririxi.bunnylock.MainActivity
import dev.ririxi.bunnylock.R

object LockNotifier {
    const val ACTION_UNLOCK_ALL = "dev.ririxi.bunnylock.action.UNLOCK_ALL"
    private const val CHANNEL_ID = "locks"
    private const val NOTIFICATION_ID = 1

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL_ID, context.getString(R.string.system_notify_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.system_notify_channel_description)
                setShowBadge(false)
            }
        manager(context).createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        lines: List<String>,
        lockedSinceMillis: Long,
    ) {
        val permissionMissing =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (permissionMissing) return
        ensureChannel(context)
        val openApp =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val unlockAll =
            PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java).setAction(ACTION_UNLOCK_ALL).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle(context.getString(R.string.system_notify_title))
                .setContentText(lines.joinToString(context.getString(R.string.system_notify_line_separator)))
                .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
                .setWhen(lockedSinceMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp)
                .addAction(Notification.Action.Builder(null, context.getString(R.string.common_unlock_all), unlockAll).build())
                .build()
        manager(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) = manager(context).cancel(NOTIFICATION_ID)
}
