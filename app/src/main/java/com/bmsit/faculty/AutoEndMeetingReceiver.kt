package com.bmsit.faculty

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
                
                if (currentUser != null) {
                    autoEndMeetings(db, currentUser.uid)
                }
            } catch (e: Exception) {
                Log.e("AutoEndMeeting", "Error in onReceive", e)
            }
        }
    }
    
    private fun autoEndMeetings(db: FirebaseFirestore, userId: String) {
        try {
            // Get all active meetings where the current user is the host
            db.collection("meetings")
                .whereEqualTo("scheduledBy", userId)
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener { result ->
                    val batch = db.batch()
                    var meetingsToEnd = 0
                    
                    for (document in result) {
                        try {
                            val meeting = document.toObject(Meeting::class.java)
                            
                            // Check if this meeting should be auto-ended
                            if (shouldAutoEndMeeting(meeting)) {
                                // Set end time to now
                                val endTime = Timestamp.now()
                                batch.update(document.reference, "endTime", endTime)
                                batch.update(document.reference, "status", "Completed")
                                meetingsToEnd++
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
                            }
                            .addOnFailureListener { exception ->
                                Log.e("AutoEndMeeting", "Error committing batch update", exception)
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("AutoEndMeeting", "Error checking for meetings to auto-end", exception)
                }
        } catch (e: Exception) {
            Log.e("AutoEndMeeting", "Error in autoEndMeetings", e)
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
    
    // Clean up resources when receiver is destroyed
    fun cleanup() {
        executor.shutdown()
    }
}