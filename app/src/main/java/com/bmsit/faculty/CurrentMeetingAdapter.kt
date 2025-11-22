package com.bmsit.faculty

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
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
    // Track expanded positions
    private val expandedPositions = mutableSetOf<Int>()

    class CurrentMeetingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById<TextView>(R.id.textViewMeetingTitle)
        val timeText: TextView = view.findViewById<TextView>(R.id.textViewMeetingTime)
        val dateTimeText: TextView = view.findViewById<TextView>(R.id.textViewMeetingDateTime)
        val locationText: TextView = view.findViewById<TextView>(R.id.textViewMeetingLocation)
        val attendeesText: TextView = view.findViewById<TextView>(R.id.textViewMeetingAttendees)
        val hostText: TextView = view.findViewById<TextView>(R.id.textViewMeetingHost)
        val endButton: Button = view.findViewById<Button>(R.id.buttonEndMeeting)
        val takeAttendanceButton: Button = view.findViewById<Button>(R.id.buttonTakeAttendance)
        // Add references to the expandable views
        val expandedContent: View = view.findViewById<View>(R.id.expandedContent)
        
        // Attendance UI elements
        val attendanceLayout: LinearLayout = view.findViewById<LinearLayout>(R.id.attendanceLayout)
        val endTimeText: TextView = view.findViewById<TextView>(R.id.textViewEndTime)
        val setEndTimeButton: Button = view.findViewById<Button>(R.id.buttonSetEndTime)
        val attendeesRecyclerView: RecyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewAttendees)
        val saveAttendanceButton: Button = view.findViewById<Button>(R.id.buttonSaveAttendance)
        val cancelAttendanceButton: Button = view.findViewById<Button>(R.id.buttonCancelAttendance)
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

        // Show end meeting button and take attendance button only for the host
        if (meeting.scheduledBy == currentUserId) {
            // Show end meeting button for host
            holder.endButton.visibility = View.VISIBLE
            holder.endButton.setOnClickListener {
                Log.d(TAG, "End meeting button clicked for meeting: ${meeting.id}")
                Log.d(TAG, "Meeting scheduled by: ${meeting.scheduledBy}")
                Log.d(TAG, "Current user ID: $currentUserId")
                
                if (meeting.scheduledBy == currentUserId) {
                    showEndMeetingConfirmation(meeting, holder.adapterPosition)
                } else {
                    Toast.makeText(context, "You are not the host of this meeting", Toast.LENGTH_SHORT).show()
                    Log.w(TAG, "User tried to end meeting they don't host: ${meeting.id}")
                }
                // Prevent the click from propagating to the parent item view
                it.cancelPendingInputEvents()
            }
            
            // Show take attendance button for host
            holder.takeAttendanceButton.visibility = View.VISIBLE
            holder.takeAttendanceButton.setOnClickListener {
                showAttendanceUI(holder, meeting)
                // Prevent the click from propagating to the parent item view
                it.cancelPendingInputEvents()
            }
        } else {
            holder.endButton.visibility = View.GONE
            holder.takeAttendanceButton.visibility = View.GONE
        }
    }

    private fun showAttendanceUI(holder: CurrentMeetingViewHolder, meeting: CurrentMeeting) {
        // Hide the take attendance button and show the attendance layout
        holder.takeAttendanceButton.visibility = View.GONE
        holder.attendanceLayout.visibility = View.VISIBLE
        
        // Set up the attendance UI
        setupAttendanceUI(holder, meeting)
    }

    private fun setupAttendanceUI(holder: CurrentMeetingViewHolder, meeting: CurrentMeeting) {
        // Set up end time button
        holder.setEndTimeButton.setOnClickListener {
            showEndTimePicker(holder, meeting)
        }
        
        // Set up cancel button
        holder.cancelAttendanceButton.setOnClickListener {
            // Hide attendance layout and show take attendance button
            holder.attendanceLayout.visibility = View.GONE
            holder.takeAttendanceButton.visibility = View.VISIBLE
        }
        
        // Set up save button
        holder.saveAttendanceButton.setOnClickListener {
            saveAttendanceFromCard(holder, meeting)
        }
        
        // Load attendees
        loadAttendeesForMeeting(holder, meeting)
    }

    private fun showEndTimePicker(holder: CurrentMeetingViewHolder, meeting: CurrentMeeting) {
        // TODO: Implement end time picker
        Toast.makeText(context, "End time picker would appear here", Toast.LENGTH_SHORT).show()
    }

    private fun loadAttendeesForMeeting(holder: CurrentMeetingViewHolder, meeting: CurrentMeeting) {
        // TODO: Implement attendee loading
        Toast.makeText(context, "Loading attendees...", Toast.LENGTH_SHORT).show()
    }

    private fun saveAttendanceFromCard(holder: CurrentMeetingViewHolder, meeting: CurrentMeeting) {
        // TODO: Implement attendance saving
        Toast.makeText(context, "Saving attendance...", Toast.LENGTH_SHORT).show()
    }

    private fun showEndMeetingConfirmation(meeting: CurrentMeeting, position: Int) {
        Log.d(TAG, "Showing end meeting confirmation for meeting: ${meeting.id}")
        AlertDialog.Builder(context)
            .setTitle("End Meeting")
            .setMessage("Do you want to end this meeting early?")
            .setPositiveButton("End Early") { _, _ -> 
                Log.d(TAG, "User confirmed to end meeting: ${meeting.id}")
                endMeeting(meeting, position)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d(TAG, "User cancelled ending meeting: ${meeting.id}")
            }
            .show()
    }

    private fun endMeeting(meeting: CurrentMeeting, position: Int) {
        Log.d(TAG, "Ending meeting: ${meeting.id} at position: $position")
        // Directly end the meeting immediately without extra confirmation
        endMeetingImmediately(meeting)
    }
    
    private fun endMeetingImmediately(meeting: CurrentMeeting) {
        try {
            // Validate that we have a valid meeting ID
            if (meeting.id.isBlank()) {
                Log.e(TAG, "Invalid meeting ID: '${meeting.id}'")
                Toast.makeText(context, "Invalid meeting ID", Toast.LENGTH_LONG).show()
                return
            }
            
            val meetingRef = db.collection("meetings").document(meeting.id)
            
            Log.d(TAG, "Attempting to end meeting: ${meeting.id}")
            
            // First check if the document exists
            meetingRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        Log.d(TAG, "Meeting document found: ${meeting.id}")
                        val endTime = Timestamp.now()
                        Log.d(TAG, "End time: $endTime")
                        
                        meetingRef.update("endTime", endTime)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Meeting ended successfully", Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "Meeting ended successfully: ${meeting.id}")
                                
                                // Prompt host to take attendance immediately
                                showAttendancePrompt(meeting)
                                
                                // Refresh the data to reflect the changes
                                // This will update the UI to show that the meeting has ended
                                // You might want to notify the adapter or refresh the data source
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error updating meeting: ${meeting.id}", e)
                                Toast.makeText(context, "Error updating meeting: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        Log.e(TAG, "Meeting document not found: ${meeting.id}")
                        Toast.makeText(context, "Meeting not found in database", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking meeting existence: ${meeting.id}", e)
                    Toast.makeText(context, "Error checking meeting: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while ending meeting: ${meeting.id}", e)
            Toast.makeText(context, "Exception ending meeting: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAttendancePrompt(meeting: CurrentMeeting) {
        AlertDialog.Builder(context)
            .setTitle("Take Attendance")
            .setMessage("Would you like to take attendance for this meeting now?")
            .setPositiveButton("Take Attendance") { _, _ ->
                showAttendanceDialog(meeting)
            }
            .setNegativeButton("Later") { _, _ ->
                // User chose to take attendance later, do nothing
                Toast.makeText(context, "You can take attendance later from the calendar", Toast.LENGTH_SHORT).show()
            }
            .show()
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
                            "All Associate Prof" -> {
                                fetchAndShowAttendance(meeting, listOf("Associate Professor"))
                            }
                            "All Assistant Prof" -> {
                                fetchAndShowAttendance(meeting, listOf("Assistant Professor"))
                            }
                            "All Faculty" -> {
                                fetchAndShowAttendance(meeting, listOf("Faculty", "Assistant Professor", "Associate Professor", "Lab Assistant", "HOD", "ADMIN", "Unassigned"))
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