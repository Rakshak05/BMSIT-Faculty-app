package com.bmsit.faculty

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private var isInitialSpinnerSetup = true
class EditMeetingActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var meetingId: String? = null
    private var originalMeeting: Meeting? = null

    private lateinit var titleEditText: AutoCompleteTextView
    private lateinit var locationEditText: AutoCompleteTextView
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var attendeesSpinner: Spinner
    private lateinit var buttonEditCustomAttendees: Button

    private var selectedYear = 0
    private var selectedMonth = 0
    private var selectedDay = 0
    private var selectedHour = 0
    private var selectedMinute = 0
    
    // For Custom attendees
    private val customAttendeeUids = mutableListOf<String>()
    
    // Data structures to store frequency of titles and locations
    private val titleFrequencyMap = mutableMapOf<String, Int>()
    private val locationFrequencyMap = mutableMapOf<String, Int>()
    
    // Lists to store sorted suggestions
    private val sortedTitles = mutableListOf<String>()
    private val sortedLocations = mutableListOf<String>()
    
    // Store the original listener
    private var originalSpinnerListener: android.widget.AdapterView.OnItemSelectedListener? = null
    private fun temporarilyResetSpinner() {
        // Remove listener temporarily
        attendeesSpinner.onItemSelectedListener = null

        // Reset spinner to ANY OTHER option (position 0 works)
        attendeesSpinner.setSelection(0, false)

        // Reattach listener
        attendeesSpinner.onItemSelectedListener = originalSpinnerListener
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_meeting)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        meetingId = intent.getStringExtra("MEETING_ID")

        titleEditText = findViewById(R.id.editTextMeetingTitle)
        locationEditText = findViewById(R.id.editTextMeetingLocation)
        dateButton = findViewById(R.id.buttonPickDate)
        timeButton = findViewById(R.id.buttonPickTime)
        attendeesSpinner = findViewById(R.id.spinnerAttendees)
        buttonEditCustomAttendees = findViewById(R.id.buttonEditCustomAttendees)
        val updateButton = findViewById<Button>(R.id.buttonUpdateMeeting)

        // Updated attendee options - removed "dean" and kept requested options
        val attendeeOptions = arrayOf("All Associate Prof", "All Assistant Prof", "All Faculty", "Custom")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_large, attendeeOptions)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_large)
        attendeesSpinner.adapter = adapter


        // Set up attendees spinner listener FIRST
        setupAttendeesSpinner()
        
        // Fetch and display existing meeting data
        if (meetingId != null) {
            loadMeetingData(meetingId!!)
        } else {
            Toast.makeText(this, "Error: No meeting ID provided.", Toast.LENGTH_LONG).show()
            finish()
        }

        // Set up date and time pickers (same as before)
        setupPickers()
        
        // Load previous meetings for suggestions
        loadPreviousMeetingsForSuggestions()

        updateButton.setOnClickListener {
            saveMeeting()
        }
        
        // Set up the edit custom attendees button
        buttonEditCustomAttendees.setOnClickListener {
            val intent = Intent(this, SelectAttendeesActivity::class.java)
            intent.putStringArrayListExtra("SELECTED_UIDS", ArrayList(customAttendeeUids))
            startActivityForResult(intent, SELECT_ATTENDEES_REQUEST_CODE)
        }
    }

    private fun loadPreviousMeetingsForSuggestions() {
        val currentUser = auth.currentUser ?: return
        val schedulerId = currentUser.uid
        
        db.collection("meetings")
            .whereEqualTo("scheduledBy", schedulerId)
            .get()
            .addOnSuccessListener { result ->
                // Clear existing data
                titleFrequencyMap.clear()
                locationFrequencyMap.clear()
                sortedTitles.clear()
                sortedLocations.clear()
                
                // Process all previous meetings
                for (document in result) {
                    val meeting = document.toObject(Meeting::class.java)
                    val title = meeting.title.trim()
                    val location = meeting.location.trim()
                    
                    // Only count non-empty titles and locations
                    if (title.isNotEmpty()) {
                        titleFrequencyMap[title] = titleFrequencyMap.getOrDefault(title, 0) + 1
                    }
                    
                    if (location.isNotEmpty()) {
                        locationFrequencyMap[location] = locationFrequencyMap.getOrDefault(location, 0) + 1
                    }
                }
                
                // Sort by frequency (descending) and then alphabetically
                sortedTitles.addAll(
                    titleFrequencyMap.entries
                        .sortedWith(compareBy({ -it.value }, { it.key }))
                        .map { it.key }
                )

                sortedLocations.addAll(
                    locationFrequencyMap.entries
                        .sortedWith(compareBy({ -it.value }, { it.key }))
                        .map { it.key }
                )
                
                // Set up auto-complete for title and location
                setupAutoComplete()
            }
            .addOnFailureListener { exception ->
                // Handle error silently as this is a convenience feature
            }
    }
    
    private fun setupAutoComplete() {
        // Set up auto-complete for title
        val titleAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sortedTitles)
        titleEditText.setAdapter(titleAdapter)
        
        // Set up auto-complete for location
        val locationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sortedLocations)
        locationEditText.setAdapter(locationAdapter)
    }

    private fun loadMeetingData(id: String) {
        db.collection("meetings").document(id).get()
            .addOnSuccessListener { document ->
                val meeting = document.toObject(Meeting::class.java)
                if (meeting != null) {
                    originalMeeting = meeting

                    // --- Fill basic fields ---
                    titleEditText.setText(meeting.title)
                    locationEditText.setText(meeting.location)

                    val calendar = Calendar.getInstance()
                    calendar.time = meeting.dateTime.toDate()

                    selectedYear = calendar.get(Calendar.YEAR)
                    selectedMonth = calendar.get(Calendar.MONTH)
                    selectedDay = calendar.get(Calendar.DAY_OF_MONTH)
                    selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
                    selectedMinute = calendar.get(Calendar.MINUTE)

                    dateButton.text = SimpleDateFormat("d/M/yyyy", Locale.getDefault())
                        .format(calendar.time)
                    timeButton.text = SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(calendar.time)

                    // --- Set attendees without triggering listener ---
                    val attendeePosition =
                        (attendeesSpinner.adapter as ArrayAdapter<String>)
                            .getPosition(meeting.attendees)

                    // 🔥 IMPORTANT: prevent spinner auto-triggering
                    isInitialSpinnerSetup = true   // Ignore any onItemSelected events

                    attendeesSpinner.setSelection(attendeePosition, false)

                    // --- Load custom attendee list if needed ---
                    if (meeting.attendees == "Custom") {
                        customAttendeeUids.clear()
                        customAttendeeUids.addAll(meeting.customAttendeeUids)
                    }

                    // 🔥 IMPORTANT: Re-enable listener AFTER UI finishes loading
                    attendeesSpinner.post {
                        isInitialSpinnerSetup = false
                    }

                    // Check if user can edit (attendance rules)
                    checkEditPermission(meeting)
                }
            }
    }



    private fun checkEditPermission(meeting: Meeting) {
        // If attendance has been taken, check if editing is still allowed
        meeting.attendanceTakenAt?.let { attendanceTimestamp -> 
            val attendanceTime = attendanceTimestamp.toDate()
            if (!WorkingDaysUtils.canEditMeeting(attendanceTime)) {
                // Show dialog explaining why editing is not allowed
                AlertDialog.Builder(this)
                    .setTitle("Editing Not Allowed")
                    .setMessage("Attendance for this meeting was taken more than 3 working days ago. Editing is no longer permitted.")
                    .setPositiveButton("OK") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
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

    // In EditMeetingActivity.kt

    // In EditMeetingActivity.kt

    private fun setupAttendeesSpinner() {
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = parent.getItemAtPosition(position).toString()

                if (selected == "Custom") {
                    buttonEditCustomAttendees.visibility = View.VISIBLE
                } else {
                    buttonEditCustomAttendees.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        attendeesSpinner.onItemSelectedListener = listener
        originalSpinnerListener = listener
    }







    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SELECT_ATTENDEES_REQUEST_CODE && resultCode == RESULT_OK) {

            val selectedUids = data?.getStringArrayListExtra("SELECTED_UIDS")
            customAttendeeUids.clear()
            if (selectedUids != null) customAttendeeUids.addAll(selectedUids)

            val customPos = (attendeesSpinner.adapter as ArrayAdapter<String>).getPosition("Custom")

            attendeesSpinner.onItemSelectedListener = null
            attendeesSpinner.setSelection(customPos, false)
            attendeesSpinner.onItemSelectedListener = originalSpinnerListener
        }
    }




    private fun launchSelectAttendees() {
        val intent = Intent(this, SelectAttendeesActivity::class.java)
        if (customAttendeeUids.isNotEmpty()) {
            intent.putStringArrayListExtra("SELECTED_UIDS", ArrayList(customAttendeeUids))
        }
        startActivityForResult(intent, SELECT_ATTENDEES_REQUEST_CODE)
    }



    private fun saveMeeting() {
        val updatedTitle = titleEditText.text.toString().trim()
        val updatedLocation = locationEditText.text.toString().trim()
        val updatedAttendees = attendeesSpinner.selectedItem.toString()
        val currentUserUid = auth.currentUser?.uid

        // Validate inputs
        if (updatedTitle.isEmpty()) {
            titleEditText.error = "Title is required"
            return
        }
        
        if (selectedDay == 0 || selectedMonth == 0 || selectedYear == 0) {
            Toast.makeText(this, "Please select a valid date", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedHour == 0 && selectedMinute == 0) {
            Toast.makeText(this, "Please select a valid time", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentUserUid == null) {
            Toast.makeText(this, "Error: You must be logged in.", Toast.LENGTH_LONG).show()
            return
        }

        val calendar = Calendar.getInstance()
        calendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Check if the selected date/time is in the past
        val currentCalendar = Calendar.getInstance()
        if (calendar.before(currentCalendar)) {
            Toast.makeText(this, "Cannot schedule meetings in the past. Please select a future date and time.", Toast.LENGTH_LONG).show()
            return
        }

        // NEW: Prevent users from scheduling meetings with only themselves
        if (updatedAttendees == "Custom" && customAttendeeUids.size == 1 && customAttendeeUids.contains(currentUserUid)) {
            Toast.makeText(this, "You cannot schedule a meeting for yourself only. Please select at least one other attendee.", Toast.LENGTH_LONG).show()
            return
        }

        // Create a map of the fields to update
        val updates = mutableMapOf<String, Any>(
            "title" to updatedTitle,
            "location" to updatedLocation,
            "attendees" to updatedAttendees,
            "dateTime" to Timestamp(calendar.time)
        )
        
        // If this is a custom meeting, also update the custom attendee UIDs
        if (updatedAttendees == "Custom") {
            updates["customAttendeeUids"] = customAttendeeUids
        }

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
    
    companion object {
        private const val SELECT_ATTENDEES_REQUEST_CODE = 1001
    }
}