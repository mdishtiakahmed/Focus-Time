package com.focusguard.app.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.focusguard.app.LockService
import com.focusguard.app.MainActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START_FOCUS -> {
                Log.d(TAG, "Starting scheduled focus")
                val durationMinutes = intent.getIntExtra(EXTRA_DURATION, 0)
                if (durationMinutes > 0) {
                    val serviceIntent = Intent(context, LockService::class.java).apply {
                        putExtra(LockService.EXTRA_DURATION_MINUTES, durationMinutes)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
            ACTION_PRE_NOTIFY -> {
                Log.d(TAG, "Showing pre-notification")
                showPreNotification(context)
            }
        }
    }

    private fun showPreNotification(context: Context) {
        val channelId = "focus_schedule_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Focus Schedule",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled focus sessions"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Focus Mode Starting Soon")
            .setContentText("Focus Mode starts in 1 min. Save your work!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START_FOCUS = "com.focusguard.app.ACTION_START_FOCUS"
        const val ACTION_PRE_NOTIFY = "com.focusguard.app.ACTION_PRE_NOTIFY"
        const val EXTRA_DURATION = "extra_duration"
        const val TAG = "AlarmReceiver"
        const val NOTIFICATION_ID = 2001
    }
}
