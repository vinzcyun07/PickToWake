package com.wyn.ptw

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ID = "pick_to_wake_channel"
    const val NOTIF_ID = 101

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PickToWake",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Foreground service for PickToWake (no UI)"
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("PickToWake đang chạy")
            .setContentText("Nhấc điện thoại lên ~90° để bật màn hình.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Tắt",
                stopPendingIntent(context)
            )
            .build()

    private fun stopPendingIntent(context: Context): PendingIntent {
        val i = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_STOP
        }
        return PendingIntent.getBroadcast(
            context,
            2001,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
