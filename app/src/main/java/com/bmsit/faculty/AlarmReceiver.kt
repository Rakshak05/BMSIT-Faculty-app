package com.bmsit.faculty

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    // This function is called by the Android system when the alarm time is reached.
    override fun onReceive(context: Context, intent: Intent) {
        // Get the meeting details that we passed from the DashboardFragment
        val meetingTitle = intent.getStringExtra("MEETING_TITLE") ?: "Meeting Reminder"
        val meetingLocation = intent.getStringExtra("MEETING_LOCATION") ?: "Check app for details"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // For modern Android versions (8.0+), we must create a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "meeting_reminders", // A unique ID for the channel
                "Meeting Reminders", // The user-visible name of the channel
                NotificationManager.IMPORTANCE_HIGH // Make it a high-priority notification
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Build the actual notification that the user will see.
        val notification = NotificationCompat.Builder(context, "meeting_reminders")
            .setSmallIcon(R.drawable.ic_calendar) // Use our existing calendar icon
            .setContentTitle(meetingTitle)
            .setContentText("Your meeting is starting soon at $meetingLocation")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // The notification will disappear when the user taps it
            .build()

        // Show the notification. We use a unique ID based on the current time
        // to ensure multiple notifications can be shown.
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

