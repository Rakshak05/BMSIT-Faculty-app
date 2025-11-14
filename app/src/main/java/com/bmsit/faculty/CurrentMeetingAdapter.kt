package com.bmsit.faculty

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bmsit.faculty.speech.SpeechService
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CurrentMeetingAdapter(
    private val context: Context,
    private val currentMeetings: List<CurrentMeeting>,
    private val userNamesMap: Map<String, String>,
    private val currentUserId: String
) : RecyclerView.Adapter<CurrentMeetingAdapter.CurrentMeetingViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
    private val speechService = SpeechService(context)
    // Track expanded positions
    private val expandedPositions = mutableSetOf<Int>()

    class CurrentMeetingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.textViewMeetingTitle)
        val timeText: TextView = view.findViewById(R.id.textViewMeetingTime)
        val dateTimeText: TextView = view.findViewById(R.id.textViewMeetingDateTime)
        val locationText: TextView = view.findViewById(R.id.textViewMeetingLocation)
        val attendeesText: TextView = view.findViewById(R.id.textViewMeetingAttendees)
        val hostText: TextView = view.findViewById(R.id.textViewMeetingHost)
        val recordButton: Button = view.findViewById(R.id.buttonRecordMeeting)
        val endButton: Button = view.findViewById(R.id.buttonEndMeeting)
        // Add references to the expandable views
        val expandedContent: View = view.findViewById(R.id.expandedContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurrentMeetingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_current_meeting, parent, false)
        return CurrentMeetingViewHolder(view)
    }

    override fun onBindViewHolder(holder: CurrentMeetingViewHolder, position: Int) {
        val meeting = currentMeetings[position]
        holder.titleText.text = meeting.title
        
        // Format time similar to upcoming meetings
        val meetingTime = meeting.dateTime
        holder.timeText.text = timeFormat.format(meetingTime)
        holder.dateTimeText.text = dateTimeFormat.format(meetingTime)
        
        holder.locationText.text = "Location: ${meeting.location}"
        holder.attendeesText.text = "For: ${meeting.attendees}"

        // Set host name
        val hostName = userNamesMap[meeting.scheduledBy] ?: "Unknown User"
        holder.hostText.text = "Hosted by: $hostName"

        // Handle expand/collapse
        val isExpanded = expandedPositions.contains(position)
        holder.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

        // Set click listener for expand/collapse on the entire item
        holder.itemView.setOnClickListener {
            if (expandedPositions.contains(position)) {
                expandedPositions.remove(position)
            } else {
                expandedPositions.add(position)
            }
            notifyItemChanged(position)
        }

        // Show record button and end meeting button only for the host
        if (meeting.scheduledBy == currentUserId) {
            holder.recordButton.visibility = View.VISIBLE
            if (meeting.isRecording) {
                holder.recordButton.text = "Stop Recording"
            } else {
                holder.recordButton.text = "Record Meeting"
            }
            
            holder.recordButton.setOnClickListener {
                toggleRecording(meeting, position, holder.recordButton)
            }
            
            // Show end meeting button for host
            holder.endButton.visibility = View.VISIBLE
            holder.endButton.setOnClickListener {
                showEndMeetingConfirmation(meeting, holder.adapterPosition)
            }
        } else {
            holder.recordButton.visibility = View.GONE
            holder.endButton.visibility = View.GONE
        }
    }

    private fun toggleRecording(meeting: CurrentMeeting, position: Int, recordButton: Button) {
        try {
            if (meeting.isRecording) {
                // Stop recording
                speechService.stopRecording()
                val updatedMeeting = meeting.copy(isRecording = false)
                // Update UI
                recordButton.text = "Record Meeting"
                // In a real implementation, you would save the final transcription here
                Toast.makeText(context, "Recording stopped and saved", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Stopped recording for meeting: ${meeting.id}")
            } else {
                // Start recording
                speechService.startRecording(meeting.id) { transcription ->
                    // Update the meeting with the latest transcription
                    // In a real implementation, you would update the UI with the transcription
                    Log.d(TAG, "Transcription update: $transcription")
                }
                val updatedMeeting = meeting.copy(isRecording = true)
                // Update UI
                recordButton.text = "Stop Recording"
                Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Started recording for meeting: ${meeting.id}")
            }
            
            // Update the meeting in the list
            // Note: In a real implementation, you would need to update the data source
            // and notify the adapter of the change
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling recording", e)
            Toast.makeText(context, "Error with recording: ${e.message}", Toast.LENGTH_LONG).show()
            // Show error message to user
        }
    }

    private fun showEndMeetingConfirmation(meeting: CurrentMeeting, position: Int) {
        AlertDialog.Builder(context)
            .setTitle("End Meeting")
            .setMessage("Are you sure you want to end this meeting? You will be prompted to take attendance.")
            .setPositiveButton("End Meeting") { _, _ ->
                endMeeting(meeting, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun endMeeting(meeting: CurrentMeeting, position: Int) {
        try {
            // Stop any ongoing recording
            if (speechService.isRecording()) {
                speechService.stopRecording()
            }
            
            // Update meeting with end time in Firestore
            val endTime = Timestamp.now()
            val meetingRef = db.collection("meetings").document(meeting.id)
            
            meetingRef.update("endTime", endTime)
                .addOnSuccessListener {
                    Toast.makeText(context, "Meeting ended successfully", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Meeting ended: ${meeting.id}")
                    
                    // Show attendance dialog
                    showAttendanceDialog(meeting)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error ending meeting", e)
                    Toast.makeText(context, "Error ending meeting: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error ending meeting", e)
            Toast.makeText(context, "Error ending meeting: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAttendanceDialog(meeting: CurrentMeeting) {
        // For custom meetings, fetch the attendee UIDs from the meeting document
        db.collection("meetings").document(meeting.id).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val meetingData = document.toObject(Meeting::class.java)
                    if (meetingData != null) {
                        when (meetingData.attendees) {
                            "Custom" -> {
                                showAttendanceActivity(meeting, meetingData.customAttendeeUids)
                            }
                            "All Faculty" -> {
                                fetchAndShowAttendance(meeting, listOf("Faculty", "Assistant Professor", "Associate Professor", "Lab Assistant", "HOD", "DEAN", "ADMIN"))
                            }
                            "All HODs" -> {
                                fetchAndShowAttendance(meeting, listOf("HOD", "ADMIN"))
                            }
                            "All Deans" -> {
                                fetchAndShowAttendance(meeting, listOf("DEAN", "ADMIN"))
                            }
                            else -> {
                                Toast.makeText(context, "Attendance can only be taken for custom meetings with specific attendees.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Error loading meeting data", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Meeting not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading meeting data", e)
                Toast.makeText(context, "Error loading meeting data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun fetchAndShowAttendance(meeting: CurrentMeeting, designations: List<String>) {
        // Show loading message
        Toast.makeText(context, "Fetching attendee list...", Toast.LENGTH_SHORT).show()
        
        // Fetch users with specified designations
        db.collection("users")
            .whereIn("designation", designations)
            .get()
            .addOnSuccessListener { result ->
                val attendeeUids = result.map { it.id }
                showAttendanceActivity(meeting, attendeeUids)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error fetching attendees: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showAttendanceActivity(meeting: CurrentMeeting, attendeeUids: List<String>) {
        if (attendeeUids.isEmpty()) {
            Toast.makeText(context, "No attendees found for this meeting.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show attendance dialog/activity
        val intent = Intent(context, AttendanceActivity::class.java).apply {
            putExtra("MEETING_ID", meeting.id)
            putExtra("MEETING_TITLE", meeting.title)
            putStringArrayListExtra("ATTENDEE_UIDS", ArrayList(attendeeUids))
        }
        context.startActivity(intent)
    }

    override fun getItemCount() = currentMeetings.size

    companion object {
        private const val TAG = "CurrentMeetingAdapter"
    }
}