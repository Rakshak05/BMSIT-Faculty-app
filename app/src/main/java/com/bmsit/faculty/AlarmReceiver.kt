package com.bmsit.faculty

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    // This function is called by the Android system when the alarm time is reached.
    override fun onReceive(context: Context, intent: Intent) {
        // Forward to a foreground service that handles continuous ringing and user actions
        val meetingTitle = intent.getStringExtra("MEETING_TITLE") ?: "Meeting Reminder"
        val meetingLocation = intent.getStringExtra("MEETING_LOCATION") ?: "Check app for details"

        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra("MEETING_TITLE", meetingTitle)
            putExtra("MEETING_LOCATION", meetingLocation)
        }
        // Use ContextCompat to properly start foreground service across API levels
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

