package com.bmsit.faculty

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.bmsit.faculty.databinding.ActivityScheduleMeetingBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ScheduleMeetingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleMeetingBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val selectedCalendar = Calendar.getInstance()
    private val customAttendeeUids = mutableListOf<String>()
    private val titleFrequencyMap = mutableMapOf<String, Int>()
    private val locationFrequencyMap = mutableMapOf<String, Int>()
    private val sortedTitles = mutableListOf<String>()
    private val sortedLocations = mutableListOf<String>()
    
    // Variables for editing functionality
    private var meetingId: String? = null
    private var originalMeeting: Meeting? = null
    
    companion object {
        private const val SELECT_ATTENDEES_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleMeetingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if we're editing an existing meeting
        meetingId = intent.getStringExtra("MEETING_ID")
        
        setupToolbar()
        loadPreviousMeetingsForSuggestions()
        setupDropdowns()
        setupPickers()
        setupSubmitButton()
        
        // If editing, load the existing meeting data
        if (meetingId != null) {
            loadMeetingData(meetingId!!)
            binding.toolbarSchedule.title = "Edit Meeting"
            binding.btnConfirmSchedule.text = "Update Meeting"
        } else {
            updateDateTimeDisplay()
        }
    }

    private fun setupToolbar() {
        binding.toolbarSchedule.setNavigationOnClickListener { finish() }
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
            }
            .addOnFailureListener { exception ->
                Log.e("ScheduleMeetingActivity", "Error loading previous meetings", exception)
            }
    }

    private fun loadMeetingData(id: String) {
        db.collection("meetings").document(id).get()
            .addOnSuccessListener { document ->
                val meeting = document.toObject(Meeting::class.java)
                if (meeting != null) {
                    originalMeeting = meeting

                    // Fill basic fields
                    binding.etReason.setText(meeting.title)
                    binding.etVenue.setText(meeting.location)

                    // Set calendar to meeting date/time
                    val meetingDate = meeting.dateTime.toDate()
                    selectedCalendar.time = meetingDate
                    
                    // Update display
                    updateDateTimeDisplay()

                    // Set attendees
                    binding.actParticipants.setText(meeting.attendees, false)

                    // Load custom attendee list if needed
                    if (meeting.attendees == "Custom") {
                        customAttendeeUids.clear()
                        customAttendeeUids.addAll(meeting.customAttendeeUids)
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ScheduleMeetingActivity", "Error loading meeting data", exception)
                Toast.makeText(this, "Error loading meeting data: ${exception.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun setupDropdowns() {
        // Setup Participants Dropdown
        val participantOptions = listOf("All Faculty", "Associate Professors", "Assistant Professors", "HODs Only", "Custom")
        val participantAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, participantOptions)
        binding.actParticipants.setAdapter(participantAdapter)
        
        // Handle participant selection changes
        binding.actParticipants.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
            val selected = participantOptions[position]
            if (selected == "Custom") {
                // Open the select attendees activity
                val intent = Intent(this, SelectAttendeesActivity::class.java)
                intent.putStringArrayListExtra("SELECTED_UIDS", ArrayList(customAttendeeUids))
                startActivityForResult(intent, SELECT_ATTENDEES_REQUEST_CODE)
            }
        }
    }

    private fun setupPickers() {
        // 1. DATE CLICK
        binding.cardDateSelector.setOnClickListener {
            val year = selectedCalendar.get(Calendar.YEAR)
            val month = selectedCalendar.get(Calendar.MONTH)
            val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(this, { _, y, m, d ->
                selectedCalendar.set(y, m, d)
                updateDateTimeDisplay()
            }, year, month, day)
            datePicker.datePicker.minDate = System.currentTimeMillis() - 1000
            datePicker.show()
        }

        // 2. TIME CLICK (Using standard TimePickerDialog with spinner style)
        binding.cardStartTime.setOnClickListener {
            val hour = selectedCalendar.get(Calendar.HOUR_OF_DAY)
            val minute = selectedCalendar.get(Calendar.MINUTE)

            // Create TimePickerDialog with our custom spinner theme
            val timePicker = TimePickerDialog(
                this,
                R.style.SpinnerTimePickerDialog,
                { _, hourOfDay, minute ->
                    // Update the calendar with selected time
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    selectedCalendar.set(Calendar.MINUTE, minute)
                    // Update the display immediately when time is selected
                    updateDateTimeDisplay()
                },
                hour,
                minute,
                false // is24HourView
            )
            
            // Set title for the dialog
            timePicker.setTitle("Select Time")
            timePicker.show()
        }

        // 3. DURATION CLICK (Uses a Popup Menu instead of full dropdown for cleaner look)
        binding.cardDuration.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("30 min")
            popup.menu.add("45 min")
            popup.menu.add("1 hr")
            popup.menu.add("1.5 hrs")
            popup.menu.add("2 hrs")
            popup.menu.add("3 hrs")
            
            popup.setOnMenuItemClickListener { item ->
                binding.tvDuration.text = item.title
                true
            }
            popup.show()
        }
    }

    private fun updateDateTimeDisplay() {
        // Update date display - keeping the original format you liked
        val dayFormat = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
        binding.tvSelectedDate.text = dayFormat.format(selectedCalendar.time)
        binding.tvYear.text = selectedCalendar.get(Calendar.YEAR).toString()
        
        // Update time display - keeping the original format you liked
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        binding.tvStartTime.text = timeFormat.format(selectedCalendar.time)
    }

    private fun setupSubmitButton() {
        binding.btnConfirmSchedule.setOnClickListener {
            val reason = binding.etReason.text.toString()
            val venue = binding.etVenue.text.toString()
            val participants = binding.actParticipants.text.toString()
            val duration = binding.tvDuration.text.toString()

            if (reason.isBlank() || venue.isBlank() || duration.isBlank()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if custom participants were selected but none chosen
            if (participants == "Custom" && customAttendeeUids.isEmpty()) {
                Toast.makeText(this, "Please select at least one custom attendee", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check user designation to restrict meeting scheduling to HODs, Associate Professors, and HOD's Assistants
            val schedulerId = auth.currentUser?.uid
            if (schedulerId == null) {
                Toast.makeText(this, "Error: You must be logged in.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            db.collection("users").document(schedulerId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val userDesignation = document.getString("designation")
                        // Only HODs, Associate Professors, and HOD's Assistants can schedule meetings
                        if (userDesignation != "HOD" && userDesignation != "Associate Professor" && userDesignation != "HOD'S ASSISTANT") {
                            Toast.makeText(this, "Only HODs, Associate Professors, and HOD's Assistants are authorized to schedule meetings.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                        
                        // Continue with meeting scheduling/editing if user is authorized
                        if (meetingId != null) {
                            // Editing existing meeting
                            updateMeeting(reason, venue, participants, duration)
                        } else {
                            // Creating new meeting
                            saveMeeting(reason, venue, participants, duration)
                        }
                    } else {
                        Toast.makeText(this, "Error: User data not found.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error checking user authorization: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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

    private fun saveMeeting(title: String, venue: String, group: String, duration: String) {
        binding.btnConfirmSchedule.isEnabled = false
        binding.btnConfirmSchedule.text = "Scheduling..."
        
        // Validation for Custom meetings
        if (group == "Custom" && customAttendeeUids.isEmpty()) {
            Toast.makeText(this, "Please select at least one custom attendee.", Toast.LENGTH_SHORT).show()
            binding.btnConfirmSchedule.isEnabled = true
            binding.btnConfirmSchedule.text = "Schedule Meeting"
            return
        }
        
        val schedulerId = auth.currentUser?.uid
        if (schedulerId != null) {
            // NEW: Prevent users from scheduling meetings with only themselves
            if (group == "Custom" && customAttendeeUids.size == 1 && customAttendeeUids.contains(schedulerId)) {
                Toast.makeText(this, "You cannot schedule a meeting for yourself only. Please select at least one other attendee.", Toast.LENGTH_SHORT).show()
                binding.btnConfirmSchedule.isEnabled = true
                binding.btnConfirmSchedule.text = "Schedule Meeting"
                return
            }
        }

        val calendar = Calendar.getInstance()
        calendar.set(selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH), selectedCalendar.get(Calendar.HOUR_OF_DAY), selectedCalendar.get(Calendar.MINUTE), 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Check if the selected date/time is in the past
        val currentCalendar = Calendar.getInstance()
        if (calendar.before(currentCalendar)) {
            Toast.makeText(this, "Cannot schedule meetings in the past. Please select a future date and time.", Toast.LENGTH_LONG).show()
            binding.btnConfirmSchedule.isEnabled = true
            binding.btnConfirmSchedule.text = "Schedule Meeting"
            return
        }

        val meetingTimestamp = Timestamp(calendar.time)
        val selectedDuration = when (duration) {
            "30 min" -> 30
            "45 min" -> 45
            "1 hr" -> 60
            "1.5 hrs" -> 90
            "2 hrs" -> 120
            "3 hrs" -> 180
            else -> 60 // Default to 1 hour
        }

        val meetingId = db.collection("meetings").document().id
        val newMeeting = Meeting(
            id = meetingId,
            title = title,
            location = venue,
            dateTime = meetingTimestamp,
            attendees = group,
            scheduledBy = schedulerId ?: "",
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
                        binding.btnConfirmSchedule.isEnabled = true
                        binding.btnConfirmSchedule.text = "Schedule Meeting"
                        Toast.makeText(this, "Error scheduling meeting: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                // Conflicts found, don't save and reset button
                binding.btnConfirmSchedule.isEnabled = true
                binding.btnConfirmSchedule.text = "Schedule Meeting"
            }
        }
    }
    
    private fun updateMeeting(title: String, venue: String, group: String, duration: String) {
        binding.btnConfirmSchedule.isEnabled = false
        binding.btnConfirmSchedule.text = "Updating..."
        
        val schedulerId = auth.currentUser?.uid
        if (schedulerId == null) {
            Toast.makeText(this, "Error: You must be logged in.", Toast.LENGTH_LONG).show()
            binding.btnConfirmSchedule.isEnabled = true
            binding.btnConfirmSchedule.text = "Update Meeting"
            return
        }
        
        // Validation for Custom meetings
        if (group == "Custom" && customAttendeeUids.isEmpty()) {
            Toast.makeText(this, "Please select at least one custom attendee.", Toast.LENGTH_SHORT).show()
            binding.btnConfirmSchedule.isEnabled = true
            binding.btnConfirmSchedule.text = "Update Meeting"
            return
        }
        
        // NEW: Prevent users from scheduling meetings with only themselves
        if (group == "Custom" && customAttendeeUids.size == 1 && customAttendeeUids.contains(schedulerId)) {
            Toast.makeText(this, "You cannot schedule a meeting for yourself only. Please select at least one other attendee.", Toast.LENGTH_LONG).show()
            binding.btnConfirmSchedule.isEnabled = true
            binding.btnConfirmSchedule.text = "Update Meeting"
            return
        }

        val calendar = Calendar.getInstance()
        calendar.set(selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH), selectedCalendar.get(Calendar.HOUR_OF_DAY), selectedCalendar.get(Calendar.MINUTE), 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Check if the selected date/time is in the past
        val currentCalendar = Calendar.getInstance()
        if (calendar.before(currentCalendar)) {
            Toast.makeText(this, "Cannot schedule meetings in the past. Please select a future date and time.", Toast.LENGTH_LONG).show()
            binding.btnConfirmSchedule.isEnabled = true
            binding.btnConfirmSchedule.text = "Update Meeting"
            return
        }

        val meetingTimestamp = Timestamp(calendar.time)
        val selectedDuration = when (duration) {
            "30 min" -> 30
            "45 min" -> 45
            "1 hr" -> 60
            "1.5 hrs" -> 90
            "2 hrs" -> 120
            "3 hrs" -> 180
            else -> 60 // Default to 1 hour
        }

        // Create a map of the fields to update
        val updates = mutableMapOf<String, Any>(
            "title" to title,
            "location" to venue,
            "dateTime" to meetingTimestamp,
            "attendees" to group,
            "duration" to selectedDuration
        )
        
        // If this is a custom meeting, also update the custom attendee UIDs
        if (group == "Custom") {
            updates["customAttendeeUids"] = customAttendeeUids
        }

        // Update the document in Firestore
        db.collection("meetings").document(meetingId!!).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Meeting updated successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.btnConfirmSchedule.isEnabled = true
                binding.btnConfirmSchedule.text = "Update Meeting"
                Toast.makeText(this, "Error updating meeting: ${e.message}", Toast.LENGTH_LONG).show()
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
}