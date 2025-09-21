package com.bmsit.faculty

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ScheduleMeetingActivity : AppCompatActivity() {

    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var titleEditText: EditText
    private lateinit var locationEditText: EditText
    private lateinit var attendeesSpinner: Spinner

    // --- NEW: Add Firebase instances ---
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Variables to store the selected date and time
    private var selectedYear = -1
    private var selectedMonth = -1
    private var selectedDay = -1
    private var selectedHour = -1
    private var selectedMinute = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_meeting)

        // --- NEW: Initialize Firebase ---
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        dateButton = findViewById(R.id.buttonPickDate)
        timeButton = findViewById(R.id.buttonPickTime)
        titleEditText = findViewById(R.id.editTextMeetingTitle)
        locationEditText = findViewById(R.id.editTextMeetingLocation)
        attendeesSpinner = findViewById(R.id.spinnerAttendees)
        val saveButton = findViewById<Button>(R.id.buttonSaveMeeting)

        val attendeeOptions = arrayOf("All Faculty", "All HODs", "Custom")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, attendeeOptions)
        attendeesSpinner.adapter = adapter

        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, monthOfYear, dayOfMonth ->
                dateButton.text = "$dayOfMonth/${monthOfYear + 1}/$year"
                selectedYear = year
                selectedMonth = monthOfYear
                selectedDay = dayOfMonth
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        timeButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(this, { _, hourOfDay, minute ->
                timeButton.text = String.format("%02d:%02d", hourOfDay, minute)
                selectedHour = hourOfDay
                selectedMinute = minute
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        // --- NEW: Updated Save Button Logic ---
        saveButton.setOnClickListener {
            saveMeetingToFirestore()
        }
    }

    // --- This is a brand new function ---
    private fun saveMeetingToFirestore() {
        val title = titleEditText.text.toString().trim()
        val location = locationEditText.text.toString().trim()
        val attendees = attendeesSpinner.selectedItem.toString()
        val schedulerId = auth.currentUser?.uid

        // --- Input Validation ---
        if (title.isEmpty()) {
            titleEditText.error = "Title is required"
            return
        }
        if (selectedDay == -1 || selectedHour == -1) {
            Toast.makeText(this, "Please select a date and time", Toast.LENGTH_SHORT).show()
            return
        }
        if (schedulerId == null) {
            Toast.makeText(this, "Error: You must be logged in to schedule a meeting.", Toast.LENGTH_LONG).show()
            return
        }

        // --- Create a Calendar object and convert to Firestore Timestamp ---
        val calendar = Calendar.getInstance()
        calendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
        val meetingTimestamp = Timestamp(calendar.time)

        // --- Create a new meeting object ---
        val meetingId = db.collection("meetings").document().id // Generate a unique ID
        val newMeeting = Meeting(
            id = meetingId,
            title = title,
            location = location,
            dateTime = meetingTimestamp,
            attendees = attendees,
            scheduledBy = schedulerId
        )

        // --- Save to Firestore ---
        db.collection("meetings").document(meetingId).set(newMeeting)
            .addOnSuccessListener {
                Toast.makeText(this, "Meeting scheduled successfully!", Toast.LENGTH_SHORT).show()
                Log.d("Firestore", "Meeting saved with ID: $meetingId")
                finish() // Close the screen and go back to the dashboard
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving meeting. Please try again.", Toast.LENGTH_SHORT).show()
                Log.w("Firestore", "Error writing document", e)
            }
    }
}
