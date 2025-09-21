package com.bmsit.faculty

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    // This function is called when the alarm goes off.
    override fun onReceive(context: Context, intent: Intent) {
        val meetingTitle = intent.getStringExtra("MEETING_TITLE") ?: "Meeting Reminder"
        val meetingLocation = intent.getStringExtra("MEETING_LOCATION") ?: "Check app for details"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // For Android 8.0 (Oreo) and higher, we need a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "meeting_reminders",
                "Meeting Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Build the notification
        val notification = NotificationCompat.Builder(context, "meeting_reminders")
            .setSmallIcon(R.drawable.ic_calendar) // Use our calendar icon
            .setContentTitle(meetingTitle)
            .setContentText("Your meeting is starting soon at $meetingLocation")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // Show the notification
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
