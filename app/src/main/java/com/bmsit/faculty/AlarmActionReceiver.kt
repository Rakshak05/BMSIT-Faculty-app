package com.bmsit.faculty

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmRingingService.ACTION_STOP -> {
                // Stop the foreground service (which also stops the ringtone)
                context.stopService(Intent(context, AlarmRingingService::class.java))
            }
            AlarmRingingService.ACTION_SNOOZE -> {
                // Stop current ringing
                context.stopService(Intent(context, AlarmRingingService::class.java))

                // Reschedule alarm after snooze minutes
                val snooze = intent.getIntExtra("SNOOZE_MINUTES", 5)
                val title = intent.getStringExtra("MEETING_TITLE") ?: "Meeting Reminder"
                val location = intent.getStringExtra("MEETING_LOCATION") ?: "Check app for details"

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("MEETING_TITLE", title)
                    putExtra("MEETING_LOCATION", location)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    99001, // Use the constant value directly
                    alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = System.currentTimeMillis() + snooze * 60_000L
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }
}
