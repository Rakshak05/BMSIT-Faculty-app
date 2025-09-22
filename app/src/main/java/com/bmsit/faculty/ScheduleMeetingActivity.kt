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
import java.util.*

class ScheduleMeetingActivity : AppCompatActivity() {

    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var titleEditText: EditText
    private lateinit var locationEditText: EditText
    private lateinit var attendeesSpinner: Spinner

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
        val saveButton = findViewById<Button>(R.id.buttonSaveMeeting)

        val attendeeOptions = arrayOf("All Faculty", "All HODs", "Custom")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, attendeeOptions)
        attendeesSpinner.adapter = adapter

        // Listen for when an item is selected in the dropdown
        attendeesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (attendeeOptions[position] == "Custom") {
                    // If "Custom" is selected, launch our new screen
                    val intent = Intent(this@ScheduleMeetingActivity, SelectAttendeesActivity::class.java)
                    selectAttendeesLauncher.launch(intent)
                } else {
                    // If user selects something else (e.g., "All Faculty"), clear the custom list
                    customAttendeeUids = emptyList()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
        if (selectedDay == -1 || selectedHour == -1) {
            Toast.makeText(this, "Please select a date and time", Toast.LENGTH_SHORT).show(); return
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
        calendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
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
            customAttendeeUids = customAttendeeUids
        )

        db.collection("meetings").document(meetingId).set(newMeeting)
            .addOnSuccessListener {
                Toast.makeText(this, "Meeting scheduled successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving meeting. Please try again.", Toast.LENGTH_SHORT).show()
                Log.w("ScheduleMeeting", "Error writing document", e)
            }
    }

    @SuppressLint("DefaultLocale", "SetTextI18n")
    private fun setupPickers() {
        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                dateButton.text = "$day/${month + 1}/$year"
                selectedYear = year; selectedMonth = month; selectedDay = day
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        timeButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                timeButton.text = String.format("%02d:%02d", hour, minute)
                selectedHour = hour; selectedMinute = minute
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }
    }
}

