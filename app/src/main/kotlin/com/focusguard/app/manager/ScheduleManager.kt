package com.focusguard.app.manager

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.focusguard.app.receiver.AlarmReceiver
import java.util.Calendar

class ScheduleManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleFocus(
        hour: Int, 
        minute: Int, 
        durationMinutes: Int
    ) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            // If time is in the past, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        val startTime = calendar.timeInMillis
        
        // 1. Schedule Focus Start
        val startIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_START_FOCUS
            putExtra(AlarmReceiver.EXTRA_DURATION, durationMinutes)
        }
        
        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_START,
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Use setExactAndAllowWhileIdle for reliable timing
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            startTime,
            startPendingIntent
        )
        
        // 2. Schedule Pre-Notification (1 minute before)
        val notifyTime = startTime - (60 * 1000)
        if (notifyTime > System.currentTimeMillis()) {
            val notifyIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_PRE_NOTIFY
            }
            
            val notifyPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_NOTIFY,
                notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                notifyTime,
                notifyPendingIntent
            )
        }
    }
    
    fun cancelSchedule() {
        // Cancel Start Intent
        val startIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_START_FOCUS
        }
        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_START,
            startIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        startPendingIntent?.let { alarmManager.cancel(it) }
        
        // Cancel Notify Intent
        val notifyIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_PRE_NOTIFY
        }
        val notifyPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NOTIFY,
            notifyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        notifyPendingIntent?.let { alarmManager.cancel(it) }
    }

    companion object {
        private const val REQUEST_CODE_START = 100
        private const val REQUEST_CODE_NOTIFY = 101
    }
}
