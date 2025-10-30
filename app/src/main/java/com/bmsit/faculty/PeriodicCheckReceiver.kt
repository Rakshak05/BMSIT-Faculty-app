package com.bmsit.faculty

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import java.util.Calendar
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build

class PeriodicCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("PeriodicCheck", "Checking for new meetings")
        
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val currentUser = auth.currentUser
        
        if (currentUser != null) {
            checkForNewMeetings(context, db, currentUser.uid)
        }
    }
    
    private fun checkForNewMeetings(context: Context, db: FirebaseFirestore, userId: String) {
        try {
            // First, check if the user is a Developer
            db.collection("users").document(userId).get()
                .addOnSuccessListener { userDocument ->
                    val userDesignation = userDocument.getString("designation")
                    
                    // If user is a Developer, don't notify them about any meetings
                    if (userDesignation == "Developer") {
                        return@addOnSuccessListener
                    }
                    
                    // Get the time of the last check (you might want to store this in SharedPreferences)
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.MINUTE, -15) // Check for meetings in the last 15 minutes
                    val lastCheckTime = Timestamp(calendar.time)
                    
                    db.collection("meetings")
                        .whereGreaterThanOrEqualTo("dateTime", lastCheckTime)
                        .whereEqualTo("status", "Active")
                        .get()
                        .addOnSuccessListener { result ->
                            for (document in result) {
                                val meeting = document.toObject(Meeting::class.java)
                                // Check if this is a new meeting that the user should be notified about
                                if (shouldNotifyUser(meeting, userId)) {
                                    showNotification(context, meeting)
                                }
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("PeriodicCheck", "Error checking for meetings", exception)
                        }
                }
                .addOnFailureListener { exception ->
                    Log.e("PeriodicCheck", "Error checking user designation", exception)
                    // If we can't get the user's designation, proceed with notification
                    // to avoid missing notifications
                    
                    // Get the time of the last check (you might want to store this in SharedPreferences)
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.MINUTE, -15) // Check for meetings in the last 15 minutes
                    val lastCheckTime = Timestamp(calendar.time)
                    
                    db.collection("meetings")
                        .whereGreaterThanOrEqualTo("dateTime", lastCheckTime)
                        .whereEqualTo("status", "Active")
                        .get()
                        .addOnSuccessListener { result ->
                            for (document in result) {
                                val meeting = document.toObject(Meeting::class.java)
                                // Check if this is a new meeting that the user should be notified about
                                if (shouldNotifyUser(meeting, userId)) {
                                    showNotification(context, meeting)
                                }
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("PeriodicCheck", "Error checking for meetings", exception)
                        }
                }
        } catch (e: Exception) {
            Log.e("PeriodicCheck", "Error in checkForNewMeetings", e)
        }
    }
    
    private fun shouldNotifyUser(meeting: Meeting, userId: String): Boolean {
        // Check if user is the meeting scheduler (no need to notify)
        if (meeting.scheduledBy == userId) return false
        
        // Check if meeting is active
        if (meeting.status != "Active") return false
        
        // Check if user should be notified based on attendees
        return when (meeting.attendees) {
            "All Faculty" -> true // Simplified check - in a real implementation, you'd check the user's designation
            "All HODs" -> true // Simplified check
            "All Deans" -> true // Simplified check
            "Custom" -> meeting.customAttendeeUids.contains(userId)
            else -> false
        }
    }
    
    private fun showNotification(context: Context, meeting: Meeting) {
        try {
            // Create notification channel if needed
            ensureNotificationChannel(context)
            
            val title = "New Meeting: ${meeting.title}"
            val body = "Scheduled for ${formatRelativeDay(meeting.dateTime.toDate().time)} at ${formatTime(meeting.dateTime.toDate().time)} in ${meeting.location}"
            
            val builder = NotificationCompat.Builder(context, "meetings_channel")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            
            with(NotificationManagerCompat.from(context)) {
                notify(meeting.id.hashCode(), builder.build())
            }
        } catch (e: Exception) {
            Log.e("PeriodicCheck", "Error showing notification", e)
        }
    }
    
    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "meetings_channel"
            val name = "Meeting Notifications"
            val desc = "Notifications for meetings, reminders, cancellations, and reschedules"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = desc
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
    
    private fun formatRelativeDay(millis: Long): String {
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        return when {
            isSameDay(target, today) -> "today"
            isSameDay(target, tomorrow) -> "tomorrow"
            else -> {
                val sdf = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
                "on ${sdf.format(target.time)}"
            }
        }
    }
    
    private fun formatTime(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }
    
    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}