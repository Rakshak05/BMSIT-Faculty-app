package com.bmsit.faculty

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.bmsit.faculty.Meeting
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

data class Attendee(
    val uid: String,
    val name: String,
    var isPresent: Boolean = true
)

class AttendanceActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var meetingTitleText: TextView
    private lateinit var meetingDateText: TextView
    private lateinit var endTimeText: TextView
    private lateinit var endTimeButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var saveButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    
    private lateinit var adapter: AttendanceAdapter
    private val attendees = mutableListOf<Attendee>()
    private lateinit var meetingId: String
    private lateinit var meetingTitle: String
    private var meetingStartTime: Date? = null
    private var selectedEndTime: Calendar? = null
    private var meetingDuration: Int = 60 // Default to 60 minutes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)
        
        db = FirebaseFirestore.getInstance()
        
        meetingId = intent.getStringExtra("MEETING_ID") ?: ""
        meetingTitle = intent.getStringExtra("MEETING_TITLE") ?: ""
        val attendeeUids = intent.getStringArrayListExtra("ATTENDEE_UIDS") ?: arrayListOf()
        
        // Initialize with default values
        meetingDuration = 60 // Default to 1 hour
        
        if (meetingId.isEmpty() || meetingTitle.isEmpty() || attendeeUids.isEmpty()) {
            Toast.makeText(this, "Invalid meeting data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        initViews()
        loadMeetingDetails()
        loadAttendees(attendeeUids)
    }
    
    private fun initViews() {
        meetingTitleText = findViewById(R.id.textViewMeetingTitle)
        meetingDateText = findViewById(R.id.textViewMeetingDate)
        endTimeText = findViewById(R.id.textViewEndTime)
        endTimeButton = findViewById(R.id.buttonSetEndTime)
        recyclerView = findViewById(R.id.recyclerViewAttendees)
        saveButton = findViewById(R.id.buttonSaveAttendance)
        cancelButton = findViewById(R.id.buttonCancel)
        
        meetingTitleText.text = meetingTitle
        
        endTimeButton.setOnClickListener {
            showEndTimePicker()
        }
        
        adapter = AttendanceAdapter(attendees)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        saveButton.setOnClickListener {
            saveAttendance()
        }
        
        cancelButton.setOnClickListener {
            finish()
        }
    }
    
    private fun loadMeetingDetails() {
        db.collection("meetings").document(meetingId).get()
            .addOnSuccessListener { document ->
                val meeting = document.toObject(Meeting::class.java)
                if (meeting != null) {
                    meetingStartTime = meeting.dateTime.toDate()
                    meetingDuration = if (meeting.duration > 0) meeting.duration else 60 // Ensure valid duration
                    meetingDateText.text = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(meetingStartTime ?: Date())
                    
                    // If meeting already has an end time, display it
                    meeting.endTime?.let { endTime -> 
                        selectedEndTime = Calendar.getInstance().apply { time = endTime.toDate() }
                        endTimeText.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(endTime.toDate())
                        // Enable the save button but indicate that attendance was already taken
                        saveButton.text = "Update Attendance"
                    }
                } else {
                    // Keep default values if meeting not found
                    meetingDateText.text = "Meeting information not available"
                }
            }
            .addOnFailureListener {
                meetingDateText.text = "Error loading meeting information"
                // Keep default values if failed to load
            }
    }
    
    private fun loadAttendees(attendeeUids: List<String>) {
        // Fetch user names for all attendees
        for (uid in attendeeUids) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    val user = document.toObject(User::class.java)
                    val name = user?.name ?: "Unknown User"
                    attendees.add(Attendee(uid, name, true))
                    adapter.notifyDataSetChanged()
                }
                .addOnFailureListener {
                    attendees.add(Attendee(uid, "Unknown User", true))
                    adapter.notifyDataSetChanged()
                }
        }
    }
    
    private fun showEndTimePicker() {
        val calendar = Calendar.getInstance()
        
        TimePickerDialog(this, { _, hour, minute ->
            // Create the selected time based on meeting start date but with selected hour/minute
            val selectedTime = Calendar.getInstance().apply {
                time = meetingStartTime ?: Date()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            // Ensure the selected end time is not in the future
            val currentTime = Calendar.getInstance()
            if (selectedTime.after(currentTime)) {
                Toast.makeText(this, "End time cannot be in the future.", Toast.LENGTH_SHORT).show()
                return@TimePickerDialog
            }
            
            // Set the selected end time
            selectedEndTime = selectedTime
            
            // Format and display the selected end time
            selectedEndTime?.time?.let { 
                endTimeText.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
            }
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }
    
    private fun saveAttendance() {
        // First check if we have all required data
        if (meetingStartTime == null) {
            Toast.makeText(this, "Error: Meeting information not loaded", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (meetingDuration <= 0) {
            Toast.makeText(this, "Error: Invalid meeting duration", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get current time
        val currentTime = Calendar.getInstance().time
        
        // Validate that the meeting has started
        if (meetingStartTime!!.after(currentTime)) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            Toast.makeText(this, 
                "Cannot mark attendance before the meeting starts.\n" +
                "Meeting starts at: ${dateFormat.format(meetingStartTime!!)}\n" +
                "Current time: ${timeFormat.format(currentTime)}\n" +
                "Please wait until after the meeting starts.", 
                Toast.LENGTH_LONG).show()
            return
        }
        
        // NEW: Prevent marking attendance for future meetings
        // Check if the meeting start time is in the future (beyond a reasonable buffer)
        val futureBuffer = Calendar.getInstance()
        futureBuffer.add(Calendar.MINUTE, 5) // 5-minute buffer for network delays/etc.
        
        if (meetingStartTime!!.after(futureBuffer.time)) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            Toast.makeText(this, 
                "Cannot mark attendance for future meetings.\n" +
                "Meeting starts at: ${dateFormat.format(meetingStartTime!!)}\n" +
                "Current time: ${timeFormat.format(currentTime)}\n" +
                "Please wait until the meeting starts.", 
                Toast.LENGTH_LONG).show()
            return
        }
        
        // Check if the meeting has already been ended (endTime is set in database)
        db.collection("meetings").document(meetingId).get()
            .addOnSuccessListener { document ->
                val meeting = document.toObject(Meeting::class.java)
                if (meeting != null && meeting.endTime != null) {
                    // Meeting has already been ended, allow attendance
                    proceedWithAttendanceSave()
                } else {
                    // Meeting hasn't been ended yet, validate timing
                    validateMeetingTimingAndProceed()
                }
            }
            .addOnFailureListener {
                // If we can't check the database, show an error
                Toast.makeText(this, "Error checking meeting status. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun validateMeetingTimingAndProceed() {
        // Ensure we have all required data
        if (meetingStartTime == null) {
            Toast.makeText(this, "Error: Meeting information not loaded", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (meetingDuration <= 0) {
            Toast.makeText(this, "Error: Invalid meeting duration", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get current time
        val currentTime = Calendar.getInstance().time
        
        // Validate that the meeting has started
        if (meetingStartTime!!.after(currentTime)) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            Toast.makeText(this, 
                "Cannot mark attendance before the meeting starts.\n" +
                "Meeting starts at: ${dateFormat.format(meetingStartTime!!)}\n" +
                "Current time: ${timeFormat.format(currentTime)}\n" +
                "Please wait until after the meeting starts.", 
                Toast.LENGTH_LONG).show()
            return
        }
        
        // Calculate when the meeting should naturally end
        val expectedEndTime = Calendar.getInstance().apply {
            time = meetingStartTime!!
            add(Calendar.MINUTE, meetingDuration)
        }.time
        
        // Validate that the meeting has ended naturally
        if (currentTime.before(expectedEndTime)) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            Toast.makeText(this, 
                "Cannot mark attendance before the meeting ends.\n" +
                "Meeting started: ${dateFormat.format(meetingStartTime!!)}\n" +
                "Expected end: ${timeFormat.format(expectedEndTime)}\n" +
                "Current time: ${timeFormat.format(currentTime)}\n" +
                "Please wait until after ${timeFormat.format(expectedEndTime)}\n\n" +
                "If you want to end the meeting early, use the 'End Meeting' button on the dashboard first.", 
                Toast.LENGTH_LONG).show()
            return
        }
        
        // If we get here, it's OK to mark attendance based on natural end time
        proceedWithAttendanceSave()
    }
    
    private fun proceedWithAttendanceSave() {
        // If we get here, it's OK to mark attendance
        // But first validate that an end time was selected
        if (selectedEndTime == null) {
            Toast.makeText(this, "Please select the end time of the meeting", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get current time
        val currentTime = Calendar.getInstance().time
        
        // Validate that selected end time is not in the future
        if (selectedEndTime?.time?.after(currentTime) == true) {
            Toast.makeText(this, "Meeting end time cannot be in the future", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate that end time is after start time
        if (selectedEndTime?.time?.before(meetingStartTime) == true) {
            Toast.makeText(this, "End time must be after the start time", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save the attendance
        selectedEndTime?.time?.let { endTime ->
            val updates = hashMapOf<String, Any>(
                "endTime" to Timestamp(endTime),
                "attendanceTakenAt" to Timestamp.now()
            )
            
            db.collection("meetings").document(meetingId).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Attendance marked successfully!", Toast.LENGTH_SHORT).show()
                    // Update user statistics after attendance is marked
                    updateUserStatistics()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error marking attendance. Please try again.", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    /**
     * Update user statistics after attendance is marked
     */
    private fun updateUserStatistics() {
        try {
            // Get the meeting document to get attendee information
            db.collection("meetings").document(meetingId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val meeting = document.toObject(Meeting::class.java)
                        if (meeting != null) {
                            // Update statistics for all attendees
                            updateAttendeeStatistics(meeting)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("AttendanceActivity", "Error fetching meeting for statistics update", exception)
                }
        } catch (e: Exception) {
            Log.e("AttendanceActivity", "Error in updateUserStatistics", e)
        }
    }
    
    /**
     * Update statistics for all attendees of the meeting
     */
    private fun updateAttendeeStatistics(meeting: Meeting) {
        try {
            // Get list of all attendees
            val attendeeUids = when (meeting.attendees) {
                "Custom" -> meeting.customAttendeeUids
                // For group meetings, we would need to fetch all users in that group
                // For now, we'll just handle custom meetings
                else -> {
                    Log.d("AttendanceActivity", "Skipping statistics update for non-custom meeting: ${meeting.attendees}")
                    return
                }
            }
            
            // Also include the meeting scheduler in the attendee list
            val allAttendeeUids = attendeeUids.toMutableList()
            if (!allAttendeeUids.contains(meeting.scheduledBy)) {
                allAttendeeUids.add(meeting.scheduledBy)
            }
            
            // Update statistics for each attendee
            for (uid in allAttendeeUids) {
                updateUserMeetingStats(uid, meeting)
            }
        } catch (e: Exception) {
            Log.e("AttendanceActivity", "Error in updateAttendeeStatistics", e)
        }
    }
    
    /**
     * Update meeting statistics for a specific user
     */
    private fun updateUserMeetingStats(userId: String, meeting: Meeting) {
        try {
            // Note: In a production app, you might want to store these statistics directly in the user document
            // For now, we're just logging that the update would happen
            // The ProfileFragment already calculates these stats dynamically by querying meetings
            Log.d("AttendanceActivity", "Would update statistics for user: $userId")
            
            // In a real implementation, you might do something like:
            /*
            val userRef = db.collection("users").document(userId)
            userRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Update the user's meeting statistics
                        // This would require adding fields to the User model
                    }
                }
            */
        } catch (e: Exception) {
            Log.e("AttendanceActivity", "Error updating stats for user: $userId", e)
        }
    }

}
