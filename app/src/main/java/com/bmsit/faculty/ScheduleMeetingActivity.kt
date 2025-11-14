package com.bmsit.faculty

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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

class ScheduleMeetingActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var titleEditText: EditText
    private lateinit var locationEditText: EditText
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var durationSpinner: Spinner
    private lateinit var attendeesSpinner: Spinner
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
        scheduleButton = findViewById(R.id.buttonSaveMeeting)

        setupSpinners()
        setupPickers()

        scheduleButton.setOnClickListener {
            if (!isMeetingSaved) {
                isMeetingSaved = true
                saveMeetingToFirestore()
            }
        }
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

        val attendeeOptions = arrayOf("All Faculty", "All Deans", "All HODs", "Custom")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_large, attendeeOptions)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_large)
        attendeesSpinner.adapter = adapter

        attendeesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = parent.getItemAtPosition(position).toString()
                if (selected == "Custom") {
                    // Launch the SelectAttendeesActivity to choose custom attendees
                    val intent = android.content.Intent(this@ScheduleMeetingActivity, SelectAttendeesActivity::class.java)
                    startActivityForResult(intent, SELECT_ATTENDEES_REQUEST_CODE)
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

        // Check user designation to prevent Assistant Professors and Lab Assistants from scheduling meetings
        db.collection("users").document(schedulerId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userDesignation = document.getString("designation")
                    if (userDesignation == "Assistant Professor" || userDesignation == "Lab Assistant") {
                        Toast.makeText(this, "Assistant Professors and Lab Assistants are not authorized to schedule meetings.", Toast.LENGTH_LONG).show()
                        isMeetingSaved = false // Reset flag
                        return@addOnSuccessListener
                    }
                    
                    // Continue with meeting scheduling if user is authorized
                    proceedWithMeetingScheduling(title, location, attendees, schedulerId)
                } else {
                    Toast.makeText(this, "Error: User data not found.", Toast.LENGTH_LONG).show()
                    isMeetingSaved = false // Reset flag
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking user authorization: ${e.message}", Toast.LENGTH_LONG).show()
                isMeetingSaved = false // Reset flag
            }
    }
    
    private fun proceedWithMeetingScheduling(title: String, location: String, attendees: String, schedulerId: String) {
        // Validation for Custom meetings
        if (attendees == "Custom" && customAttendeeUids.isEmpty()) {
            Toast.makeText(this, "Please select at least one custom attendee.", Toast.LENGTH_SHORT).show()
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
            // Save the duration of the meeting
            duration = selectedDuration
        )

        // Check for conflicts before saving the meeting using the new system
        checkForConflictsBeforeScheduling(newMeeting, schedulerId)
    }
    
    private fun saveMeetingToFirestore(meeting: Meeting) {
        db.collection("meetings").document(meeting.id).set(meeting)
            .addOnSuccessListener {
                Toast.makeText(this, "Meeting scheduled successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving meeting. Please try again.", Toast.LENGTH_SHORT).show()
                Log.w("ScheduleMeeting", "Error writing document", e)
                isMeetingSaved = false // Reset flag on failure
            }
    }
    
    // New function to properly check for attendee conflicts
    private fun checkForAttendeeConflicts(
        newMeeting: Meeting,
        existingMeetings: List<Meeting>,
        schedulerId: String,
        onConflictCheckComplete: (Boolean) -> Unit
    ) {
        // If there are no existing meetings, there can't be conflicts
        if (existingMeetings.isEmpty()) {
            onConflictCheckComplete(false)
            return
        }
        
        // Get the list of attendees for the new meeting
        val newMeetingAttendeeUids = when (newMeeting.attendees) {
            "Custom" -> newMeeting.customAttendeeUids
            else -> {
                // For group meetings, we'll need to get the list of users
                // For now, we'll return an empty list and handle group meetings differently
                emptyList()
            }
        }
        
        if (newMeeting.attendees != "Custom" && newMeetingAttendeeUids.isEmpty()) {
            // For group meetings, we'll check differently
            checkGroupMeetingConflicts(newMeeting, existingMeetings, schedulerId, onConflictCheckComplete)
            return
        }
        
        if (newMeetingAttendeeUids.isEmpty()) {
            onConflictCheckComplete(false)
            return
        }
        
        // Check each existing meeting for attendee conflicts
        var conflictFound = false
        val conflictDetails = mutableListOf<String>()
        
        for (existingMeeting in existingMeetings) {
            // Check if this meeting affects any of our attendees
            val affectsAttendees = when (existingMeeting.attendees) {
                "Custom" -> {
                    // For custom meetings, check if there's overlap in attendee lists
                    existingMeeting.customAttendeeUids.any { it in newMeetingAttendeeUids }
                }
                else -> {
                    // For group meetings, we'll assume there could be overlap
                    // In a real implementation, you'd check if any of the new meeting's attendees
                    // belong to the group of the existing meeting
                    true
                }
            }
            
            if (affectsAttendees) {
                conflictFound = true
                // Get host name for conflict details
                db.collection("users").document(existingMeeting.scheduledBy).get()
                    .addOnSuccessListener { hostDoc ->
                        val hostName = hostDoc.getString("name") ?: "Unknown User"
                        val startTime = existingMeeting.dateTime.toDate()
                        val calendar = Calendar.getInstance()
                        calendar.time = startTime
                        calendar.add(Calendar.MINUTE, existingMeeting.duration)
                        val endTime = calendar.time
                        
                        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                        val conflictInfo = "${existingMeeting.title} from ${timeFormat.format(startTime)} to ${timeFormat.format(endTime)} hosted by $hostName"
                        conflictDetails.add(conflictInfo)
                    }
                    .addOnFailureListener {
                        val conflictInfo = "${existingMeeting.title} hosted by Unknown User"
                        conflictDetails.add(conflictInfo)
                    }
            }
        }
        
        // If conflicts were found, show details and prevent scheduling
        if (conflictFound) {
            // Wait a bit for all the Firestore calls to complete
            android.os.Handler().postDelayed({
                showConflictDetails(conflictDetails.toList())
                onConflictCheckComplete(true)
            }, 500)
        } else {
            onConflictCheckComplete(false)
        }
    }
    
    // Check conflicts for group meetings
    private fun checkGroupMeetingConflicts(
        newMeeting: Meeting,
        existingMeetings: List<Meeting>,
        schedulerId: String,
        onConflictCheckComplete: (Boolean) -> Unit
    ) {
        // For group meetings, we'll check if any existing meetings affect the same group
        var conflictFound = false
        val conflictDetails = mutableListOf<String>()
        
        for (existingMeeting in existingMeetings) {
            // Check if the existing meeting affects the same group
            val affectsGroup = when {
                // Same group type
                existingMeeting.attendees == newMeeting.attendees -> true
                // Custom meeting that might include group members
                existingMeeting.attendees == "Custom" -> {
                    // We would need to check if any group members are in the custom list
                    // For now, we'll assume there could be overlap
                    true
                }
                // Group meeting that might include custom attendees
                newMeeting.attendees == "Custom" -> {
                    // We would need to check if any custom attendees belong to the group
                    // For now, we'll assume there could be overlap
                    true
                }
                else -> false
            }
            
            if (affectsGroup) {
                conflictFound = true
                // Get host name for conflict details
                db.collection("users").document(existingMeeting.scheduledBy).get()
                    .addOnSuccessListener { hostDoc ->
                        val hostName = hostDoc.getString("name") ?: "Unknown User"
                        val startTime = existingMeeting.dateTime.toDate()
                        val calendar = Calendar.getInstance()
                        calendar.time = startTime
                        calendar.add(Calendar.MINUTE, existingMeeting.duration)
                        val endTime = calendar.time
                        
                        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                        val conflictInfo = "${existingMeeting.title} from ${timeFormat.format(startTime)} to ${timeFormat.format(endTime)} hosted by $hostName"
                        conflictDetails.add(conflictInfo)
                    }
                    .addOnFailureListener {
                        val conflictInfo = "${existingMeeting.title} hosted by Unknown User"
                        conflictDetails.add(conflictInfo)
                    }
            }
        }
        
        // If conflicts were found, show details and prevent scheduling
        if (conflictFound) {
            // Wait a bit for all the Firestore calls to complete
            android.os.Handler().postDelayed({
                showConflictDetails(conflictDetails.toList())
                onConflictCheckComplete(true)
            }, 500)
        } else {
            onConflictCheckComplete(false)
        }
    }
    
    // Show conflict details to the scheduler
    private fun showConflictDetails(conflictDetails: List<String>) {
        val conflictMessage = if (conflictDetails.size == 1) {
            // Single conflict - use the first format
            val conflict = conflictDetails[0]
            // Extract meeting name and time from conflict string
            val parts = conflict.split(" hosted by ")
            if (parts.size >= 2) {
                val meetingName = parts[0]
                val timeAndHost = parts[1].split(" from ")
                if (timeAndHost.size >= 2) {
                    val hostName = timeAndHost[0]
                    val timeInfo = timeAndHost[1]
                    val timeParts = timeInfo.split(" to ")
                    val startTime = if (timeParts.isNotEmpty()) timeParts[0] else "the scheduled time"
                    val duration = if (timeParts.size >= 2) {
                        try {
                            val start = SimpleDateFormat("h:mm a", Locale.getDefault()).parse(startTime)
                            val end = SimpleDateFormat("h:mm a", Locale.getDefault()).parse(timeParts[1])
                            if (start != null && end != null) {
                                val diff = (end.time - start.time) / (1000 * 60) // difference in minutes
                                "${diff / 60}hrs" + if (diff % 60 > 0) " ${diff % 60}mins" else ""
                            } else {
                                "2hrs"
                            }
                        } catch (e: Exception) {
                            "2hrs"
                        }
                    } else {
                        "2hrs"
                    }
                    "This person is attending $meetingName from $startTime and is expected to last for $duration."
                } else {
                    "This person is attending $meetingName during this time slot."
                }
            } else {
                "This person is attending a meeting during this time slot."
            }
        } else {
            // Multiple conflicts - use the second format
            val conflictMessage = StringBuilder("The below listed prof's are attending meetings from the scheduled time:\n")
            conflictDetails.forEach { conflict ->
                conflictMessage.append("\n• $conflict")
            }
            conflictMessage.toString()
        }
        
        AlertDialog.Builder(this)
            .setTitle("Meeting Conflicts Detected")
            .setMessage(conflictMessage)
            .setPositiveButton("OK") { _, _ ->
                isMeetingSaved = false // Reset flag
            }
            .setCancelable(false)
            .show()
    }
    
    // Data class to represent a time slot in a user's calendar
    data class TimeSlot(
        val startTime: Date,
        val endTime: Date,
        val meetingId: String,
        val meetingTitle: String,
        val attendees: List<String>, // UIDs of attendees
        val reason: String
    )
    
    // Data class to represent a user's daily calendar
    data class UserCalendar(
        val userId: String,
        val date: Date,
        val timeSlots: MutableList<TimeSlot> = mutableListOf()
    )
    
    // Function to check if a time slot is available for all attendees
    private fun checkTimeSlotAvailability(
        attendees: List<String>,
        startTime: Date,
        endTime: Date,
        onAvailabilityCheckComplete: (Boolean, List<String>) -> Unit // (isAvailable, conflictingUsers)
    ) {
        val conflictingUsers = mutableListOf<String>()
        var usersChecked = 0
        
        if (attendees.isEmpty()) {
            onAvailabilityCheckComplete(true, emptyList())
            return
        }
        
        for (userId in attendees) {
            checkUserCalendar(userId, startTime, endTime) { isAvailable ->
                usersChecked++
                
                if (!isAvailable) {
                    conflictingUsers.add(userId)
                }
                
                // If we've checked all users, complete the check
                if (usersChecked == attendees.size) {
                    onAvailabilityCheckComplete(conflictingUsers.isEmpty(), conflictingUsers)
                }
            }
        }
    }
    
    // Function to check a specific user's calendar for availability
    private fun checkUserCalendar(
        userId: String,
        startTime: Date,
        endTime: Date,
        onCheckComplete: (Boolean) -> Unit
    ) {
        // Get all meetings for this user on the same date
        val calendar = Calendar.getInstance()
        calendar.time = startTime
        val startOfDay = calendar.clone() as Calendar
        startOfDay.set(Calendar.HOUR_OF_DAY, 0)
        startOfDay.set(Calendar.MINUTE, 0)
        startOfDay.set(Calendar.SECOND, 0)
        startOfDay.set(Calendar.MILLISECOND, 0)
        
        val endOfDay = calendar.clone() as Calendar
        endOfDay.set(Calendar.HOUR_OF_DAY, 23)
        endOfDay.set(Calendar.MINUTE, 59)
        endOfDay.set(Calendar.SECOND, 59)
        endOfDay.set(Calendar.MILLISECOND, 999)
        
        val startTs = Timestamp(startOfDay.time)
        val endTs = Timestamp(endOfDay.time)
        
        // Get all meetings for this user on this day
        db.collection("meetings")
            .whereGreaterThanOrEqualTo("dateTime", startTs)
            .whereLessThan("dateTime", endTs)
            .get()
            .addOnSuccessListener { result ->
                var hasConflict = false
                val meetingsToCheck = result.size()
                var meetingsChecked = 0
                
                if (meetingsToCheck == 0) {
                    onCheckComplete(true)
                    return@addOnSuccessListener
                }
                
                for (doc in result) {
                    val meeting = doc.toObject(Meeting::class.java)
                    
                    // Skip inactive meetings
                    if (meeting.status != "Active") {
                        meetingsChecked++
                        if (meetingsChecked == meetingsToCheck) {
                            onCheckComplete(!hasConflict)
                        }
                        continue
                    }
                    
                    // Check if this meeting involves the user
                    checkIfUserIsAttendee(userId, meeting) { userIsAttendee ->
                        meetingsChecked++
                        
                        if (userIsAttendee) {
                            // Check if time ranges overlap
                            val meetingStart = meeting.dateTime.toDate()
                            val meetingCalendar = Calendar.getInstance()
                            meetingCalendar.time = meetingStart
                            meetingCalendar.add(Calendar.MINUTE, meeting.duration)
                            val meetingEnd = meetingCalendar.time
                            
                            if (timeRangesOverlap(startTime, endTime, meetingStart, meetingEnd)) {
                                hasConflict = true
                            }
                        }
                        
                        // If we've checked all meetings, complete the check
                        if (meetingsChecked == meetingsToCheck) {
                            onCheckComplete(!hasConflict)
                        }
                    }
                }
            }
            .addOnFailureListener {
                // If we can't check the calendar, assume it's not available to be safe
                onCheckComplete(false)
            }
    }
    
    // Helper function to check if a user is an attendee of a meeting
    private fun checkIfUserIsAttendee(userId: String, meeting: Meeting, onResult: (Boolean) -> Unit) {
        when (meeting.attendees) {
            "Custom" -> {
                onResult(userId in meeting.customAttendeeUids)
            }
            "All Faculty" -> {
                isUserFaculty(userId) { isFaculty ->
                    onResult(isFaculty)
                }
            }
            "All HODs" -> {
                isUserHOD(userId) { isHOD ->
                    onResult(isHOD)
                }
            }
            "All Deans" -> {
                isUserDean(userId) { isDean ->
                    onResult(isDean)
                }
            }
            else -> {
                onResult(false)
            }
        }
    }
    
    // Helper function to check if two time ranges overlap
    private fun timeRangesOverlap(
        start1: Date, 
        end1: Date, 
        start2: Date, 
        end2: Date
    ): Boolean {
        return start1.before(end2) && start2.before(end1)
    }
    
    // Helper function to check if a user is faculty
    private fun isUserFaculty(userId: String, onResult: (Boolean) -> Unit) {
        // This would typically make a database call
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val department = document.getString("department") ?: ""
                    onResult(department == "AIML")
                } else {
                    onResult(false)
                }
            }
            .addOnFailureListener {
                onResult(false)
            }
    }
    
    // Helper function to check if a user is HOD
    private fun isUserHOD(userId: String, onResult: (Boolean) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val designation = document.getString("designation") ?: ""
                    onResult(designation == "HOD")
                } else {
                    onResult(false)
                }
            }
            .addOnFailureListener {
                onResult(false)
            }
    }
    
    // Helper function to check if a user is Dean
    private fun isUserDean(userId: String, onResult: (Boolean) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val designation = document.getString("designation") ?: ""
                    onResult(designation == "DEAN")
                } else {
                    onResult(false)
                }
            }
            .addOnFailureListener {
                onResult(false)
            }
    }
    
    // Enhanced conflict detection before scheduling
    private fun checkForConflictsBeforeScheduling(newMeeting: Meeting, schedulerId: String) {
        // Define a window for collision detection based on meeting duration
        val cal = Calendar.getInstance()
        cal.time = newMeeting.dateTime.toDate()
        val endCal = cal.clone() as Calendar
        endCal.add(Calendar.MINUTE, newMeeting.duration)

        val startTime = cal.time
        val endTime = endCal.time
        
        // Get the list of attendees for the new meeting
        getAttendeesForMeeting(newMeeting, schedulerId) { attendeeUids ->
            if (attendeeUids.isEmpty()) {
                // No attendees, proceed with saving
                saveMeetingToFirestore(newMeeting)
                return@getAttendeesForMeeting
            }
            
            // Check if the time slot is available for all attendees
            checkTimeSlotAvailability(attendeeUids, startTime, endTime) { isAvailable, conflictingUsers ->
                if (isAvailable) {
                    // No conflicts, proceed with saving
                    saveMeetingToFirestore(newMeeting)
                } else {
                    // Conflicts found, get details and show to user
                    getConflictDetails(conflictingUsers, startTime, endTime) { conflictDetails ->
                        showDetailedConflictInfo(conflictDetails)
                        isMeetingSaved = false // Reset flag
                    }
                }
            }
        }
    }
    
    // Get the list of attendees for a meeting
    private fun getAttendeesForMeeting(
        meeting: Meeting,
        schedulerId: String,
        onAttendeesRetrieved: (List<String>) -> Unit
    ) {
        when (meeting.attendees) {
            "Custom" -> {
                onAttendeesRetrieved(meeting.customAttendeeUids)
            }
            "All Faculty" -> {
                // Get all faculty members in AIML department
                db.collection("users")
                    .whereEqualTo("department", "AIML")
                    .get()
                    .addOnSuccessListener { result ->
                        val uids = result.map { it.id }
                        onAttendeesRetrieved(uids)
                    }
                    .addOnFailureListener {
                        // Fallback to just the scheduler if we can't get the list
                        onAttendeesRetrieved(listOf(schedulerId))
                    }
            }
            "All HODs" -> {
                // Get all HODs
                db.collection("users")
                    .whereEqualTo("designation", "HOD")
                    .get()
                    .addOnSuccessListener { result ->
                        val uids = result.map { it.id }
                        onAttendeesRetrieved(uids)
                    }
                    .addOnFailureListener {
                        // Fallback to just the scheduler if we can't get the list
                        onAttendeesRetrieved(listOf(schedulerId))
                    }
            }
            "All Deans" -> {
                // Get all Deans
                db.collection("users")
                    .whereEqualTo("designation", "DEAN")
                    .get()
                    .addOnSuccessListener { result ->
                        val uids = result.map { it.id }
                        onAttendeesRetrieved(uids)
                    }
                    .addOnFailureListener {
                        // Fallback to just the scheduler if we can't get the list
                        onAttendeesRetrieved(listOf(schedulerId))
                    }
            }
            else -> {
                onAttendeesRetrieved(listOf(schedulerId))
            }
        }
    }
    
    // Get detailed conflict information
    private fun getConflictDetails(
        conflictingUsers: List<String>,
        startTime: Date,
        endTime: Date,
        onDetailsRetrieved: (List<String>) -> Unit
    ) {
        val conflictDetails = mutableListOf<String>()
        var usersChecked = 0
        
        if (conflictingUsers.isEmpty()) {
            onDetailsRetrieved(emptyList())
            return
        }
        
        for (userId in conflictingUsers) {
            // Get user's conflicting meetings
            getUserConflictingMeetings(userId, startTime, endTime) { meetings ->
                conflictDetails.addAll(meetings)
                usersChecked++
                
                if (usersChecked == conflictingUsers.size) {
                    onDetailsRetrieved(conflictDetails)
                }
            }
        }
    }
    
    // Get conflicting meetings for a user
    private fun getUserConflictingMeetings(
        userId: String,
        startTime: Date,
        endTime: Date,
        onMeetingsRetrieved: (List<String>) -> Unit
    ) {
        val conflictDetails = mutableListOf<String>()
        
        // Get user name
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val userName = userDoc.getString("name") ?: "Unknown User"
                
                // Get meetings for this user that conflict with the time slot
                val calendar = Calendar.getInstance()
                calendar.time = startTime
                val startOfDay = calendar.clone() as Calendar
                startOfDay.set(Calendar.HOUR_OF_DAY, 0)
                startOfDay.set(Calendar.MINUTE, 0)
                startOfDay.set(Calendar.SECOND, 0)
                startOfDay.set(Calendar.MILLISECOND, 0)
                
                val endOfDay = calendar.clone() as Calendar
                endOfDay.set(Calendar.HOUR_OF_DAY, 23)
                endOfDay.set(Calendar.MINUTE, 59)
                endOfDay.set(Calendar.SECOND, 59)
                endOfDay.set(Calendar.MILLISECOND, 999)
                
                val startTs = Timestamp(startOfDay.time)
                val endTs = Timestamp(endOfDay.time)
                
                db.collection("meetings")
                    .whereGreaterThanOrEqualTo("dateTime", startTs)
                    .whereLessThan("dateTime", endTs)
                    .get()
                    .addOnSuccessListener { result ->
                        for (doc in result) {
                            val meeting = doc.toObject(Meeting::class.java)
                            
                            // Skip inactive meetings
                            if (meeting.status != "Active") continue
                            
                            // Check if this meeting involves the user
                            val userIsAttendee = when (meeting.attendees) {
                                "Custom" -> userId in meeting.customAttendeeUids
                                else -> true // For group meetings, assume potential conflict
                            }
                            
                            if (userIsAttendee) {
                                // Check if time ranges overlap
                                val meetingStart = meeting.dateTime.toDate()
                                val meetingCalendar = Calendar.getInstance()
                                meetingCalendar.time = meetingStart
                                meetingCalendar.add(Calendar.MINUTE, meeting.duration)
                                val meetingEnd = meetingCalendar.time
                                
                                if (timeRangesOverlap(startTime, endTime, meetingStart, meetingEnd)) {
                                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                                    conflictDetails.add("${meeting.title} from ${timeFormat.format(meetingStart)} to ${timeFormat.format(meetingEnd)} for ${userName}")
                                }
                            }
                        }
                        
                        onMeetingsRetrieved(conflictDetails)
                    }
                    .addOnFailureListener {
                        onMeetingsRetrieved(conflictDetails)
                    }
            }
            .addOnFailureListener {
                onMeetingsRetrieved(conflictDetails)
            }
    }
    
    // Show detailed conflict information
    private fun showDetailedConflictInfo(conflictDetails: List<String>) {
        val conflictMessage = if (conflictDetails.size == 1) {
            // Single conflict - use the first format
            val conflict = conflictDetails[0]
            // Extract meeting name and time from conflict string
            val parts = conflict.split(" from ")
            if (parts.size >= 2) {
                val meetingInfo = parts[0].split(" with ")
                val meetingName = if (meetingInfo.isNotEmpty()) meetingInfo[0] else "a meeting"
                val timeInfo = parts[1].split(" to ")
                val startTime = if (timeInfo.isNotEmpty()) timeInfo[0] else "the scheduled time"
                val duration = if (timeInfo.size >= 2) {
                    try {
                        val start = SimpleDateFormat("h:mm a", Locale.getDefault()).parse(startTime)
                        val end = SimpleDateFormat("h:mm a", Locale.getDefault()).parse(timeInfo[1].split(" for ")[0])
                        if (start != null && end != null) {
                            val diff = (end.time - start.time) / (1000 * 60) // difference in minutes
                            "${diff / 60}hrs" + if (diff % 60 > 0) " ${diff % 60}mins" else ""
                        } else {
                            "2hrs"
                        }
                    } catch (e: Exception) {
                        "2hrs"
                    }
                } else {
                    "2hrs"
                }
                "This person is attending $meetingName from $startTime and is expected to last for $duration."
            } else {
                "This person is attending a meeting during this time slot."
            }
        } else {
            // Multiple conflicts - use the second format
            val conflictMessage = StringBuilder("The below listed prof's are attending meetings during this time slot:\n")
            conflictDetails.forEach { conflict ->
                conflictMessage.append("\n• $conflict")
            }
            conflictMessage.toString()
        }
        
        AlertDialog.Builder(this)
            .setTitle("Time Slot Conflict")
            .setMessage(conflictMessage)
            .setPositiveButton("OK") { _, _ ->
                isMeetingSaved = false // Reset flag
            }
            .setCancelable(false)
            .show()
    }
    
    private fun handleConflicts(
        newMeeting: Meeting, 
        conflictingMeetings: List<Meeting>, 
        myRank: Int, 
        schedulerId: String
    ) {
        // Process the first conflicting meeting
        if (conflictingMeetings.isNotEmpty()) {
            val conflictingMeeting = conflictingMeetings[0]
            
            // Get host name and designation for the conflicting meeting
            db.collection("users").document(conflictingMeeting.scheduledBy).get()
                .addOnSuccessListener { hostDoc ->
                    val hostName = hostDoc.getString("name") ?: "Unknown User"
                    val hostDesignation = hostDoc.getString("designation") ?: "Unknown"
                    val hostRank = designationRank(hostDesignation)
                    
                    // Check if current user is higher ranked than the host
                    if (myRank < hostRank) {
                        // Lower ranked user - show error and prevent scheduling
                        showLowerRankConflictError(conflictingMeeting, hostName)
                        isMeetingSaved = false // Reset flag
                    } else if (myRank == hostRank) {
                        // Same rank - show warning and ask to reschedule
                        showSameRankConflictDialog(newMeeting, conflictingMeeting, hostName)
                    } else {
                        // Higher ranked user - show warning and ask to continue or reschedule
                        showHigherRankConflictDialog(newMeeting, conflictingMeeting, hostName, schedulerId)
                    }
                }
                .addOnFailureListener {
                    // If we can't get host info, assume conflict exists
                    Toast.makeText(this, "This person is attending a meeting during this time slot. Please select a different time.", Toast.LENGTH_LONG).show()
                    isMeetingSaved = false // Reset flag
                }
        } else {
            // This shouldn't happen, but just in case
            Toast.makeText(this, "This person is attending a meeting during this time slot. Please select a different time.", Toast.LENGTH_LONG).show()
            isMeetingSaved = false // Reset flag
        }
    }
    
    private fun showHigherRankConflictDialog(
        newMeeting: Meeting, 
        conflictingMeeting: Meeting, 
        hostName: String,
        schedulerId: String
    ) {
        val startTime = conflictingMeeting.dateTime.toDate()
        val calendar = Calendar.getInstance()
        calendar.time = startTime
        calendar.add(Calendar.MINUTE, conflictingMeeting.duration)
        val endTime = calendar.time
        
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val duration = "${conflictingMeeting.duration / 60}hrs" + 
            if (conflictingMeeting.duration % 60 > 0) " ${conflictingMeeting.duration % 60}mins" else ""
        
        val conflictMessage = "This person is attending ${conflictingMeeting.title} from ${timeFormat.format(startTime)} and is expected to last for $duration."
        
        AlertDialog.Builder(this)
            .setTitle("Meeting Conflict Detected")
            .setMessage(conflictMessage + " Do you want to proceed with scheduling anyway?")
            .setPositiveButton("Schedule Anyway") { _, _ ->
                // Proceed with scheduling despite conflict
                saveMeetingToFirestore(newMeeting)
                // Notify the other host about the conflict
                notifyConflictingHost(newMeeting, conflictingMeeting, hostName, schedulerId)
                isMeetingSaved = true // Set flag to prevent multiple saves
            }
            .setNegativeButton("Change Time") { _, _ ->
                // Cancel scheduling and let user change time
                isMeetingSaved = false // Reset flag
                Toast.makeText(this, "Please select a different time for your meeting.", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showSameRankConflictDialog(
        newMeeting: Meeting, 
        conflictingMeeting: Meeting, 
        hostName: String
    ) {
        val startTime = conflictingMeeting.dateTime.toDate()
        val calendar = Calendar.getInstance()
        calendar.time = startTime
        calendar.add(Calendar.MINUTE, conflictingMeeting.duration)
        val endTime = calendar.time
        
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val duration = "${conflictingMeeting.duration / 60}hrs" + 
            if (conflictingMeeting.duration % 60 > 0) " ${conflictingMeeting.duration % 60}mins" else ""
        
        val conflictMessage = "This person is attending ${conflictingMeeting.title} from ${timeFormat.format(startTime)} and is expected to last for $duration."
        
        AlertDialog.Builder(this)
            .setTitle("Meeting Conflict Detected")
            .setMessage(conflictMessage + " Please select a different time.")
            .setPositiveButton("OK") { _, _ ->
                isMeetingSaved = false // Reset flag
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showLowerRankConflictError(
        conflictingMeeting: Meeting, 
        hostName: String
    ) {
        val startTime = conflictingMeeting.dateTime.toDate()
        val calendar = Calendar.getInstance()
        calendar.time = startTime
        calendar.add(Calendar.MINUTE, conflictingMeeting.duration)
        val endTime = calendar.time
        
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val duration = "${conflictingMeeting.duration / 60}hrs" + 
            if (conflictingMeeting.duration % 60 > 0) " ${conflictingMeeting.duration % 60}mins" else ""
        
        val conflictMessage = "This person is attending ${conflictingMeeting.title} from ${timeFormat.format(startTime)} and is expected to last for $duration."
        
        Toast.makeText(this, "Conflict: $conflictMessage (higher authority) during this time slot.", Toast.LENGTH_LONG).show()
        isMeetingSaved = false // Reset flag
    }
    
    private fun notifyConflictingHost(
        newMeeting: Meeting, 
        conflictingMeeting: Meeting, 
        hostName: String,
        schedulerId: String
    ) {
        // Get current user's name
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.uid != schedulerId) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { userDoc ->
                    val currentUserName = userDoc.getString("name") ?: "Unknown User"
                    
                    // Send notification to the conflicting host
                    val message = "Your meeting '${conflictingMeeting.title}' has a conflict. ${currentUserName} (higher authority) has force scheduled a meeting '${newMeeting.title}' during the same time slot for the same participants. Please reschedule or cancel your meeting."
                    val notification = hashMapOf(
                        "recipientUid" to conflictingMeeting.scheduledBy,
                        "message" to message,
                        "createdAt" to Timestamp.now(),
                        "read" to false
                    )
                    db.collection("notifications").add(notification)
                }
        }
    }
    
    private fun detectAndNotifyCollisions(newMeeting: Meeting) {
        // This method is no longer needed as we're handling conflicts before scheduling
        // Keeping it for backward compatibility but it's empty now
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

    companion object {
        private const val SELECT_ATTENDEES_REQUEST_CODE = 1001
    }
}