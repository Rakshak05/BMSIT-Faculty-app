package com.bmsit.faculty

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.concurrent.Executors

class AutoEndMeetingReceiver : BroadcastReceiver() {
    // Use a single thread executor to prevent multiple instances from running simultaneously
    private val executor = Executors.newSingleThreadExecutor()
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AutoEndMeeting", "Checking for meetings that should be auto-ended")
        
        // Run the check on a background thread to avoid blocking the main thread
        executor.execute {
            try {
                val auth = FirebaseAuth.getInstance()
                val db = FirebaseFirestore.getInstance()
                val currentUser = auth.currentUser
                
                Log.d("AutoEndMeeting", "Running auto-end meeting check")
                if (currentUser != null) {
                    autoEndMeetings(db, currentUser.uid, context)
                } else {
                    // Even if no user is logged in, still try to end meetings
                    // This ensures meetings end even if the host is not currently logged in
                    autoEndMeetings(db, "", context)
                }
            } catch (e: Exception) {
                Log.e("AutoEndMeeting", "Error in onReceive", e)
            }
        }
    }
    
    private fun autoEndMeetings(db: FirebaseFirestore, userId: String, context: Context) {
        try {
            // Get all active meetings, not just those hosted by current user
            // This ensures meetings are ended even if the host is not active
            val query = db.collection("meetings")
                .whereEqualTo("status", "Active")
            
            Log.d("AutoEndMeeting", "Querying all active meetings")
            
            query.get()
                .addOnSuccessListener { result ->
                    Log.d("AutoEndMeeting", "Found ${result.size()} active meetings to check")
                    
                    val batch = db.batch()
                    var meetingsToEnd = 0
                    val endedMeetings = mutableListOf<Meeting>()
                    
                    val currentTime = Calendar.getInstance().time
                    val nearEndOfDayCutoff = isNearEndOfDayCutoff(currentTime)
                    
                    for (document in result) {
                        try {
                            val meeting = document.toObject(Meeting::class.java)
                            
                            // Check if this meeting should be auto-ended
                            var shouldEnd = shouldAutoEndMeeting(meeting)
                            
                            // If we're near the end-of-day cutoff, be more aggressive about ending meetings
                            // that started today
                            var shouldEndAggressively = false
                            if (nearEndOfDayCutoff) {
                                val today = Calendar.getInstance()
                                today.set(Calendar.HOUR_OF_DAY, 0)
                                today.set(Calendar.MINUTE, 0)
                                today.set(Calendar.SECOND, 0)
                                today.set(Calendar.MILLISECOND, 0)
                                
                                val meetingDate = Calendar.getInstance()
                                meetingDate.time = meeting.dateTime.toDate()
                                meetingDate.set(Calendar.HOUR_OF_DAY, 0)
                                meetingDate.set(Calendar.MINUTE, 0)
                                meetingDate.set(Calendar.SECOND, 0)
                                meetingDate.set(Calendar.MILLISECOND, 0)
                                
                                // If the meeting started today and we're near cutoff, end it
                                if (meetingDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                                    meetingDate.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                                    shouldEndAggressively = true
                                }
                            }
                            
                            if (shouldEnd || shouldEndAggressively) {
                                Log.d("AutoEndMeeting", "Ending meeting ${document.id} - shouldEnd: $shouldEnd, shouldEndAggressively: $shouldEndAggressively")
                                // Set end time to now
                                val endTime = Timestamp.now()
                                batch.update(document.reference, "endTime", endTime)
                                batch.update(document.reference, "status", "Completed")
                                meetingsToEnd++
                                endedMeetings.add(meeting)
                            }
                        } catch (e: Exception) {
                            Log.e("AutoEndMeeting", "Error processing meeting ${document.id}", e)
                        }
                    }
                    
                    // Commit all updates
                    if (meetingsToEnd > 0) {
                        batch.commit()
                            .addOnSuccessListener {
                                Log.d("AutoEndMeeting", "Auto-ended $meetingsToEnd meetings")
                                // Show notifications for ended meetings, handling permission errors
                                // Only attempt to show notifications if we have permission
                                if (hasNotificationPermission(context)) {
                                    for (meeting in endedMeetings) {
                                        try {
                                            showNotification(context, meeting)
                                        } catch (e: SecurityException) {
                                            Log.w("AutoEndMeeting", "Notification permission denied for meeting ${meeting.id}", e)
                                        } catch (e: Exception) {
                                            Log.e("AutoEndMeeting", "Error showing notification for meeting ${meeting.id}", e)
                                        }
                                    }
                                } else {
                                    Log.d("AutoEndMeeting", "Notification permission denied, skipping notifications for $meetingsToEnd ended meetings")
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("AutoEndMeeting", "Error committing batch update", exception)
                            }
                    } else {
                        Log.d("AutoEndMeeting", "No meetings needed to be ended")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("AutoEndMeeting", "Error checking for meetings to auto-end", exception)
                }
        } catch (e: Exception) {
            Log.e("AutoEndMeeting", "Error in autoEndMeetings", e)
        }
    }
    
    private fun hasNotificationPermission(context: Context): Boolean {
        // Check for notification permission before showing notification
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            // For older Android versions, we assume we have permission
            true
        }
    }
    
    private fun showNotification(context: Context, meeting: Meeting) {
        // Double-check permission before showing notification
        if (!hasNotificationPermission(context)) {
            // Cannot show notification without permission, silently return
            Log.w("AutoEndMeeting", "Notification permission not granted for meeting ${meeting.id}")
            return
        }
        
        // Create notification channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "meetings_channel"
            val name = "Meeting Notifications"
            val desc = "Notifications for meetings, reminders, cancellations, and reschedules"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(channelId, name, importance).apply {
                description = desc
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        
        val title = "Meeting Ended Automatically"
        val body = "The meeting '${meeting.title}' has been automatically ended."
        
        val builder = NotificationCompat.Builder(context, "meetings_channel")
            .setSmallIcon(com.bmsit.faculty.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(meeting.id.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            // Handle the case where permission is denied at runtime
            // This can happen even if we checked above, as permissions can be revoked
            Log.w("AutoEndMeeting", "Notification permission denied at runtime for meeting ${meeting.id}", e)
        }
    }
    
    private fun shouldAutoEndMeeting(meeting: Meeting): Boolean {
        val startTime = meeting.dateTime.toDate()
        val currentTime = Calendar.getInstance().time
        
        // Calculate duration
        val durationMillis = currentTime.time - startTime.time
        
        // End if meeting has been going on for more than 6 hours (6 * 60 * 60 * 1000 milliseconds)
        if (durationMillis > 6 * 60 * 60 * 1000) {
            return true
        }
        
        // End if current time is past 9 PM
        val currentCalendar = Calendar.getInstance()
        currentCalendar.time = currentTime
        val currentHour = currentCalendar.get(Calendar.HOUR_OF_DAY)
        if (currentHour >= 21) { // 21:00 is 9 PM
            return true
        }
        
        return false
    }
    
    // Add a special function to check if we're close to the 9 PM cutoff
    private fun isNearEndOfDayCutoff(currentTime: java.util.Date): Boolean {
        val calendar = Calendar.getInstance()
        calendar.time = currentTime
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        
        // If we're between 8:45 PM and 9:00 PM, we should be more aggressive about ending meetings
        if (currentHour == 20 && currentMinute >= 45) { // 8:45 PM
            return true
        }
        
        // If we're past 9 PM
        if (currentHour >= 21) { // 9 PM
            return true
        }
        
        return false
    }
    
    // Clean up resources when receiver is destroyed
    fun cleanup() {
        executor.shutdown()
    }
}