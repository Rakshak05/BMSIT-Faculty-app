package com.bmsit.faculty

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

// Import the User class for conflict detection
import com.bmsit.faculty.User

class ScheduleMeetingActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var titleEditText: AutoCompleteTextView
    private lateinit var locationEditText: AutoCompleteTextView
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var durationSpinner: Spinner
    private lateinit var attendeesSpinner: Spinner
    private lateinit var buttonEditCustomAttendees: Button
    private lateinit var scheduleButton: Button

    private var selectedYear = -1
    private var selectedMonth = -1
    private var selectedDay = -1
    private var selectedHour = -1
    private var selectedMinute = -1
    private var selectedDuration = 60 // Default to 60 minutes (1 hour)

    // For Custom attendees
    private val customAttendeeUids = mutableListOf<String>()
    
    // Flag to prevent multiple saves
    private var isMeetingSaved = false

    // Data structures to store frequency of titles and locations
    private val titleFrequencyMap = mutableMapOf<String, Int>()
    private val locationFrequencyMap = mutableMapOf<String, Int>()
    
    // Lists to store sorted suggestions
    private val sortedTitles = mutableListOf<String>()
    private val sortedLocations = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_meeting)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        titleEditText = findViewById(R.id.editTextMeetingTitle)
        locationEditText = findViewById(R.id.editTextMeetingLocation)
        dateButton = findViewById(R.id.buttonPickDate)
        timeButton = findViewById(R.id.buttonPickTime)
        durationSpinner = findViewById(R.id.spinnerDuration)
        attendeesSpinner = findViewById(R.id.spinnerAttendees)
        buttonEditCustomAttendees = findViewById(R.id.buttonEditCustomAttendees)
        scheduleButton = findViewById(R.id.buttonSaveMeeting)

        setupSpinners()
        setupPickers()
        loadPreviousMeetingsForSuggestions()

        scheduleButton.setOnClickListener {
            if (!isMeetingSaved) {
                isMeetingSaved = true
                saveMeetingToFirestore()
            }
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
                Log.e("ScheduleMeetingActivity", "Error loading previous meetings", exception)
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

    private fun setupSpinners() {
        // Setup duration spinner
        val durationOptions = arrayOf("30 minutes", "1 hour", "1.5 hours", "2 hours", "2.5 hours", "3 hours")
        val durationAdapter = ArrayAdapter(this, R.layout.spinner_item_large, durationOptions)
        durationAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_large)
        durationSpinner.adapter = durationAdapter
        
        // Set default to 1 hour (index 1)
        durationSpinner.setSelection(1)
        
        durationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedDuration = when (position) {
                    0 -> 30   // 30 minutes
                    1 -> 60   // 1 hour
                    2 -> 90   // 1.5 hours
                    3 -> 120  // 2 hours
                    4 -> 150  // 2.5 hours
                    5 -> 180  // 3 hours
                    else -> 60 // Default to 1 hour
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedDuration = 60 // Default to 1 hour
            }
        }

        // Updated attendee options - removed "dean" and kept requested options
        val attendeeOptions = arrayOf("All Associate Prof", "All Assistant Prof", "All Faculty", "Custom")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_large, attendeeOptions)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_large)
        attendeesSpinner.adapter = adapter
        
        // Set "All Faculty" as the default selection (index 2)
        attendeesSpinner.setSelection(2)

        attendeesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = parent.getItemAtPosition(position).toString()

                if (selected == "Custom") {
                    buttonEditCustomAttendees.visibility = View.VISIBLE
                } else {
                    buttonEditCustomAttendees.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_ATTENDEES_REQUEST_CODE && resultCode == RESULT_OK) {
            // Clear previous selections
            customAttendeeUids.clear()
            
            // Get the selected UIDs from the intent
            val selectedUids = data?.getStringArrayListExtra("SELECTED_UIDS")
            if (selectedUids != null) {
                customAttendeeUids.addAll(selectedUids)
                Toast.makeText(this, "${customAttendeeUids.size} attendees selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveMeetingToFirestore() {
        val title = titleEditText.text.toString().trim()
        val location = locationEditText.text.toString().trim()
        val attendees = attendeesSpinner.selectedItem.toString()
        val schedulerId = auth.currentUser?.uid

        if (title.isEmpty()) {
            titleEditText.error = "Title is required"
            isMeetingSaved = false // Reset flag
            return
        }
        if (selectedDay == -1 || selectedMonth == -1 || selectedYear == -1) {
            Toast.makeText(this, "Please select a valid date", Toast.LENGTH_SHORT).show()
            isMeetingSaved = false // Reset flag
            return
        }
        if (selectedHour == -1 || selectedMinute == -1) {
            Toast.makeText(this, "Please select a valid time", Toast.LENGTH_SHORT).show()
            isMeetingSaved = false // Reset flag
            return
        }
        if (schedulerId == null) {
            Toast.makeText(this, "Error: You must be logged in.", Toast.LENGTH_LONG).show()
            isMeetingSaved = false // Reset flag
            return
        }

        // Check user designation to restrict meeting scheduling to HODs, Associate Professors, and HOD's Assistants
        db.collection("users").document(schedulerId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userDesignation = document.getString("designation")
                    // Only HODs, Associate Professors, and HOD's Assistants can schedule meetings
                    if (userDesignation != "HOD" && userDesignation != "Associate Professor" && userDesignation != "HOD'S ASSISTANT") {
                        Toast.makeText(this, "Only HODs, Associate Professors, and HOD's Assistants are authorized to schedule meetings.", Toast.LENGTH_LONG).show()
                        isMeetingSaved = false // Reset flag
                        finish() // Close the activity
                        return@addOnSuccessListener
                    }
                    
                    // Continue with meeting scheduling if user is authorized
                    proceedWithMeetingScheduling(title, location, attendees, schedulerId)
                } else {
                    Toast.makeText(this, "Error: User data not found.", Toast.LENGTH_LONG).show()
                    isMeetingSaved = false // Reset flag
                    finish() // Close the activity
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking user authorization: ${e.message}", Toast.LENGTH_LONG).show()
                isMeetingSaved = false // Reset flag
                finish() // Close the activity
            }
    }
    
    private fun proceedWithMeetingScheduling(title: String, location: String, attendees: String, schedulerId: String) {
        // Validation for Custom meetings
        if (attendees == "Custom" && customAttendeeUids.isEmpty()) {
            Toast.makeText(this, "Please select at least one custom attendee.", Toast.LENGTH_SHORT).show()
            isMeetingSaved = false // Reset flag
            return
        }
        
        // NEW: Prevent users from scheduling meetings with only themselves
        if (attendees == "Custom" && customAttendeeUids.size == 1 && customAttendeeUids.contains(schedulerId)) {
            Toast.makeText(this, "You cannot schedule a meeting for yourself only. Please select at least one other attendee.", Toast.LENGTH_SHORT).show()
            isMeetingSaved = false // Reset flag
            return
        }

        val calendar = Calendar.getInstance()
        calendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Check if the selected date/time is in the past
        val currentCalendar = Calendar.getInstance()
        if (calendar.before(currentCalendar)) {
            Toast.makeText(this, "Cannot schedule meetings in the past. Please select a future date and time.", Toast.LENGTH_LONG).show()
            isMeetingSaved = false // Reset flag
            return
        }

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
            status = "Active",
            duration = selectedDuration
        )

        // Check for conflicts with existing meetings
        checkForConflicts(newMeeting) { hasConflicts ->
            if (!hasConflicts) {
                // No conflicts, proceed with saving
                db.collection("meetings").document(meetingId).set(newMeeting)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Meeting scheduled successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error scheduling meeting: ${e.message}", Toast.LENGTH_LONG).show()
                        isMeetingSaved = false // Reset flag
                    }
            } else {
                // Conflicts found, don't save and reset flag
                isMeetingSaved = false // Reset flag
            }
        }
    }

    private fun checkForConflicts(newMeeting: Meeting, callback: (Boolean) -> Unit) {
        val currentUser = auth.currentUser ?: return
        val schedulerId = currentUser.uid
        
        // Get all active meetings for the current user (either scheduled by them or they're attending)
        db.collection("meetings")
            .whereEqualTo("status", "Active")
            .get()
            .addOnSuccessListener { result ->
                val conflictingMeetings = mutableListOf<Meeting>()
                
                for (document in result) {
                    val existingMeeting = document.toObject(Meeting::class.java)
                    
                    // Skip the meeting we're trying to schedule (if it somehow exists)
                    if (existingMeeting.id == newMeeting.id) continue
                    
                    // Check if the current user is involved in this existing meeting
                    // Either they scheduled it or they're attending it
                    val isUserInvolvedInExisting = existingMeeting.scheduledBy == schedulerId ||
                            (existingMeeting.attendees == "Custom" && existingMeeting.customAttendeeUids.contains(schedulerId))
                    
                    // Only check for conflicts if user is involved in the existing meeting
                    if (isUserInvolvedInExisting) {
                        // Check if the meetings overlap in time
                        if (meetingsOverlap(newMeeting, existingMeeting)) {
                            conflictingMeetings.add(existingMeeting)
                        }
                    }
                }
                
                if (conflictingMeetings.isEmpty()) {
                    callback(false) // No conflicts
                } else {
                    // Show detailed conflict information
                    showConflictDetails(newMeeting, conflictingMeetings)
                    callback(true) // Has conflicts
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ScheduleMeetingActivity", "Error checking for conflicts", exception)
                // In case of error, we'll be conservative and assume there's a conflict
                Toast.makeText(this, "Error checking for conflicts. Please try again.", Toast.LENGTH_LONG).show()
                callback(true)
            }
    }
    
    /**
     * Show detailed information about conflicting meetings
     */
    private fun showConflictDetails(newMeeting: Meeting, conflictingMeetings: List<Meeting>) {
        // Fetch user names for all conflicting meetings
        fetchUserNamesForConflicts(conflictingMeetings) { userNamesMap ->
            val conflictMessage = buildConflictMessage(newMeeting, conflictingMeetings, userNamesMap)
            
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Scheduling Conflict")
                    .setMessage(conflictMessage)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
    }
    
    /**
     * Fetch user names for conflicting meetings
     */
    private fun fetchUserNamesForConflicts(conflictingMeetings: List<Meeting>, callback: (Map<String, String>) -> Unit) {
        val userNamesMap = mutableMapOf<String, String>()
        val schedulerIds = conflictingMeetings.map { it.scheduledBy }.distinct()
        
        if (schedulerIds.isEmpty()) {
            callback(userNamesMap)
            return
        }
        
        var completedRequests = 0
        val totalRequests = schedulerIds.size
        
        schedulerIds.forEach { userId ->
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val user = document.toObject(User::class.java)
                        userNamesMap[userId] = user?.name ?: "Unknown User"
                    } else {
                        userNamesMap[userId] = "Unknown User"
                    }
                    
                    completedRequests++
                    if (completedRequests == totalRequests) {
                        callback(userNamesMap)
                    }
                }
                .addOnFailureListener {
                    userNamesMap[userId] = "Unknown User"
                    completedRequests++
                    if (completedRequests == totalRequests) {
                        callback(userNamesMap)
                    }
                }
        }
    }
    
    /**
     * Build a detailed conflict message
     */
    private fun buildConflictMessage(newMeeting: Meeting, conflictingMeetings: List<Meeting>, userNamesMap: Map<String, String>): String {
        val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        
        val newMeetingStartTime = newMeeting.dateTime.toDate()
        val calendar = Calendar.getInstance()
        calendar.time = newMeetingStartTime
        val duration = if (newMeeting.duration > 0) newMeeting.duration else 60
        calendar.add(Calendar.MINUTE, duration)
        val newMeetingEndTime = calendar.time
        
        val buffer = StringBuilder()
        buffer.append("The meeting \"${newMeeting.title}\" scheduled from ")
        buffer.append("${timeFormat.format(newMeetingStartTime)} to ${timeFormat.format(newMeetingEndTime)} ")
        buffer.append("conflicts with the following existing meeting(s):\n\n")
        
        conflictingMeetings.forEachIndexed { index, meeting ->
            val meetingStartTime = meeting.dateTime.toDate()
            val meetingCalendar = Calendar.getInstance()
            meetingCalendar.time = meetingStartTime
            val meetingDuration = if (meeting.duration > 0) meeting.duration else 60
            meetingCalendar.add(Calendar.MINUTE, meetingDuration)
            val meetingEndTime = meetingCalendar.time
            
            val hostName = userNamesMap[meeting.scheduledBy] ?: "Unknown User"
            
            buffer.append("${index + 1}. \"${meeting.title}\"\n")
            buffer.append("   Time: ${timeFormat.format(meetingStartTime)} to ${timeFormat.format(meetingEndTime)}\n")
            buffer.append("   Host: $hostName\n")
            buffer.append("   Attendees: ${meeting.attendees}\n\n")
        }
        
        buffer.append("Please choose a different time slot.")
        return buffer.toString()
    }
    
    /**
     * Check if two meetings overlap in time
     */
    private fun meetingsOverlap(meeting1: Meeting, meeting2: Meeting): Boolean {
        val start1 = meeting1.dateTime.toDate()
        val end1 = getMeetingEndTime(meeting1)
        
        val start2 = meeting2.dateTime.toDate()
        val end2 = getMeetingEndTime(meeting2)
        
        // Two meetings overlap if one starts before the other ends
        // and ends after the other starts
        return start1.before(end2) && end1.after(start2)
    }
    
    /**
     * Get the end time of a meeting, considering both explicit endTime and expected duration
     */
    private fun getMeetingEndTime(meeting: Meeting): Date {
        // If meeting has an explicit end time, use that
        if (meeting.endTime != null) {
            return meeting.endTime!!.toDate()
        }
        
        // Otherwise, calculate expected end time based on duration
        val startTime = meeting.dateTime.toDate()
        val calendar = Calendar.getInstance()
        calendar.time = startTime
        // Ensure duration is valid, default to 60 minutes if not set properly
        val duration = if (meeting.duration > 0) meeting.duration else 60
        calendar.add(Calendar.MINUTE, duration)
        return calendar.time
    }

    companion object {
        private const val SELECT_ATTENDEES_REQUEST_CODE = 1001
    }
}