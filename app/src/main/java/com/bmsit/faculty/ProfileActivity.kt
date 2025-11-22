package com.bmsit.faculty

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import com.bmsit.faculty.Meeting

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var targetUserId: String? = null
    private var isViewingOtherUser: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Check if we received a specific USER_ID from the intent (clicked from Faculty list)
        if (intent.hasExtra("USER_ID")) {
            targetUserId = intent.getStringExtra("USER_ID")
            isViewingOtherUser = true
        } else {
            // No ID passed? Default to the currently logged-in user
            if (auth.currentUser != null) {
                targetUserId = auth.currentUser?.uid
                isViewingOtherUser = false
            } else {
                // Handle error: User not logged in
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }
        
        setupViews()
        loadUserData()
    }

    private fun setupViews() {
        // Back Button
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Setup rows with updated labels (location removed)
        setupRow(findViewById(R.id.rowDisplayName), "Name", "Not set")
        setupRow(findViewById(R.id.rowAccount), "Mail id", "Not set")
        setupRow(findViewById(R.id.rowDepartment), "Contact No.", "Not set")
        setupRow(findViewById(R.id.rowJobTitle), "Department", "Not set")
        setupRow(findViewById(R.id.rowManager), "Designation", "Not set")
        setupRow(findViewById(R.id.rowMeetingsAttended), "Meetings Attended", "0")
        setupRow(findViewById(R.id.rowMeetingsMissed), "Meetings Missed", "0")
        
        // Add click listener for editable field (only contact number)
        // Only allow editing if viewing current user's profile
        if (!isViewingOtherUser) {
            findViewById<View>(R.id.rowDepartment).setOnClickListener {
                showEditDialog("Contact No.", getCurrentValue(R.id.rowDepartment)) { newValue ->
                    updateUserData("phoneNumber", newValue)
                }
            }
        }
        
        // Add long press listeners for copying contact info
        setupCopyListeners()
        
        // Add click listeners for activity rows
        findViewById<View>(R.id.rowMeetingsAttended).setOnClickListener {
            if (targetUserId != null) {
                val intent = Intent(this, AttendedMeetingsActivity::class.java)
                intent.putExtra("TARGET_USER_ID", targetUserId)
                startActivity(intent)
            }
        }
        
        findViewById<View>(R.id.rowMeetingsMissed).setOnClickListener {
            if (targetUserId != null) {
                val intent = Intent(this, MissedMeetingsActivity::class.java)
                intent.putExtra("TARGET_USER_ID", targetUserId)
                startActivity(intent)
            }
        }
    }

    private fun setupRow(view: View, label: String, value: String) {
        val tvLabel = view.findViewById<TextView>(R.id.tvLabel)
        val tvValue = view.findViewById<TextView>(R.id.tvValue)

        tvLabel.text = label
        tvValue.text = value
    }
    
    private fun getCurrentValue(rowId: Int): String {
        val rowView = findViewById<View>(rowId)
        val tvValue = rowView.findViewById<TextView>(R.id.tvValue)
        return tvValue.text.toString()
    }

    private fun loadUserData() {
        if (targetUserId != null) {
            // Fetch user data from Firestore (including name)
            db.collection("users").document(targetUserId!!).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Get name from Firestore, fallback to Auth display name if not in Firestore
                        val firestoreName = document.getString("name")
                        val displayName = if (!firestoreName.isNullOrBlank()) {
                            firestoreName
                        } else {
                            // If viewing another user, we don't have access to their auth info
                            if (!isViewingOtherUser && auth.currentUser != null) {
                                auth.currentUser?.displayName ?: "No Name"
                            } else {
                                "No Name"
                            }
                        }
                        
                        // Set user's display name in header and avatar
                        val headerNameTextView = findViewById<TextView>(R.id.tvProfileNameHeader)
                        val avatarInitialsTextView = findViewById<TextView>(R.id.tvAvatarInitials)
                        
                        headerNameTextView.text = displayName
                        avatarInitialsTextView.text = getUserInitials(displayName)
                        
                        // Update user info rows
                        updateRowValue(findViewById(R.id.rowDisplayName), displayName)
                        updateRowValue(findViewById(R.id.rowAccount), document.getString("email"))
                        
                        // Update additional user data from Firestore
                        val phoneNumber = document.getString("phoneNumber")
                        // Show "+91" as default if phone number is not set
                        val displayPhoneNumber = if (phoneNumber.isNullOrBlank() || phoneNumber == "Not set") {
                            "+91"
                        } else {
                            phoneNumber
                        }
                        updateRowValue(findViewById(R.id.rowDepartment), displayPhoneNumber)
                        updateRowValue(findViewById(R.id.rowJobTitle), document.getString("department"))
                        updateRowValue(findViewById(R.id.rowManager), document.getString("designation"))
                        
                        // Fetch meeting statistics
                        fetchMeetingStatistics(targetUserId!!)
                    } else {
                        Log.d("ProfileActivity", "No such document")
                        Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("ProfileActivity", "get failed with ", exception)
                    Toast.makeText(this, "Error fetching profile data.", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    private fun updateRowValue(rowView: View, value: String?) {
         rowView.findViewById<TextView>(R.id.tvValue).text = value ?: "Not set"
    }
    
    private fun getUserInitials(name: String): String {
        return try {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) return "U"
            
            val names = trimmedName.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            when (names.size) {
                0 -> "U"
                1 -> names[0].firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                else -> {
                    val firstInitial = names[0].firstOrNull()?.uppercaseChar()?.toString() ?: ""
                    val lastInitial = names[names.size - 1].firstOrNull()?.uppercaseChar()?.toString() ?: ""
                    firstInitial + lastInitial
                }
            }
        } catch (e: Exception) {
            "U"
        }
    }
    
    private fun showEditDialog(fieldLabel: String, currentValue: String, onSave: (String) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit $fieldLabel")
        
        val input = EditText(this)
        // Set the current value or default to "+91" for phone numbers
        val displayValue = if (fieldLabel == "Contact No." && currentValue == "Not set") {
            "+91"
        } else {
            currentValue
        }
        input.setText(displayValue)
        
        // For phone numbers, set input type to phone and add text watcher for validation
        if (fieldLabel == "Contact No.") {
            input.inputType = android.text.InputType.TYPE_CLASS_PHONE
            input.addTextChangedListener(object : android.text.TextWatcher {
                private var beforeText = ""
                
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    beforeText = s.toString()
                }
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: android.text.Editable?) {
                    val currentText = s.toString()
                    // Allow only digits and + sign, and ensure + is only at the beginning
                    if (currentText.isNotEmpty()) {
                        var cleanedText = currentText.replace(Regex("[^0-9+]"), "")
                        // Ensure + is only at the beginning
                        val plusCount = cleanedText.count { it == '+' }
                        if (plusCount > 1) {
                            cleanedText = cleanedText.replace("+", "")
                            if (cleanedText.isNotEmpty()) {
                                cleanedText = "+$cleanedText"
                            } else {
                                cleanedText = "+"
                            }
                        } else if (plusCount == 1 && !cleanedText.startsWith("+")) {
                            cleanedText = cleanedText.replace("+", "")
                            if (cleanedText.isNotEmpty()) {
                                cleanedText = "+$cleanedText"
                            } else {
                                cleanedText = "+"
                            }
                        }
                        
                        // Update the text if it's different
                        if (cleanedText != currentText) {
                            input.setText(cleanedText)
                            input.setSelection(cleanedText.length)
                        }
                    }
                }
            })
        }
        
        builder.setView(input)
        
        builder.setPositiveButton("Save") { _, _ ->
            val newValue = input.text.toString().trim()
            if (newValue.isNotEmpty()) {
                // For phone numbers, ensure it starts with +91 and has valid digits
                if (fieldLabel == "Contact No.") {
                    val cleanedNumber = newValue.replace(Regex("[^0-9+]"), "")
                    if (cleanedNumber.isEmpty()) {
                        Toast.makeText(this, "Phone number cannot be empty", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    // Ensure it starts with +91
                    val formattedNumber = if (cleanedNumber.startsWith("+91")) {
                        cleanedNumber
                    } else if (cleanedNumber.startsWith("91")) {
                        "+$cleanedNumber"
                    } else if (cleanedNumber.startsWith("+")) {
                        "+91${cleanedNumber.substring(1)}"
                    } else {
                        "+91$cleanedNumber"
                    }
                    
                    // Validate that we have at least 10 digits after +91
                    val digitsAfterCode = formattedNumber.substring(3)
                    if (digitsAfterCode.length < 10) {
                        Toast.makeText(this, "Phone number must have at least 10 digits", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    onSave(formattedNumber)
                } else {
                    onSave(newValue)
                }
            } else {
                Toast.makeText(this, "$fieldLabel cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        
        builder.show()
    }
    
    private fun updateUserData(field: String, value: String) {
        if (targetUserId != null) {
            val updates = hashMapOf<String, Any>(field to value)
            
            db.collection("users").document(targetUserId!!).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    // Update the specific row
                    when (field) {
                        "phoneNumber" -> updateRowValue(findViewById(R.id.rowDepartment), value)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileActivity", "Error updating profile", exception)
                    Toast.makeText(this, "Error updating profile: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    private fun fetchMeetingStatistics(userId: String) {
        // Fetch all meetings where the user is either the scheduler or an attendee
        db.collection("meetings")
            .get()
            .addOnSuccessListener { result ->
                var meetingsAttended = 0
                var meetingsMissed = 0
                
                for (document in result) {
                    val meeting = document.toObject(Meeting::class.java)
                    
                    // Check if the user is involved in this meeting
                    val isUserInvolved = isUserInvolvedInMeeting(meeting, userId)
                    
                    // If user is involved in this meeting
                    if (isUserInvolved) {
                        // Check if the meeting has already passed
                        val meetingDate = meeting.dateTime.toDate()
                        val currentDate = java.util.Date()
                        
                        if (meetingDate.before(currentDate)) {
                            // This is a past meeting (scheduled time has passed)
                            // Check if the meeting has an end time (indicating it was conducted)
                            if (meeting.endTime != null) {
                                // Meeting was conducted, count as attended
                                meetingsAttended++
                            } else {
                                // Meeting has passed but no end time is recorded
                                // Check if the current time is past the expected end time
                                val calendar = java.util.Calendar.getInstance()
                                calendar.time = meetingDate
                                // Ensure duration is valid, default to 60 minutes if not set properly
                                val duration = if (meeting.duration > 0) meeting.duration else 60
                                calendar.add(java.util.Calendar.MINUTE, duration)
                                val expectedEndTime = calendar.time
                                
                                // Only count as missed if we're past the expected end time
                                if (currentDate.after(expectedEndTime)) {
                                    meetingsMissed++
                                }
                                // If we're still within the expected meeting duration, don't count it yet
                            }
                        }
                        // For future meetings, we don't count them in either category
                        // They will be counted only after they occur
                    }
                }
                
                // Update the UI with the statistics
                updateRowValue(findViewById(R.id.rowMeetingsAttended), meetingsAttended.toString())
                updateRowValue(findViewById(R.id.rowMeetingsMissed), meetingsMissed.toString())
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileActivity", "Error fetching meetings: ", exception)
                updateRowValue(findViewById(R.id.rowMeetingsAttended), "0")
                updateRowValue(findViewById(R.id.rowMeetingsMissed), "0")
            }
    }
    
    // Function to check if a user is involved in a meeting
    private fun isUserInvolvedInMeeting(meeting: Meeting, userId: String): Boolean {
        // User scheduled the meeting
        if (meeting.scheduledBy == userId) {
            return true
        }
        
        // User is a custom attendee
        if (meeting.attendees == "Custom" && meeting.customAttendeeUids.contains(userId)) {
            return true
        }
        
        // For group meetings (All Associate Prof, All Assistant Prof, etc.), we would need to check if the user 
        // belongs to that group, but for simplicity, we'll skip this for now
        // In a production app, you would implement group membership checking here
        
        return false
    }
    
    private fun setupCopyListeners() {
        // Add long click listener for email (rowAccount)
        findViewById<View>(R.id.rowAccount).setOnLongClickListener {
            val emailValue = getCurrentValue(R.id.rowAccount)
            if (emailValue.isNotEmpty() && emailValue != "Not set") {
                copyToClipboard("Email", emailValue)
                true // Consumed the long click
            } else {
                false // Did not consume the long click
            }
        }
        
        // Add long click listener for phone number (rowDepartment)
        findViewById<View>(R.id.rowDepartment).setOnLongClickListener {
            val phoneValue = getCurrentValue(R.id.rowDepartment)
            if (phoneValue.isNotEmpty() && phoneValue != "Not set" && phoneValue != "+91") {
                copyToClipboard("Phone Number", phoneValue)
                true // Consumed the long click
            } else {
                false // Did not consume the long click
            }
        }
    }
    
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        
        // Show a toast to confirm the copy
        Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}