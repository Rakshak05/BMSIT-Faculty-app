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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)
        
        db = FirebaseFirestore.getInstance()
        
        meetingId = intent.getStringExtra("MEETING_ID") ?: ""
        meetingTitle = intent.getStringExtra("MEETING_TITLE") ?: ""
        val attendeeUids = intent.getStringArrayListExtra("ATTENDEE_UIDS") ?: arrayListOf()
        
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
                    meetingDateText.text = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(meetingStartTime ?: Date())
                    
                    // If meeting already has an end time, display it
                    meeting.endTime?.let { endTime ->
                        selectedEndTime = Calendar.getInstance().apply { time = endTime.toDate() }
                        endTimeText.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(endTime.toDate())
                    }
                }
            }
            .addOnFailureListener {
                meetingDateText.text = "Meeting date not available"
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
            selectedEndTime = Calendar.getInstance().apply {
                time = meetingStartTime ?: Date()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            
            // Format and display the selected end time
            selectedEndTime?.time?.let { 
                endTimeText.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
            }
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }
    
    private fun saveAttendance() {
        if (selectedEndTime == null) {
            Toast.makeText(this, "Please select the end time of the meeting", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate that end time is after start time
        if (meetingStartTime != null && selectedEndTime?.time?.before(meetingStartTime) == true) {
            Toast.makeText(this, "End time must be after the start time", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Prepare data to save
        selectedEndTime?.time?.let {
            val updates = hashMapOf<String, Any>(
                "endTime" to Timestamp(it),
                "attendanceTakenAt" to Timestamp.now() // Add timestamp when attendance was taken
            )
            
            // In a real implementation, you would also save the attendance data
            // For now, we'll just save the end time and attendance timestamp
            
            db.collection("meetings").document(meetingId).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Meeting end time saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error saving meeting end time. Please try again.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}