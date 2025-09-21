package com.bmsit.faculty

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class EditMeetingActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private var meetingId: String? = null

    private lateinit var titleEditText: EditText
    private lateinit var locationEditText: EditText
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var attendeesSpinner: Spinner

    private var selectedYear = 0
    private var selectedMonth = 0
    private var selectedDay = 0
    private var selectedHour = 0
    private var selectedMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_meeting)

        db = FirebaseFirestore.getInstance()
        meetingId = intent.getStringExtra("MEETING_ID")

        titleEditText = findViewById(R.id.editTextMeetingTitle)
        locationEditText = findViewById(R.id.editTextMeetingLocation)
        dateButton = findViewById(R.id.buttonPickDate)
        timeButton = findViewById(R.id.buttonPickTime)
        attendeesSpinner = findViewById(R.id.spinnerAttendees)
        val updateButton = findViewById<Button>(R.id.buttonUpdateMeeting)

        // Set up the spinner (same as before)
        val attendeeOptions = arrayOf("All Faculty", "All HODs", "Custom")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, attendeeOptions)
        attendeesSpinner.adapter = adapter

        // Fetch and display existing meeting data
        if (meetingId != null) {
            loadMeetingData(meetingId!!)
        } else {
            Toast.makeText(this, "Error: No meeting ID provided.", Toast.LENGTH_LONG).show()
            finish()
        }

        // Set up date and time pickers (same as before)
        setupPickers()

        updateButton.setOnClickListener {
            updateMeetingInFirestore()
        }
    }

    private fun loadMeetingData(id: String) {
        db.collection("meetings").document(id).get()
            .addOnSuccessListener { document ->
                val meeting = document.toObject(Meeting::class.java)
                if (meeting != null) {
                    titleEditText.setText(meeting.title)
                    locationEditText.setText(meeting.location)

                    // Pre-fill date and time
                    val calendar = Calendar.getInstance()
                    calendar.time = meeting.dateTime.toDate()
                    selectedYear = calendar.get(Calendar.YEAR)
                    selectedMonth = calendar.get(Calendar.MONTH)
                    selectedDay = calendar.get(Calendar.DAY_OF_MONTH)
                    selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
                    selectedMinute = calendar.get(Calendar.MINUTE)

                    dateButton.text = SimpleDateFormat("d/M/yyyy", Locale.getDefault()).format(calendar.time)
                    timeButton.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)

                    // Pre-select the correct attendee group
                    val attendeePosition = (attendeesSpinner.adapter as ArrayAdapter<String>).getPosition(meeting.attendees)
                    attendeesSpinner.setSelection(attendeePosition)
                }
            }
    }

    private fun setupPickers() {
        dateButton.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                selectedYear = year
                selectedMonth = month
                selectedDay = day
                dateButton.text = "$day/${month + 1}/$year"
            }, selectedYear, selectedMonth, selectedDay).show()
        }

        timeButton.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                timeButton.text = String.format("%02d:%02d", hour, minute)
            }, selectedHour, selectedMinute, true).show()
        }
    }

    private fun updateMeetingInFirestore() {
        val updatedTitle = titleEditText.text.toString().trim()
        val updatedLocation = locationEditText.text.toString().trim()
        val updatedAttendees = attendeesSpinner.selectedItem.toString()

        if (updatedTitle.isEmpty()) {
            titleEditText.error = "Title is required"
            return
        }

        val calendar = Calendar.getInstance()
        calendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
        val updatedTimestamp = Timestamp(calendar.time)

        // Create a map of the fields to update
        val updates = mapOf(
            "title" to updatedTitle,
            "location" to updatedLocation,
            "attendees" to updatedAttendees,
            "dateTime" to updatedTimestamp
        )

        // Update the document in Firestore
        db.collection("meetings").document(meetingId!!).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Meeting updated successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating meeting.", Toast.LENGTH_SHORT).show()
            }
    }
}
