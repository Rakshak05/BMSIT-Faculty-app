package com.bmsit.faculty

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import java.util.Calendar
import com.bmsit.faculty.Meeting

class ProfileActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var targetUserId: String? = null
    private var isViewingOtherUser: Boolean = false
    private var isEditMode: Boolean = false
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle

    // Editable fields
    private lateinit var spinnerDepartment: Spinner
    private lateinit var spinnerDesignation: Spinner
    private lateinit var btnSaveChanges: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Initialize drawer layout and navigation view
        drawerLayout = findViewById(R.id.drawer_layout_profile)
        val toolbar: Toolbar = findViewById(R.id.toolbar_profile)
        setSupportActionBar(toolbar)
        
        navigationView = findViewById(R.id.nav_view_profile)
        navigationView.setNavigationItemSelectedListener(this)
        
        // Setup ActionBarDrawerToggle
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        // Enable the hamburger icon
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setHomeButtonEnabled(true)
        
        // Setup the navigation menu based on user role
        setupNavigationMenuBasedOnUser()
        
        // Set the profile item as checked when the activity is created
        navigationView.setCheckedItem(R.id.nav_profile)
        
        // Initialize editable fields
        spinnerDepartment = findViewById(R.id.spinnerDepartment)
        spinnerDesignation = findViewById(R.id.spinnerDesignation)
        btnSaveChanges = findViewById(R.id.btn_save_changes)
        
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
        
        // Check if we're in edit mode (admin editing faculty)
        isEditMode = intent.getBooleanExtra("IS_EDIT_MODE", false)
        
        // Initialize UI elements to empty values to prevent showing hardcoded defaults
        initializeUI()
        
        setupViews()
        loadUserData()
        
        // Configure UI based on edit mode
        configureEditMode()
    }

    private fun initializeUI() {
        // Clear all profile fields to prevent showing hardcoded default values
        findViewById<TextView>(R.id.tvAvatarInitials).text = ""
        findViewById<TextView>(R.id.tvProfileNameHeader).text = ""
        findViewById<TextView>(R.id.tvDisplayName).text = ""
        findViewById<TextView>(R.id.tvAccount).text = ""
        findViewById<TextView>(R.id.tvDepartment).text = ""
        findViewById<TextView>(R.id.tvJobTitle).text = ""
        findViewById<TextView>(R.id.tvManager).text = ""
        findViewById<TextView>(R.id.tvMeetingsAttended).text = "0"
        findViewById<TextView>(R.id.tvMeetingsMissed).text = "0"
        findViewById<TextView>(R.id.tvTotalHoursAttended).text = "00:00 hrs"
    }

    private fun setupViews() {
        // Sign Out Button
        findViewById<Button>(R.id.buttonSignOut).setOnClickListener {
            auth.signOut()
            // Go back to the Login screen
            val intent = Intent(this, LoginActivity::class.java)
            // These flags prevent the user from going back to the main activity after logging out
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        
        // Change Password Button
        findViewById<Button>(R.id.buttonChangePassword).setOnClickListener {
            showChangePasswordDialog()
        }
        
        // Add click listener for editable field (only contact number)
        // Only allow editing if viewing current user's profile
        if (!isViewingOtherUser) {
            findViewById<View>(R.id.tvDepartment).setOnClickListener {
                showEditDialog("Contact No.", findViewById<TextView>(R.id.tvDepartment).text.toString()) { newValue ->
                    updateUserData("phoneNumber", newValue)
                }
            }
            
            // Add click listener for name editing
            findViewById<View>(R.id.tvDisplayName).setOnClickListener {
                showEditDialog("Display Name", findViewById<TextView>(R.id.tvDisplayName).text.toString()) { newValue ->
                    updateUserData("name", newValue)
                }
            }
        }
        
        // Add long press listeners for copying contact info
        setupCopyListeners()
        
        // Add click listeners for activity rows (using parent LinearLayouts for better click area)
        // Also add click listener directly on the TextViews as a fallback
        val attendedRow = findViewById<View>(R.id.tvMeetingsAttended).parent as? View
        attendedRow?.setOnClickListener {
            if (targetUserId != null) {
                val intent = Intent(this, AttendedMeetingsActivity::class.java)
                intent.putExtra("TARGET_USER_ID", targetUserId)
                startActivity(intent)
            }
        }
        
        // Add click listener directly on the TextView as well
        findViewById<View>(R.id.tvMeetingsAttended).setOnClickListener {
            if (targetUserId != null) {
                val intent = Intent(this, AttendedMeetingsActivity::class.java)
                intent.putExtra("TARGET_USER_ID", targetUserId)
                startActivity(intent)
            }
        }
        
        val missedRow = findViewById<View>(R.id.tvMeetingsMissed).parent as? View
        missedRow?.setOnClickListener {
            if (targetUserId != null) {
                val intent = Intent(this, MissedMeetingsActivity::class.java)
                intent.putExtra("TARGET_USER_ID", targetUserId)
                startActivity(intent)
            }
        }
        
        // Add click listener directly on the TextView as well
        findViewById<View>(R.id.tvMeetingsMissed).setOnClickListener {
            if (targetUserId != null) {
                val intent = Intent(this, MissedMeetingsActivity::class.java)
                intent.putExtra("TARGET_USER_ID", targetUserId)
                startActivity(intent)
            }
        }
        
        // Save button logic
        btnSaveChanges.setOnClickListener {
            saveChanges()
        }
        
        // Hide sign out and change password buttons when viewing another user's profile
        if (isViewingOtherUser) {
            findViewById<Button>(R.id.buttonSignOut).visibility = View.GONE
            findViewById<Button>(R.id.buttonChangePassword).visibility = View.GONE
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        
        val oldPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.etOldPassword)
        val newPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPassword)
        
        val oldPasswordLayout = dialogView.findViewById<TextInputLayout>(R.id.tilOldPassword)
        val newPasswordLayout = dialogView.findViewById<TextInputLayout>(R.id.tilNewPassword)
        val confirmPasswordLayout = dialogView.findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Change", null) // We'll override this to prevent auto-dismiss
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val oldPassword = oldPasswordInput.text.toString().trim()
                val newPassword = newPasswordInput.text.toString().trim()
                val confirmPassword = confirmPasswordInput.text.toString().trim()
                
                // Reset errors
                oldPasswordLayout.error = null
                newPasswordLayout.error = null
                confirmPasswordLayout.error = null
                
                var isValid = true
                
                // Validate inputs
                if (oldPassword.isEmpty()) {
                    oldPasswordLayout.error = "Old password is required"
                    isValid = false
                }
                
                if (newPassword.isEmpty()) {
                    newPasswordLayout.error = "New password is required"
                    isValid = false
                } else if (newPassword.length < 6) {
                    newPasswordLayout.error = "Password must be at least 6 characters"
                    isValid = false
                }
                
                if (confirmPassword.isEmpty()) {
                    confirmPasswordLayout.error = "Please confirm your new password"
                    isValid = false
                } else if (newPassword != confirmPassword) {
                    confirmPasswordLayout.error = "Passwords do not match"
                    isValid = false
                }
                
                if (isValid) {
                    changePassword(oldPassword, newPassword, dialog)
                }
            }
        }
        
        dialog.show()
    }
    
    private fun changePassword(oldPassword: String, newPassword: String, dialog: AlertDialog) {
        val user = auth.currentUser
        if (user != null && user.email != null) {
            // First re-authenticate the user with their old password
            val credential = EmailAuthProvider.getCredential(user.email!!, oldPassword)
            
            user.reauthenticate(credential)
                .addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        // Re-authentication successful, now update the password
                        user.updatePassword(newPassword)
                            .addOnCompleteListener { updateTask ->
                                if (updateTask.isSuccessful) {
                                    Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                } else {
                                    Toast.makeText(this, "Failed to update password: ${updateTask.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        Toast.makeText(this, "Authentication failed. Please check your old password.", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureEditMode() {
        if (isEditMode) {
            // Enable editing for department and designation
            findViewById<View>(R.id.layoutDepartmentEdit).visibility = View.VISIBLE
            findViewById<View>(R.id.layoutDesignationEdit).visibility = View.VISIBLE
            
            // Hide the static rows for department and designation
            findViewById<View>(R.id.layoutDepartmentDisplay).visibility = View.GONE
            findViewById<View>(R.id.layoutDesignationDisplay).visibility = View.GONE
            
            // Show save button
            btnSaveChanges.visibility = View.VISIBLE
            
            // Disable editing of other fields
            findViewById<View>(R.id.tvDepartment).setOnClickListener(null)
        } else {
            // Keep normal view mode
            findViewById<View>(R.id.layoutDepartmentEdit).visibility = View.GONE
            findViewById<View>(R.id.layoutDesignationEdit).visibility = View.GONE
            
            // Show the static rows for department and designation
            findViewById<View>(R.id.layoutDepartmentDisplay).visibility = View.VISIBLE
            findViewById<View>(R.id.layoutDesignationDisplay).visibility = View.VISIBLE
            
            // Hide save button
            btnSaveChanges.visibility = View.GONE
        }
    }

    private fun setupRow() {
        // This method is no longer used as we've updated the layout structure
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
                        findViewById<TextView>(R.id.tvDisplayName).text = displayName
                        findViewById<TextView>(R.id.tvAccount).text = document.getString("email") ?: "Not set"
                        
                        // Update additional user data from Firestore
                        val phoneNumber = document.getString("phoneNumber")
                        // Format phone number for display
                        val displayPhoneNumber = if (phoneNumber.isNullOrBlank() || phoneNumber == "Not set") {
                            "+91 "
                        } else {
                            formatPhoneNumberForDisplay(phoneNumber)
                        }
                        findViewById<TextView>(R.id.tvDepartment).text = displayPhoneNumber
                        
                        val department = document.getString("department")
                        val designation = document.getString("designation")
                        
                        findViewById<TextView>(R.id.tvJobTitle).text = department ?: "Not set"
                        findViewById<TextView>(R.id.tvManager).text = designation ?: "Not set"
                        
                        // If in edit mode, populate the editable fields
                        if (isEditMode) {
                            // Setup department spinner with custom right-aligned layout
                            // Updated to include all departments in alphabetical order
                            val departments = arrayOf("AIML", "Civil", "CS", "CSBS", "ECE", "EEE", "ETE", "ISE", "MECH", "Unassigned")
                            val deptAdapter = ArrayAdapter(this, R.layout.spinner_item_right_aligned, departments)
                            deptAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerDepartment.adapter = deptAdapter
                            spinnerDepartment.setSelection(departments.indexOf(department ?: "Unassigned").coerceAtLeast(0))
                            
                            // Setup designation spinner with custom right-aligned layout
                            val designations = arrayOf("HOD", "Associate Professor", "Assistant Professor", "Lab Assistant", "HOD's Assistant", "Unassigned")
                            val desigAdapter = ArrayAdapter(this, R.layout.spinner_item_right_aligned, designations)
                            desigAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerDesignation.adapter = desigAdapter
                            spinnerDesignation.setSelection(designations.indexOf(designation ?: "Unassigned").coerceAtLeast(0))
                        }
                        
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
    
    private fun updateRowValue() {
        // This method is no longer used as we've updated the layout structure
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
        val displayValue = if (fieldLabel == "Contact No." && (currentValue == "Not set" || currentValue.isBlank())) {
            "+91 "
        } else if (fieldLabel == "Contact No.") {
            // Format the phone number for display
            formatPhoneNumberForDisplay(currentValue)
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
                    // Format the phone number as the user types
                    val formattedText = formatPhoneNumberInput(currentText)
                    
                    // Update the text if it's different
                    if (formattedText != currentText) {
                        input.setText(formattedText)
                        input.setSelection(formattedText.length)
                    }
                }
            })
        }
        
        builder.setView(input)
        
        builder.setPositiveButton("Save") { _, _ ->
            val newValue = input.text.toString().trim()
            if (newValue.isNotEmpty()) {
                // For phone numbers, ensure it starts with +91 and has exactly 10 digits after it
                if (fieldLabel == "Contact No.") {
                    val cleanedNumber = newValue.replace(Regex("[^0-9+]"), "")
                    
                    // Handle the case where user just types digits without +91
                    val formattedNumber = if (cleanedNumber.startsWith("+91")) {
                        "+91${cleanedNumber.substring(3)}"
                    } else if (cleanedNumber.startsWith("91") && cleanedNumber.length > 2) {
                        "+91${cleanedNumber.substring(2)}"
                    } else if (cleanedNumber.startsWith("+") && cleanedNumber.length > 1) {
                        "+91${cleanedNumber.substring(1)}"
                    } else {
                        "+91$cleanedNumber"
                    }
                    
                    // Extract just the digits after +91
                    val digitsAfterCode = formattedNumber.substring(3)
                    
                    // Validate that we have exactly 10 digits
                    if (digitsAfterCode.length != 10) {
                        Toast.makeText(this, "Phone number must have exactly 10 digits after +91", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    // Final formatted number with proper spacing
                    val finalFormattedNumber = "+91 ${digitsAfterCode}"
                    onSave(finalFormattedNumber)
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
    
    // Helper function to format phone number for display
    private fun formatPhoneNumberForDisplay(phoneNumber: String): String {
        // If it's already in the correct format, return as is
        if (phoneNumber.matches(Regex("\\+91 \\d{10}"))) {
            return phoneNumber
        }
        
        // Remove all non-digit characters except +
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
        
        // Extract the digits after +91
        return if (cleaned.startsWith("+91") && cleaned.length >= 3) {
            val digits = cleaned.substring(3)
            if (digits.length >= 10) {
                "+91 ${digits.substring(0, 10)}"
            } else {
                "+91 $digits"
            }
        } else if (cleaned.startsWith("91") && cleaned.length >= 2) {
            val digits = cleaned.substring(2)
            if (digits.length >= 10) {
                "+91 ${digits.substring(0, 10)}"
            } else {
                "+91 $digits"
            }
        } else if (cleaned.startsWith("+") && cleaned.length >= 1) {
            val digits = cleaned.substring(1)
            if (digits.length >= 10) {
                "+91 ${digits.substring(0, 10)}"
            } else {
                "+91 $digits"
            }
        } else {
            if (cleaned.length >= 10) {
                "+91 ${cleaned.substring(0, 10)}"
            } else {
                "+91 $cleaned"
            }
        }
    }
    
    // Helper function to format phone number as user types
    private fun formatPhoneNumberInput(input: String): String {
        // Remove all non-digit characters except +
        val cleaned = input.replace(Regex("[^0-9+]"), "")
        
        // Handle the +91 prefix
        return if (cleaned.startsWith("+91")) {
            val digits = cleaned.substring(3)
            if (digits.isNotEmpty()) {
                "+91 ${digits.take(10)}"
            } else {
                "+91 "
            }
        } else if (cleaned.startsWith("91")) {
            val digits = cleaned.substring(2)
            if (digits.isNotEmpty()) {
                "+91 ${digits.take(10)}"
            } else {
                "+91 "
            }
        } else if (cleaned.startsWith("+")) {
            val digits = cleaned.substring(1)
            if (digits.isNotEmpty()) {
                "+91 ${digits.take(10)}"
            } else {
                "+91 "
            }
        } else {
            if (cleaned.isNotEmpty()) {
                "+91 ${cleaned.take(10)}"
            } else {
                "+91 "
            }
        }
    }
    
    private fun updateUserData(field: String, value: String) {
        if (targetUserId != null) {
            val updates = hashMapOf<String, Any>(field to value)
            
            db.collection("users").document(targetUserId!!).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    // Update the specific row
                    when (field) {
                        "phoneNumber" -> findViewById<TextView>(R.id.tvDepartment).text = value
                        "name" -> {
                            findViewById<TextView>(R.id.tvDisplayName).text = value
                            findViewById<TextView>(R.id.tvProfileNameHeader).text = value
                            findViewById<TextView>(R.id.tvAvatarInitials).text = getUserInitials(value)
                            
                            // Update navigation header
                            val headerView = navigationView.getHeaderView(0)
                            val headerGreeting = headerView.findViewById<TextView>(R.id.textViewHeaderGreeting)
                            if (headerGreeting != null) {
                                headerGreeting.text = "Welcome back, $value"
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileActivity", "Error updating profile", exception)
                    Toast.makeText(this, "Error updating profile: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveChanges() {
        if (targetUserId != null) {
            val newDept = spinnerDepartment.selectedItem.toString().trim()
            val newDesg = spinnerDesignation.selectedItem.toString().trim()
            
            // Validate input
            if (newDept.isEmpty()) {
                Toast.makeText(this, "Department cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (newDesg.isEmpty()) {
                Toast.makeText(this, "Designation cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }
            
            val updates = hashMapOf<String, Any>(
                "department" to newDept,
                "designation" to newDesg
            )
            
            db.collection("users").document(targetUserId!!).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    // Update the display rows
                    findViewById<TextView>(R.id.tvJobTitle).text = newDept
                    findViewById<TextView>(R.id.tvManager).text = newDesg
                    
                    // Set result to indicate success
                    setResult(RESULT_OK)
                    finish()
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
                var totalMeetingMinutes = 0L
                
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
                                // Calculate meeting duration in minutes
                                val durationMillis = meeting.endTime!!.toDate().time - meeting.dateTime.toDate().time
                                val durationMinutes = durationMillis / (1000 * 60)
                                totalMeetingMinutes += durationMinutes
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
                findViewById<TextView>(R.id.tvMeetingsAttended).text = meetingsAttended.toString()
                findViewById<TextView>(R.id.tvMeetingsMissed).text = meetingsMissed.toString()
                
                // Convert total minutes to hours and minutes format (HH:MM hrs)
                val hours = totalMeetingMinutes / 60
                val minutes = totalMeetingMinutes % 60
                findViewById<TextView>(R.id.tvTotalHoursAttended).text = String.format("%02d:%02d hrs", hours, minutes)
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileActivity", "Error fetching meetings: ", exception)
                findViewById<TextView>(R.id.tvMeetingsAttended).text = "0"
                findViewById<TextView>(R.id.tvMeetingsMissed).text = "0"
                findViewById<TextView>(R.id.tvTotalHoursAttended).text = "00:00 hrs"
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
        // Add long click listener for email (tvAccount)
        findViewById<View>(R.id.tvAccount).setOnLongClickListener {
            val emailValue = findViewById<TextView>(R.id.tvAccount).text.toString()
            if (emailValue.isNotEmpty() && emailValue != "Not set") {
                copyToClipboard("Email", emailValue)
                true // Consumed the long click
            } else {
                false // Did not consume the long click
            }
        }
        
        // Add long click listener for phone number (tvDepartment)
        findViewById<View>(R.id.tvDepartment).setOnLongClickListener {
            val phoneValue = findViewById<TextView>(R.id.tvDepartment).text.toString()
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
    
    // Setup navigation menu based on user role (copied from MainActivity)
    private fun setupNavigationMenuBasedOnUser() {
        try {
            val currentUser = auth.currentUser
            val facultyMembersMenuItem = navigationView.menu.findItem(R.id.nav_admin)
            val downloadCSVMenuItem = navigationView.menu.findItem(R.id.nav_download_csv)
            val addFacultyMenuItem = navigationView.menu.findItem(R.id.nav_add_faculty) // Add this line
            val profileMenuItem = navigationView.menu.findItem(R.id.nav_profile)
            
            val headerView = navigationView.getHeaderView(0)
            val headerGreeting = headerView.findViewById<TextView>(R.id.textViewHeaderGreeting)
            val headerProfileImage = headerView.findViewById<ImageView>(R.id.imageViewHeaderProfile)

            if (currentUser != null) {
                db.collection("users").document(currentUser.uid).get()
                    .addOnSuccessListener { document ->
                        try {
                            if (document != null && document.exists()) {
                                val userDesignation = document.getString("designation")
                                // Show faculty members panel only to HODs and HOD's Assistants
                                facultyMembersMenuItem.isVisible = (userDesignation == "HOD" || userDesignation == "HOD'S ASSISTANT")
                                
                                // Show download CSV option only to HODs and HOD's Assistants
                                downloadCSVMenuItem.isVisible = (userDesignation == "HOD" || userDesignation == "HOD'S ASSISTANT")
                                
                                // Show add faculty option only to ADMIN users
                                addFacultyMenuItem?.isVisible = (userDesignation == "ADMIN") // Add this line
                                
                                // Profile is always visible
                                profileMenuItem?.isVisible = true
                                
                                // Set header greeting from Firestore name or Google displayName
                                val nameFromDb = document.getString("name")
                                val fallbackName = currentUser.displayName
                                val name = (nameFromDb ?: fallbackName ?: "").trim()
                                if (name.isNotBlank()) {
                                    // Greeting with full name (including surname)
                                    headerGreeting?.text = "Welcome back, $name"
                                } else {
                                    headerGreeting?.text = "Welcome back,"
                                }
                                
                                // Load profile picture
                                loadProfilePicture(currentUser.uid, headerProfileImage, name)
                                
                                Log.d("ProfileActivity", "User menu setup completed")
                            } else {
                                // Hide faculty members panel for users without proper designation
                                facultyMembersMenuItem.isVisible = false
                                downloadCSVMenuItem.isVisible = false
                                addFacultyMenuItem?.isVisible = false // Add this line
                                profileMenuItem?.isVisible = true
                                val displayName = currentUser.displayName?.trim()
                                if (!displayName.isNullOrBlank()) {
                                    headerGreeting?.text = "Welcome back, $displayName"
                                } else {
                                    headerGreeting?.text = "Welcome back,"
                                }
                                
                                // Load profile picture
                                loadProfilePicture(currentUser.uid, headerProfileImage, "U")
                                
                                Log.d("ProfileActivity", "User document not found, using default menu setup")
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileActivity", "Error processing user document", e)
                            // Hide faculty members panel on error
                            facultyMembersMenuItem.isVisible = false
                            downloadCSVMenuItem.isVisible = false
                            addFacultyMenuItem?.isVisible = false // Add this line
                            profileMenuItem?.isVisible = true
                        }
                    }
                    .addOnFailureListener { exception ->
                        try {
                            // Hide faculty members panel on failure
                            Log.w("ProfileActivity", "Failed to fetch user data, hiding faculty members menu")
                            facultyMembersMenuItem.isVisible = false
                            downloadCSVMenuItem.isVisible = false
                            addFacultyMenuItem?.isVisible = false // Add this line
                            profileMenuItem?.isVisible = true
                        } catch (e: Exception) {
                            Log.e("ProfileActivity", "Error in failure handler", e)
                        }
                    }
            } else {
                // Hide faculty members panel for non-logged in users
                facultyMembersMenuItem.isVisible = false
                downloadCSVMenuItem.isVisible = false
                addFacultyMenuItem?.isVisible = false // Add this line
                profileMenuItem?.isVisible = true
                headerGreeting?.text = "Welcome back,"
                
                Log.d("ProfileActivity", "No current user, using default menu setup")
            }
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error in setupNavigationMenuBasedOnUser", e)
            Toast.makeText(this, "Error setting up navigation menu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Load profile picture (copied from MainActivity)
    private fun loadProfilePicture(userId: String, imageView: ImageView, userName: String) {
        try {
            // Show user initials instead of profile picture
            showUserInitials(imageView, userName)
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error loading profile picture", e)
            // Show user initials as fallback
            showUserInitials(imageView, "U")
        }
    }
    
    // Show user initials (copied from MainActivity)
    private fun showUserInitials(imageView: ImageView, userName: String) {
        try {
            // Extract initials from the user's name
            val initials = getUserInitials(userName)
            
            // Create a bitmap with the initials
            val bitmap = createInitialsBitmap(initials)
            
            // Set the bitmap to the ImageView
            imageView.setImageBitmap(bitmap)
            imageView.background = null // Remove any background
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error creating initials bitmap", e)
            // Fallback to default image if there's an error
            imageView.setImageResource(R.drawable.universalpp)
        }
    }
    
    // Create initials bitmap (copied from MainActivity)
    private fun createInitialsBitmap(initials: String): Bitmap {
        // Define the size of the bitmap (in pixels)
        val size = 200
        
        // Create a bitmap and canvas
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background (blue color) - using a fixed color instead of context
        val backgroundPaint = Paint().apply {
            color = Color.parseColor("#FF6200EE") // Using purple_500 color directly
            isAntiAlias = true
        }
        canvas.drawCircle((size / 2).toFloat(), (size / 2).toFloat(), (size / 2).toFloat(), backgroundPaint)
        
        // Draw text (initials)
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size / 2.toFloat()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        
        // Measure text to center it
        val textBounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, textBounds)
        
        // Draw the text centered
        val x = size / 2.toFloat()
        val y = (size / 2 + (textBounds.bottom - textBounds.top) / 2).toFloat()
        canvas.drawText(initials, x, y, textPaint)
        
        return bitmap
    }
    
    // Function to export all users data to CSV
    private fun exportAllUsersDataToCSV() {
        try {
            val db = FirebaseFirestore.getInstance()
            
            // Fetch all users
            db.collection("users").get()
                .addOnSuccessListener { usersResult ->
                    val csvData = StringBuilder()
                    
                    // Add CSV header
                    csvData.append("User Name,Email,Department,Designation,Meetings Attended,Meetings Missed,Total Hours (HH:MM)\n")
                    
                    // Process each user
                    var processedUsers = 0
                    val totalUsers = usersResult.size()
                    
                    if (totalUsers == 0) {
                        // Save empty CSV file
                        saveCSVFile(csvData.toString(), "all_users_data.csv")
                        return@addOnSuccessListener
                    }
                    
                    for (userDocument in usersResult) {
                        val userId = userDocument.id
                        val userName = userDocument.getString("name") ?: "Unknown User"
                        val userEmail = userDocument.getString("email") ?: ""
                        val userDepartment = userDocument.getString("department") ?: ""
                        val userDesignation = userDocument.getString("designation") ?: ""
                        
                        // Fetch meetings for this user
                        db.collection("meetings").get()
                            .addOnSuccessListener { meetingsResult ->
                                var meetingsAttended = 0
                                var meetingsMissed = 0
                                var totalMeetingMinutes = 0L
                                
                                for (meetingDocument in meetingsResult) {
                                    val meeting = meetingDocument.toObject(Meeting::class.java)
                                    
                                    // Check if the user is involved in this meeting
                                    val isUserInvolved = isUserInvolvedInMeeting(meeting, userId)
                                    
                                    // If user is involved in this meeting
                                    if (isUserInvolved) {
                                        val meetingDate = meeting.dateTime.toDate()
                                        val currentDate = Date()
                                        
                                        if (meetingDate.before(currentDate)) {
                                            // This is a past meeting (scheduled time has passed)
                                            if (meeting.endTime != null) {
                                                // Meeting was conducted, count as attended
                                                meetingsAttended++
                                                // Calculate meeting duration in minutes
                                                val durationMillis = meeting.endTime!!.toDate().time - meeting.dateTime.toDate().time
                                                val durationMinutes = durationMillis / (1000 * 60)
                                                totalMeetingMinutes += durationMinutes
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
                                    }
                                }
                                
                                // Add user data to CSV
                                val hours = totalMeetingMinutes / 60
                                val minutes = totalMeetingMinutes % 60
                                val totalHoursFormatted = String.format("%02d:%02d", hours, minutes)
                                csvData.append("${userName.replace(",", ";")},${userEmail.replace(",", ";")},${userDepartment.replace(",", ";")},${userDesignation.replace(",", ";")},$meetingsAttended,$meetingsMissed,$totalHoursFormatted\n")
                                
                                processedUsers++
                                
                                // If we've processed all users, save the CSV file
                                if (processedUsers == totalUsers) {
                                    saveCSVFile(csvData.toString(), "all_users_data.csv")
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("ProfileActivity", "Error fetching meetings for user $userId: ", exception)
                                // Still add user data without meeting stats
                                csvData.append("${userName.replace(",", ";")},${userEmail.replace(",", ";")},${userDepartment.replace(",", ";")},${userDesignation.replace(",", ";")},0,0,00:00\n")
                                
                                processedUsers++
                                
                                // If we've processed all users, save the CSV file
                                if (processedUsers == totalUsers) {
                                    saveCSVFile(csvData.toString(), "all_users_data.csv")
                                }
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileActivity", "Error fetching users for CSV export: ", exception)
                    Toast.makeText(this, "Error exporting all users data.", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error exporting all users data to CSV", e)
            Toast.makeText(this, "Error exporting all users data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Function to save CSV file
    private fun saveCSVFile(csvContent: String, fileName: String) {
        try {
            val file = java.io.File(cacheDir, fileName)
            file.writeText(csvContent)
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Download All Users Data (CSV)"))
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error saving CSV file", e)
            Toast.makeText(this, "Error saving CSV file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle navigation item selection (copied and modified from MainActivity)
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        try {
            Log.d("ProfileActivity", "onNavigationItemSelected called for item: ${item.itemId}")

            // 1. HANDLE ACTION ITEMS (Like Export CSV)
            // These should NOT change the selected state in the nav drawer
            if (item.itemId == R.id.nav_download_csv) { 
                // Perform the action
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    db.collection("users").document(currentUser.uid).get()
                        .addOnSuccessListener { document ->
                            if (document != null && document.exists()) {
                                val userDesignation = document.getString("designation")
                                // Check if user is HOD or HOD's Assistant before allowing download
                                if (userDesignation == "HOD" || userDesignation == "HOD'S ASSISTANT") {
                                    exportAllUsersDataToCSV()
                                } else {
                                    Toast.makeText(this, "Only HODs and HOD's Assistants can download faculty data.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(this, "Error: User data not found.", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(this, "Error checking user authorization: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(this, "You must be logged in to download data.", Toast.LENGTH_LONG).show()
                }
                
                // Close drawer
                drawerLayout.closeDrawer(GravityCompat.START)
                
                // RETURN FALSE: This is the fix. It tells the drawer NOT to highlight this item.
                // The previously selected item (Dashboard, Calendar, etc.) remains selected.
                return false
            }

            // 2. HANDLE NAVIGATION DESTINATIONS
            // Close drawer first to provide better UX
            drawerLayout.closeDrawer(GravityCompat.START)

            // Use a slight delay to allow drawer to close before transitioning
            Handler(Looper.getMainLooper()).postDelayed({
                when (item.itemId) {
                    R.id.nav_dashboard -> {
                        // Go back to MainActivity with Dashboard selected
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        intent.putExtra("NAVIGATE_TO", "DASHBOARD")
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_calendar -> {
                        // Go back to MainActivity with Calendar selected
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        intent.putExtra("NAVIGATE_TO", "CALENDAR")
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_profile -> {
                        // We're already on the profile page, so just close the drawer
                        // No need to navigate anywhere
                    }
                    R.id.nav_admin -> {
                        // Go back to MainActivity with Faculty Members selected
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        intent.putExtra("NAVIGATE_TO", "FACULTY_MEMBERS")
                        startActivity(intent)
                        finish()
                    }
                }
            }, 300)

            // Return true for navigation items so the touch feedback occurs
            return true

        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error in onNavigationItemSelected", e)
            Toast.makeText(this, "Error handling navigation: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}