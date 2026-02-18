package com.bmsit.faculty

import android.app.Activity
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
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    // Get instances of Firebase services
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    // User ID to display profile for (null for current user)
    private var targetUserId: String? = null
    private var currentUserIsAdmin: Boolean = false

    companion object {
        private const val ARG_USER_ID = "user_id"
        private const val REQUEST_IMAGE_PICK = 1001
        private const val REQUEST_IMAGE_CAPTURE = 1002
        
        fun newInstance(userId: String?): ProfileFragment {
            val fragment = ProfileFragment()
            val args = Bundle()
            args.putString(ARG_USER_ID, userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetUserId = arguments?.getString(ARG_USER_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        // Initialize Firebase Storage with the correct bucket from google-services.json
        storage = FirebaseStorage.getInstance("gs://bmsit-faculty-30834.firebasestorage.app")
        
        // Get references to the UI elements from the layout
        val imageViewProfile = view.findViewById<ImageView>(R.id.imageViewProfile)
        val textViewName = view.findViewById<TextView>(R.id.textViewProfileName)
        val textViewEmail = view.findViewById<TextView>(R.id.textViewProfileEmail)
        val textViewDepartment = view.findViewById<TextView>(R.id.textViewProfileDepartment)
        val imageViewEditDepartmentDesignation = view.findViewById<ImageView>(R.id.imageViewEditDepartmentDesignation)
        val buttonSignOut = view.findViewById<Button>(R.id.buttonSignOut)
        
        // New UI elements for Details section
        val textViewPhoneNumber = view.findViewById<TextView>(R.id.textViewPhoneNumber)
        val textViewEmailDetail = view.findViewById<TextView>(R.id.textViewEmailDetail)
        val textViewDepartmentDetail = view.findViewById<TextView>(R.id.textViewDepartmentDetail)
        val textViewDesignationDetail = view.findViewById<TextView>(R.id.textViewDesignationDetail)
        
        // New UI elements for Activity section
        val textViewMeetingsAttendedDetail = view.findViewById<TextView>(R.id.textViewMeetingsAttendedDetail)
        val textViewMeetingsMissedDetail = view.findViewById<TextView>(R.id.textViewMeetingsMissedDetail)
        val textViewTotalHoursAttendedDetail = view.findViewById<TextView>(R.id.textViewTotalHoursAttendedDetail)
        val boxMeetingsAttended = view.findViewById<LinearLayout>(R.id.boxMeetingsAttended)
        val boxMeetingsMissed = view.findViewById<LinearLayout>(R.id.boxMeetingsMissed)
        val boxTotalHoursAttended = view.findViewById<LinearLayout>(R.id.boxTotalHoursAttended)

        // Add click listener to profile picture for enlarging and edit options
        imageViewProfile.setOnClickListener {
            showEnlargedProfilePicture(imageViewProfile)
        }

        // Add click listeners for activity boxes
        boxMeetingsAttended.setOnClickListener {
            // Navigate to attended meetings activity for the target user
            val intent = Intent(activity, AttendedMeetingsActivity::class.java)
            intent.putExtra("TARGET_USER_ID", targetUserId ?: auth.currentUser?.uid)
            startActivity(intent)
        }

        boxMeetingsMissed.setOnClickListener {
            // Navigate to missed meetings activity for the target user
            val intent = Intent(activity, MissedMeetingsActivity::class.java)
            intent.putExtra("TARGET_USER_ID", targetUserId ?: auth.currentUser?.uid)
            startActivity(intent)
        }
        
        // Add click listener for total hours box (no navigation, just informational)
        boxTotalHoursAttended.setOnClickListener {
            // No action needed, just informational
        }
        
        // Set up sign out button
        buttonSignOut.setOnClickListener {
            auth.signOut()
            // Go back to the Login screen
            val intent = Intent(activity, LoginActivity::class.java)
            // These flags prevent the user from going back to the main activity after logging out
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        }
        
        // Check if we're viewing a specific user's profile or the current user's profile
        if (targetUserId != null && targetUserId != "current") {
            // Viewing another user's profile
            displayUserProfile(targetUserId!!, imageViewProfile, textViewName, textViewEmail, textViewDepartment, 
                             textViewPhoneNumber, textViewEmailDetail, textViewDepartmentDetail, textViewDesignationDetail,
                             textViewMeetingsAttendedDetail, textViewMeetingsMissedDetail, textViewTotalHoursAttendedDetail,
                             imageViewEditDepartmentDesignation)
            
            // Add long press listeners for copying contact info (only for other users' profiles)
            setupCopyListeners(textViewPhoneNumber, textViewEmailDetail)
            
            // Hide sign out button when viewing another user's profile
            buttonSignOut.visibility = View.GONE
        } else {
            // Viewing current user's profile (default behavior)
            displayCurrentUserProfile(imageViewProfile, textViewName, textViewEmail, textViewDepartment,
                                    textViewPhoneNumber, textViewEmailDetail, textViewDepartmentDetail, textViewDesignationDetail,
                                    textViewMeetingsAttendedDetail, textViewMeetingsMissedDetail, textViewTotalHoursAttendedDetail,
                                    imageViewEditDepartmentDesignation)
            
            // Show sign out button for current user
            buttonSignOut.visibility = View.VISIBLE
            
            // Removed download all users CSV button setup as it's now in the navigation header
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Removed profile picture handling code as we're no longer using profile pictures
    }
    
    private fun displayCurrentUserProfile(
        imageViewProfile: ImageView,
        textViewName: TextView,
        textViewEmail: TextView,
        textViewDepartment: TextView,
        textViewPhoneNumber: TextView,
        textViewEmailDetail: TextView,
        textViewDepartmentDetail: TextView,
        textViewDesignationDetail: TextView,
        textViewMeetingsAttendedDetail: TextView,
        textViewMeetingsMissedDetail: TextView,
        textViewTotalHoursAttendedDetail: TextView,
        imageViewEditDepartmentDesignation: ImageView
    ) {
        // --- Fetch Current User Data ---
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Get the user's unique ID
            val uid = currentUser.uid
            // Create a reference to this user's document in the 'users' collection
            val userDocRef = db.collection("users").document(uid)

            // Fetch the document from Firestore
            userDocRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // If the document exists, get the data
                        val name = document.getString("name") ?: "No Name"
                        val email = document.getString("email") ?: "No Email"
                        val department = document.getString("department") ?: "No Department"
                        val designation = document.getString("designation") ?: "Unassigned"
                        val phoneNumber = document.getString("phoneNumber") ?: "Not Provided"

                        // Set the data into our TextViews
                        textViewName.text = name
                        textViewEmail.text = email
                        // Show designation next to department
                        textViewDepartment.text = "$department • $designation"
                        
                        // Populate Details section with formatted phone number
                        textViewPhoneNumber.text = formatPhoneNumberForDisplay(phoneNumber)
                        textViewEmailDetail.text = email
                        textViewDepartmentDetail.text = department
                        textViewDesignationDetail.text = designation
                        
                        // Show user initials instead of profile picture
                        showUserInitials(imageViewProfile, name)
                        
                        // Make the edit icon visible and set up click listener for name editing
                        imageViewEditDepartmentDesignation.visibility = View.VISIBLE
                        imageViewEditDepartmentDesignation.setOnClickListener {
                            showEditNameDialog(uid, name, textViewName)
                        }
                    } else {
                        Log.d("ProfileFragment", "No such document")
                        Toast.makeText(activity, "Profile not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("ProfileFragment", "get failed with ", exception)
                    Toast.makeText(activity, "Error fetching profile.", Toast.LENGTH_SHORT).show()
                }
        } else {
            // This case should not happen if user is logged in, but it's good practice
            textViewName.text = "Not Logged In"
        }
    }
    
    private fun showEditNameDialog(userId: String, currentName: String, textViewName: TextView) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_name, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editTextUserName)
        nameEditText.setText(currentName)
        
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle("Edit Display Name")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { dialog, which ->
                val newName = nameEditText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    updateUserName(userId, newName, textViewName)
                } else if (newName.isEmpty()) {
                    Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    
    private fun updateUserName(userId: String, newName: String, textViewName: TextView) {
        val updates = hashMapOf<String, Any>("name" to newName)
        
        db.collection("users").document(userId).update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Name updated successfully", Toast.LENGTH_SHORT).show()
                // Update the displayed name
                textViewName.text = newName
                
                // Update the main activity's navigation header if this is the current user
                val activity = requireActivity()
                if (activity is MainActivity) {
                    activity.updateUserNameInNavigation(newName)
                }
                
                // Also update the department text view to show the new name
                val textViewDepartment = view?.findViewById<TextView>(R.id.textViewProfileDepartment)
                val textViewEmail = view?.findViewById<TextView>(R.id.textViewProfileEmail)
                
                // Refresh the profile data to ensure consistency
                if (textViewDepartment != null && textViewEmail != null) {
                    // The department text view shows department • designation, so we need to fetch fresh data
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { document ->
                            if (document != null && document.exists()) {
                                val department = document.getString("department") ?: "No Department"
                                val designation = document.getString("designation") ?: "Unassigned"
                                val email = document.getString("email") ?: "No Email"
                                
                                textViewDepartment.text = "$department • $designation"
                                textViewEmail.text = email
                            }
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileFragment", "Error updating name", exception)
                Toast.makeText(context, "Error updating name: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun displayUserProfile(
        userId: String,
        imageViewProfile: ImageView,
        textViewName: TextView,
        textViewEmail: TextView,
        textViewDepartment: TextView,
        textViewPhoneNumber: TextView,
        textViewEmailDetail: TextView,
        textViewDepartmentDetail: TextView,
        textViewDesignationDetail: TextView,
        textViewMeetingsAttendedDetail: TextView,
        textViewMeetingsMissedDetail: TextView,
        textViewTotalHoursAttendedDetail: TextView,
        imageViewEditDepartmentDesignation: ImageView
    ) {
        // Create a reference to the target user's document in the 'users' collection
        val userDocRef = db.collection("users").document(userId)

        // Fetch the document from Firestore
        userDocRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // If the document exists, get the data
                    val name = document.getString("name") ?: "No Name"
                    val email = document.getString("email") ?: "No Email"
                    val department = document.getString("department") ?: "No Department"
                    val designation = document.getString("designation") ?: "Unassigned"
                    val phoneNumber = document.getString("phoneNumber") ?: "Not Provided"

                    // Set the data into our TextViews
                    textViewName.text = name
                    textViewEmail.text = email
                    // Show designation next to department
                    textViewDepartment.text = "$department • $designation"
                    
                    // Populate Details section with formatted phone number
                    textViewPhoneNumber.text = formatPhoneNumberForDisplay(phoneNumber)
                    textViewEmailDetail.text = email
                    textViewDepartmentDetail.text = department
                    textViewDesignationDetail.text = designation
                    
                    // Show user initials instead of profile picture
                    showUserInitials(imageViewProfile, name)
                    
                    // Check if current user is admin to show edit icon
                    val currentUserId = auth.currentUser?.uid
                    if (currentUserId != null) {
                        checkIfUserIsAdmin(currentUserId) { isAdmin ->
                            currentUserIsAdmin = isAdmin
                            if (isAdmin) {
                                imageViewEditDepartmentDesignation.visibility = View.VISIBLE
                                imageViewEditDepartmentDesignation.setOnClickListener {
                                    showEditDepartmentDesignationDialog(userId, department, designation, textViewDepartment)
                                }
                            }
                        }
                    }
                    
                    // Fetch meeting statistics
                    fetchMeetingStatistics(userId, textViewMeetingsAttendedDetail, textViewMeetingsMissedDetail, textViewTotalHoursAttendedDetail)
                } else {
                    Log.d("ProfileFragment", "No such document for user: $userId")
                    textViewName.text = "User Not Found"
                }
            }
            .addOnFailureListener { exception ->
                Log.d("ProfileFragment", "get failed with ", exception)
                Toast.makeText(activity, "Error fetching profile.", Toast.LENGTH_SHORT).show()
            }
    }
    
    // Helper function to format phone number for display
    private fun formatPhoneNumberForDisplay(phoneNumber: String): String {
        // If it's already in the correct format, return as is
        if (phoneNumber.matches(Regex("\\+91 \\d{10}"))) {
            return phoneNumber
        }
        
        // Handle special cases
        if (phoneNumber == "Not Provided" || phoneNumber.isBlank()) {
            return "+91 "
        }
        
        // Remove all non-digit characters except +
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
        
        // Extract the digits after +91
        return if (cleaned.startsWith("+91") && cleaned.length >= 3) {
            val digits = cleaned.substring(3)
            if (digits.length >= 10) {
                // Take only first 10 digits
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
            Log.e("ProfileFragment", "Error creating initials bitmap", e)
            // Fallback to default image if there's an error
            imageView.setImageResource(R.drawable.universalpp)
        }
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
            Log.e("ProfileFragment", "Error extracting initials from name: $name", e)
            "U"
        }
    }
    
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
    
    private fun checkIfUserIsAdmin(userId: String, callback: (Boolean) -> Unit) {
        try {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val userDesignation = document.getString("designation")
                        // Make admin panel accessible to ADMIN users
                        val isAdmin = (userDesignation == "ADMIN" || userDesignation == "HOD")
                        callback(isAdmin)
                    } else {
                        callback(false)
                    }
                }
                .addOnFailureListener {
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error checking admin status", e)
            callback(false)
        }
    }
    
    private fun showEditDepartmentDesignationDialog(
        userId: String,
        currentDepartment: String,
        currentDesignation: String,
        textViewDepartment: TextView
    ) {
        // Only allow editing if current user is admin
        if (!currentUserIsAdmin) {
            Toast.makeText(context, "Only administrators can edit user information.", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_user, null)

            val nameEditText = dialogView.findViewById<EditText>(R.id.editTextUserName)
            val phoneEditText = dialogView.findViewById<EditText>(R.id.editTextPhone)
            val departmentSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDepartment)
            val designationSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDesignation)

            // --- UPDATED: New list of departments ---
            // Updated to include all departments in alphabetical order
            val departments = arrayOf("AIML", "Civil", "CS", "CSBS", "ECE", "EEE", "ETE", "ISE", "MECH", "Unassigned")
            
            // For future reference, other designations were:
            // "ADMIN", "Others", "Unassigned"
            // Order by authority level (high -> low), then include Others and Unassigned at end
            val designations = arrayOf("HOD", "Associate Professor", "Assistant Professor", "Lab Assistant", "HOD's Assistant", "Unassigned")

            departmentSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, departments)
            designationSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, designations)

            // Show name field for editing
            nameEditText.visibility = View.VISIBLE
            val nameLabel = dialogView.findViewById<TextView>(R.id.textViewNameLabel)
            nameLabel.visibility = View.VISIBLE

            // Fetch current user data to populate the fields
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name") ?: ""
                        val phoneNumber = document.getString("phoneNumber") ?: ""
                        
                        nameEditText.setText(name)
                        // Format the phone number for display in the edit field
                        phoneEditText.setText(formatPhoneNumberForDisplay(phoneNumber))
                    }
                }
                .addOnFailureListener { 
                    // If we can't fetch the data, leave the fields empty with default format
                    phoneEditText.setText("+91 ")
                }
            
            departmentSpinner.setSelection(departments.indexOf(currentDepartment).coerceAtLeast(0))
            designationSpinner.setSelection(designations.indexOf(currentDesignation).coerceAtLeast(0))
            
            // Add text watcher to format phone number as user types
            phoneEditText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: android.text.Editable?) {
                    val currentText = s.toString()
                    // Format the phone number as the user types
                    val formattedText = formatPhoneNumberInput(currentText)
                    
                    // Update the text if it's different
                    if (formattedText != currentText) {
                        phoneEditText.setText(formattedText)
                        phoneEditText.setSelection(formattedText.length)
                    }
                }
            })

            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setTitle("Edit User Details")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update") { dialog, which ->
                    try {
                        val newName = nameEditText.text.toString().trim()
                        val newDepartment = departmentSpinner.selectedItem.toString()
                        val newDesignation = designationSpinner.selectedItem.toString()
                        val newPhoneNumber = phoneEditText.text.toString().trim()

                        // Validate name
                        if (newName.isEmpty()) {
                            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        val updates = mutableMapOf<String, Any>(
                            "name" to newName,
                            "department" to newDepartment,
                            "designation" to newDesignation
                        )
                        
                        // Only add phone number to updates if it's not empty
                        if (newPhoneNumber.isNotEmpty() && newPhoneNumber != "+91 ") {
                            // Validate and format the phone number
                            val validationResult = validateAndFormatPhoneNumber(newPhoneNumber)
                            if (validationResult != null) {
                                updates["phoneNumber"] = validationResult
                            } else {
                                Toast.makeText(context, "Invalid phone number format", Toast.LENGTH_SHORT).show()
                                return@setPositiveButton
                            }
                        } else {
                            // If empty, store as empty string or remove the field
                            updates["phoneNumber"] = ""
                        }

                        db.collection("users").document(userId).update(updates)
                            .addOnSuccessListener {
                                try {
                                    Toast.makeText(context, "Profile updated successfully.", Toast.LENGTH_SHORT).show()
                                    // Update the displayed department and designation
                                    textViewDepartment.text = "$newDepartment • $newDesignation"
                                    
                                    // Update other views if this is the current user's profile
                                    val currentUser = auth.currentUser
                                    if (currentUser != null && currentUser.uid == userId) {
                                        // Update name in the main activity if needed
                                        val activity = requireActivity()
                                        if (activity is MainActivity) {
                                            activity.updateUserNameInNavigation(newName)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ProfileFragment", "Error after successful update", e)
                                    Toast.makeText(context, "Profile updated but error refreshing display: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error updating profile.", Toast.LENGTH_SHORT).show()
                                Log.w("ProfileFragment", "Error updating user document", e)
                            }

                    } catch (e: Exception) {
                        Log.e("ProfileFragment", "Error in update dialog", e)
                        Toast.makeText(context, "Error processing update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error in showEditDepartmentDesignationDialog", e)
            Toast.makeText(context, "Error showing edit dialog: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun fetchMeetingStatistics(userId: String, textViewAttended: TextView, textViewMissed: TextView, textViewTotalHours: TextView) {
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
                        val currentDate = Date()
                        
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
                                val calendar = Calendar.getInstance()
                                calendar.time = meetingDate
                                // Ensure duration is valid, default to 60 minutes if not set properly
                                val duration = if (meeting.duration > 0) meeting.duration else 60
                                calendar.add(Calendar.MINUTE, duration)
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
                textViewAttended.text = meetingsAttended.toString()
                textViewMissed.text = meetingsMissed.toString()
                
                // Convert total minutes to hours and minutes format (HH:MM hrs)
                val hours = totalMeetingMinutes / 60
                val minutes = totalMeetingMinutes % 60
                textViewTotalHours.text = String.format("%02d:%02d hrs", hours, minutes)
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileFragment", "Error fetching meetings: ", exception)
                textViewAttended.text = "0"
                textViewMissed.text = "0"
                textViewTotalHours.text = "00:00 hrs"
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
    
    private fun showEnlargedProfilePicture(profileImageView: ImageView) {
        try {
            // Create a new AlertDialog with the enlarged image
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_enlarged_profile, null)
            val enlargedImageView = dialogView.findViewById<ImageView>(R.id.imageViewEnlarged)
            val closeButton = dialogView.findViewById<ImageButton>(R.id.buttonClose)
            val editButton = dialogView.findViewById<ImageButton>(R.id.buttonEdit)
            
            // Copy the image from the profile image view
            enlargedImageView.setImageDrawable(profileImageView.drawable)
            
            val dialog = AlertDialog.Builder(requireContext(), R.style.EnlargedImageDialogTheme)
                .setView(dialogView)
                .create()
            
            // Set up close button
            closeButton.setOnClickListener {
                dialog.dismiss()
            }
            
            // Check if this is the current user's profile to show/hide edit button
            val isCurrentUser = (targetUserId == null || targetUserId == "current" || targetUserId == auth.currentUser?.uid)
            editButton.visibility = if (isCurrentUser) View.VISIBLE else View.GONE
            
            // Set up edit button - removed profile picture functionality
            editButton.setOnClickListener {
                dialog.dismiss()
                // Removed profile picture options as we're no longer using profile pictures
                Toast.makeText(context, "Profile picture functionality has been removed", Toast.LENGTH_SHORT).show()
            }
            
            // Make dialog cancelable by touch outside
            dialog.setCanceledOnTouchOutside(true)
            
            dialog.show()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error showing enlarged image", e)
            Toast.makeText(context, "Error displaying enlarged image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupCopyListeners(phoneTextView: TextView, emailTextView: TextView) {
        // Add long click listener for phone number
        phoneTextView.setOnLongClickListener {
            val phoneNumber = phoneTextView.text.toString()
            if (phoneNumber.isNotEmpty() && phoneNumber != "Not Provided") {
                copyToClipboard("Phone Number", phoneNumber)
                true // Consumed the long click
            } else {
                false // Did not consume the long click
            }
        }
        
        // Add long click listener for email
        emailTextView.setOnLongClickListener {
            val email = emailTextView.text.toString()
            if (email.isNotEmpty() && email != "No Email") {
                copyToClipboard("Email", email)
                true // Consumed the long click
            } else {
                false // Did not consume the long click
            }
        }
    }
    
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        
        // Show a toast to confirm the copy
        Toast.makeText(activity, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    // Helper function to validate and format phone number
    private fun validateAndFormatPhoneNumber(input: String): String? {
        // Remove all non-digit characters except +
        val cleaned = input.replace(Regex("[^0-9+]"), "")
        
        // Handle the +91 prefix
        val digitsAfterCode = if (cleaned.startsWith("+91") && cleaned.length >= 3) {
            cleaned.substring(3)
        } else if (cleaned.startsWith("91") && cleaned.length >= 2) {
            cleaned.substring(2)
        } else if (cleaned.startsWith("+") && cleaned.length >= 1) {
            cleaned.substring(1)
        } else {
            cleaned
        }
        
        // Validate that we have exactly 10 digits
        if (digitsAfterCode.length != 10) {
            return null // Invalid format
        }
        
        // Return formatted number with proper spacing
        return "+91 $digitsAfterCode"
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
}