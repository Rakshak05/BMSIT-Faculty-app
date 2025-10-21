package com.bmsit.faculty

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ScheduleMeetingActivity : AppCompatActivity() {

    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var titleEditText: EditText
    
    private lateinit var locationEditText: EditText
    private lateinit var attendeesSpinner: Spinner
    private lateinit var selectAttendeesButton: Button

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var selectedYear = -1
    private var selectedMonth = -1
    private var selectedDay = -1
    private var selectedHour = -1
    private var selectedMinute = -1

    // A variable to hold the list of selected custom attendees
    private var customAttendeeUids = listOf<String>()


    // This is the modern way to handle getting a result back from another activity.
    // It listens for the result from SelectAttendeesActivity.
    private val selectAttendeesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // If the user clicked "Done", get the list of UIDs
            customAttendeeUids = result.data?.getStringArrayListExtra("SELECTED_UIDS") ?: emptyList()
            Toast.makeText(this, "${customAttendeeUids.size} attendees selected.", Toast.LENGTH_SHORT).show()
        } else {
            // If the user pressed "Back" without clicking "Done", reset the spinner to the first option
            attendeesSpinner.setSelection(0)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_meeting)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        dateButton = findViewById(R.id.buttonPickDate)
        timeButton = findViewById(R.id.buttonPickTime)
        titleEditText = findViewById(R.id.editTextMeetingTitle)
        locationEditText = findViewById(R.id.editTextMeetingLocation)
        attendeesSpinner = findViewById(R.id.spinnerAttendees)
        selectAttendeesButton = findViewById(R.id.buttonSelectAttendees)
        val saveButton = findViewById<Button>(R.id.buttonSaveMeeting)

        val attendeeOptions = arrayOf("All Faculty", "All Deans", "All HODs", "Custom")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_large, attendeeOptions)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_large)
        attendeesSpinner.adapter = adapter

        // Listen for when an item is selected in the dropdown
        attendeesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isCustom = attendeeOptions[position] == "Custom"
                selectAttendeesButton.visibility = if (isCustom) View.VISIBLE else View.GONE
                if (!isCustom) customAttendeeUids = emptyList()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        selectAttendeesButton.setOnClickListener {
            val intent = Intent(this@ScheduleMeetingActivity, SelectAttendeesActivity::class.java)
            selectAttendeesLauncher.launch(intent)
        }

        setupPickers()

        saveButton.setOnClickListener {
            saveMeetingToFirestore()
        }
    }

    private fun saveMeetingToFirestore() {
        val title = titleEditText.text.toString().trim()
        val location = locationEditText.text.toString().trim()
        val attendees = attendeesSpinner.selectedItem.toString()
        val schedulerId = auth.currentUser?.uid

        if (title.isEmpty()) {
            titleEditText.error = "Title is required"; return
        }
        if (selectedDay == -1 || selectedMonth == -1 || selectedYear == -1) {
            Toast.makeText(this, "Please select a valid date", Toast.LENGTH_SHORT).show(); return
        }
        if (selectedHour == -1 || selectedMinute == -1) {
            Toast.makeText(this, "Please select a valid time", Toast.LENGTH_SHORT).show(); return
        }
        if (schedulerId == null) {
            Toast.makeText(this, "Error: You must be logged in.", Toast.LENGTH_LONG).show(); return
        }

        // Validation for Custom meetings
        if (attendees == "Custom" && customAttendeeUids.isEmpty()) {
            Toast.makeText(this, "Please select at least one custom attendee.", Toast.LENGTH_SHORT).show()
            return
        }

        val calendar = Calendar.getInstance()
        calendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val meetingTimestamp = Timestamp(calendar.time)

        val meetingId = db.collection("meetings").document().id
        val newMeeting = Meeting(
            id = meetingId,
            title = title,
            location = location,
            dateTime = meetingTimestamp,
            attendees = attendees,
            scheduledBy = schedulerId,
            // Save the list of custom attendees to the meeting document
            customAttendeeUids = customAttendeeUids,
            status = "Active"
        )

        db.collection("meetings").document(meetingId).set(newMeeting)
            .addOnSuccessListener {
                Toast.makeText(this, "Meeting scheduled successfully!", Toast.LENGTH_SHORT).show()
                // Fire-and-forget: detect collisions and notify impacted users
                detectAndNotifyCollisions(newMeeting)
                // Also notify attendees about the new meeting
                notifyAttendees(newMeeting)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving meeting. Please try again.", Toast.LENGTH_SHORT).show()
                Log.w("ScheduleMeeting", "Error writing document", e)
            }
    }

    private fun designationRank(designation: String?): Int {
        return when (designation?.uppercase()) {
            "ADMIN" -> 7
            "DEAN" -> 7
            "HOD" -> 5
            "ASSOCIATE PROFESSOR" -> 3
            "ASSISTANT PROFESSOR" -> 2
            "FACULTY" -> 2
            "LAB ASSISTANT" -> 1
            "OTHERS" -> 1
            else -> 0
        }
    }

    private fun detectAndNotifyCollisions(newMeeting: Meeting) {
        val scheduler = auth.currentUser ?: return
        // First, get current user's designation
        db.collection("users").document(scheduler.uid).get().addOnSuccessListener { userDoc ->
            val myDesignation = userDoc.getString("designation")
            val myRank = designationRank(myDesignation)

            // Define a +/-30 minutes window for collision (since we don't store duration)
            val cal = Calendar.getInstance()
            cal.time = newMeeting.dateTime.toDate()
            val endCal = cal.clone() as Calendar
            val startCal = cal.clone() as Calendar
            startCal.add(Calendar.MINUTE, -30)
            endCal.add(Calendar.MINUTE, 30)

            val startTs = com.google.firebase.Timestamp(startCal.time)
            val endTs = com.google.firebase.Timestamp(endCal.time)

            db.collection("meetings")
                .whereGreaterThanOrEqualTo("dateTime", startTs)
                .whereLessThanOrEqualTo("dateTime", endTs)
                .get()
                .addOnSuccessListener { result ->
                    for (doc in result) {
                        val other = doc.toObject(Meeting::class.java)
                        if (other.id == newMeeting.id) continue
                        if (other.status != "Active") continue
                        // Compare ranks: if our rank >= other's rank, notify the other scheduler
                        db.collection("users").document(other.scheduledBy).get()
                            .addOnSuccessListener { otherUserDoc ->
                                val otherDesignation = otherUserDoc.getString("designation")
                                val otherRank = designationRank(otherDesignation)
                                if (myRank >= otherRank) {
                                    val message = "Your meeting '${other.title}' conflicts with '${newMeeting.title}' scheduled by a higher/equal authority at the same time. Please reschedule."
                                    val notification = hashMapOf(
                                        "recipientUid" to other.scheduledBy,
                                        "message" to message,
                                        "createdAt" to com.google.firebase.Timestamp.now(),
                                        "read" to false
                                    )
                                    db.collection("notifications").add(notification)
                                }
                            }
                    }
                }
        }
    }

    private fun notifyAttendees(newMeeting: Meeting) {
        // Create a notification for all relevant attendees
        when (newMeeting.attendees) {
            "All Faculty" -> {
                // Notify all faculty members
                db.collection("users")
                    .whereNotEqualTo("designation", "ADMIN")
                    .get()
                    .addOnSuccessListener { result ->
                        for (doc in result) {
                            val userId = doc.id
                            if (userId != newMeeting.scheduledBy) { // Don't notify the scheduler
                                createNotificationForUser(userId, newMeeting)
                            }
                        }
                    }
            }
            "All Deans" -> {
                // Notify all deans
                db.collection("users")
                    .whereEqualTo("designation", "DEAN")
                    .get()
                    .addOnSuccessListener { result ->
                        for (doc in result) {
                            val userId = doc.id
                            if (userId != newMeeting.scheduledBy) { // Don't notify the scheduler
                                createNotificationForUser(userId, newMeeting)
                            }
                        }
                    }
            }
            "All HODs" -> {
                // Notify all HODs
                db.collection("users")
                    .whereEqualTo("designation", "HOD")
                    .get()
                    .addOnSuccessListener { result ->
                        for (doc in result) {
                            val userId = doc.id
                            if (userId != newMeeting.scheduledBy) { // Don't notify the scheduler
                                createNotificationForUser(userId, newMeeting)
                            }
                        }
                    }
            }
            "Custom" -> {
                // Notify custom attendees
                newMeeting.customAttendeeUids.forEach { userId ->
                    if (userId != newMeeting.scheduledBy) { // Don't notify the scheduler
                        createNotificationForUser(userId, newMeeting)
                    }
                }
            }
        }
    }

    private fun createNotificationForUser(userId: String, meeting: Meeting) {
        val notification = hashMapOf(
            "recipientUid" to userId,
            "message" to "You have a new meeting: ${meeting.title} on ${formatDate(meeting.dateTime.toDate())} at ${formatTime(meeting.dateTime.toDate())}",
            "createdAt" to com.google.firebase.Timestamp.now(),
            "read" to false,
            "type" to "meeting_created",
            "meetingId" to meeting.id
        )
        db.collection("notifications").add(notification)
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        return sdf.format(date)
    }

    private fun formatTime(date: Date): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(date)
    }

    @SuppressLint("DefaultLocale", "SetTextI18n")
    private fun setupPickers() {
        // Set today as the default date and next hour as default time to avoid past times
        val calendar = Calendar.getInstance()
        selectedYear = calendar.get(Calendar.YEAR)
        selectedMonth = calendar.get(Calendar.MONTH)
        selectedDay = calendar.get(Calendar.DAY_OF_MONTH)
        // Set to next hour, 0 minutes
        selectedHour = calendar.get(Calendar.HOUR_OF_DAY) + 1
        selectedMinute = 0
        // Handle hour overflow
        if (selectedHour >= 24) {
            selectedHour = 0
            // Move to next day if needed
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            selectedYear = calendar.get(Calendar.YEAR)
            selectedMonth = calendar.get(Calendar.MONTH)
            selectedDay = calendar.get(Calendar.DAY_OF_MONTH)
        }
        dateButton.text = "${selectedDay}/${selectedMonth + 1}/${selectedYear}"
        timeButton.text = String.format("%02d:%02d", selectedHour, selectedMinute)

        dateButton.setOnClickListener {
            val currentCalendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                dateButton.text = "$day/${month + 1}/$year"
                selectedYear = year; selectedMonth = month; selectedDay = day
            }, currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH), currentCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        timeButton.setOnClickListener {
            val timeCalendar = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                timeButton.text = String.format("%02d:%02d", hour, minute)
                selectedHour = hour; selectedMinute = minute
            }, timeCalendar.get(Calendar.HOUR_OF_DAY), timeCalendar.get(Calendar.MINUTE), true).show()
        }
    }
}

