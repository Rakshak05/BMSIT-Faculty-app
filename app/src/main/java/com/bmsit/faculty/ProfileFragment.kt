package com.bmsit.faculty

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
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
        val textViewMeetingsAttended = view.findViewById<TextView>(R.id.textViewMeetingsAttendedCount)
        val textViewMeetingsMissed = view.findViewById<TextView>(R.id.textViewMeetingsMissedCount)
        val textViewMinutesOfMeeting = view.findViewById<TextView>(R.id.textViewMinutesOfMeeting)
        val buttonSignOut = view.findViewById<Button>(R.id.buttonSignOut)
        val cardMeetingsAttended = view.findViewById<MaterialCardView>(R.id.cardMeetingsAttended)
        val cardMeetingsMissed = view.findViewById<MaterialCardView>(R.id.cardMeetingsMissed)

        // Add click listener to profile picture for enlarging and edit options
        imageViewProfile.setOnClickListener {
            showEnlargedProfilePicture(imageViewProfile)
        }

        // Check if we're viewing a specific user's profile or the current user's profile
        if (targetUserId != null && targetUserId != "current") {
            // Viewing another user's profile
            displayUserProfile(targetUserId!!, imageViewProfile, textViewName, textViewEmail, textViewDepartment, 
                             textViewMeetingsAttended, textViewMeetingsMissed, textViewMinutesOfMeeting, imageViewEditDepartmentDesignation)
            
            // Hide sign out button when viewing another user's profile
            buttonSignOut.visibility = View.GONE
        } else {
            // Viewing current user's profile (default behavior)
            displayCurrentUserProfile(imageViewProfile, textViewName, textViewEmail, textViewDepartment, 
                                    textViewMeetingsAttended, textViewMeetingsMissed, textViewMinutesOfMeeting, imageViewEditDepartmentDesignation)
            
            // Set up sign out button for current user
            buttonSignOut.setOnClickListener {
                auth.signOut()
                // Go back to the Login screen
                val intent = Intent(activity, LoginActivity::class.java)
                // These flags prevent the user from going back to the main activity after logging out
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                activity?.finish()
            }
            
            // Removed download all users CSV button setup as it's now in the navigation header
        }

        // Make the attended meetings card clickable
        cardMeetingsAttended.setOnClickListener {
            // Navigate to attended meetings activity for the target user
            val intent = Intent(activity, AttendedMeetingsActivity::class.java)
            intent.putExtra("TARGET_USER_ID", targetUserId ?: auth.currentUser?.uid)
            startActivity(intent)
        }

        // Make the missed meetings card clickable
        cardMeetingsMissed.setOnClickListener {
            // Navigate to missed meetings activity for the target user
            val intent = Intent(activity, MissedMeetingsActivity::class.java)
            intent.putExtra("TARGET_USER_ID", targetUserId ?: auth.currentUser?.uid)
            startActivity(intent)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_PICK -> {
                    data?.data?.let { uri ->
                        uploadProfilePicture(uri)
                    }
                }
                REQUEST_IMAGE_CAPTURE -> {
                    // Handle captured image from camera
                    // This would require additional implementation
                }
            }
        }
    }
    
    private fun displayCurrentUserProfile(
        imageViewProfile: ImageView,
        textViewName: TextView,
        textViewEmail: TextView,
        textViewDepartment: TextView,
        textViewMeetingsAttended: TextView,
        textViewMeetingsMissed: TextView,
        textViewMinutesOfMeeting: TextView,
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

                        // Set the data into our TextViews
                        textViewName.text = name
                        textViewEmail.text = email
                        // Show designation next to department
                        textViewDepartment.text = "$department • $designation"
                        
                        // Load profile picture
                        loadProfilePicture(uid, imageViewProfile)
                        
                        // Check if current user is admin to show edit icon
                        checkIfUserIsAdmin(uid) { isAdmin ->
                            currentUserIsAdmin = isAdmin
                            if (isAdmin) {
                                imageViewEditDepartmentDesignation.visibility = View.VISIBLE
                                imageViewEditDepartmentDesignation.setOnClickListener {
                                    showEditDepartmentDesignationDialog(uid, department, designation, textViewDepartment)
                                }
                            }
                        }
                        
                        // Fetch meeting statistics
                        fetchMeetingStatistics(uid, textViewMeetingsAttended, textViewMeetingsMissed, textViewMinutesOfMeeting)
                    } else {
                        Log.d("ProfileFragment", "No such document")
                        textViewName.text = "User Not Found"
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
    
    private fun displayUserProfile(
        userId: String,
        imageViewProfile: ImageView,
        textViewName: TextView,
        textViewEmail: TextView,
        textViewDepartment: TextView,
        textViewMeetingsAttended: TextView,
        textViewMeetingsMissed: TextView,
        textViewMinutesOfMeeting: TextView,
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

                    // Set the data into our TextViews
                    textViewName.text = name
                    textViewEmail.text = email
                    // Show designation next to department
                    textViewDepartment.text = "$department • $designation"
                    
                    // Load profile picture
                    loadProfilePicture(userId, imageViewProfile)
                    
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
                    fetchMeetingStatistics(userId, textViewMeetingsAttended, textViewMeetingsMissed, textViewMinutesOfMeeting)
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
    
    private fun loadProfilePicture(userId: String, imageView: ImageView) {
        try {
            // Reference to the profile picture in Firebase Storage with explicit bucket
            val storageRef = storage.getReference("profile_pictures/$userId.jpg")
            
            val ONE_MEGABYTE: Long = 1024 * 1024
            storageRef.getBytes(ONE_MEGABYTE).addOnSuccessListener { bytes ->
                // Successfully downloaded data, convert to bitmap and display
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imageView.setImageBitmap(bitmap)
                imageView.background = null // Remove the default background
            }.addOnFailureListener {
                // Handle any errors - use default image
                Log.d("ProfileFragment", "No profile picture found for user: $userId, using default")
                // Set default image resource to ensure it's visible
                imageView.setImageResource(R.drawable.universalpp)
            }
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error loading profile picture", e)
            // Set default image resource to ensure it's visible
            imageView.setImageResource(R.drawable.universalpp)
        }
    }
    
    private fun showProfilePictureOptions() {
        val options = arrayOf("Choose from Gallery", "Take Photo", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Update Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> chooseFromGallery()
                    1 -> takePhoto() // This would require camera permissions
                    2 -> {} // Cancel
                }
            }
            .show()
    }
    
    private fun chooseFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }
    
    private fun takePhoto() {
        // This would require implementing camera functionality with proper permissions
        Toast.makeText(context, "Camera functionality coming soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun uploadProfilePicture(imageUri: Uri) {
        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val uid = currentUser.uid
                
                // Get bitmap from URI
                val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, imageUri)
                
                // Compress bitmap to reduce file size
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val data = baos.toByteArray()
                
                // Create storage reference with explicit bucket
                val storageRef = storage.getReference("profile_pictures/$uid.jpg")
                val uploadTask = storageRef.putBytes(data)
                
                uploadTask
                    .addOnProgressListener { taskSnapshot ->
                        val progress = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount
                        Log.d("ProfileFragment", "Upload is $progress% done")
                    }
                    .addOnSuccessListener {
                        Log.d("ProfileFragment", "Profile picture uploaded successfully")
                        Toast.makeText(context, "Profile picture updated successfully", Toast.LENGTH_SHORT).show()
                        
                        // Reload the profile picture
                        val imageViewProfile = view?.findViewById<ImageView>(R.id.imageViewProfile)
                        imageViewProfile?.let { loadProfilePicture(uid, it) }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("ProfileFragment", "Error uploading profile picture", exception)
                        Toast.makeText(context, "Error uploading profile picture: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error processing profile picture", e)
            Toast.makeText(context, "Error processing profile picture: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkIfUserIsAdmin(userId: String, callback: (Boolean) -> Unit) {
        try {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val userDesignation = document.getString("designation")
                        // Make admin panel accessible to ADMIN users
                        val isAdmin = (userDesignation == "ADMIN" || userDesignation == "DEAN" || userDesignation == "HOD")
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
            val departmentSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDepartment)
            val designationSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDesignation)

            // --- UPDATED: New list of departments ---
            // For future reference, other departments were:
            // "CS", "CSBS", "EEE", "ETE", "ECE", "Mech", "Civil", "ISE"
            val departments = arrayOf("Unassigned", "AIML") // Only keeping AIML as per new requirements
            
            // For future reference, other designations were:
            // "ADMIN", "DEAN", "Others", "Unassigned"
            // Order by authority level (high -> low), then include Others and Unassigned at end
            val designations = arrayOf("HOD", "Associate Professor", "Assistant Professor", "Lab Assistant", "HOD's Assistant", "Unassigned")

            departmentSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, departments)
            designationSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, designations)

            // Hide name field since we're only editing department and designation
            nameEditText.visibility = View.GONE
            val nameLabel = dialogView.findViewById<TextView>(R.id.textViewNameLabel)
            nameLabel.visibility = View.GONE

            departmentSpinner.setSelection(departments.indexOf(currentDepartment).coerceAtLeast(0))
            designationSpinner.setSelection(designations.indexOf(currentDesignation).coerceAtLeast(0))

            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setTitle("Edit Department & Designation")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update") { dialog, which ->
                    try {
                        val newDepartment = departmentSpinner.selectedItem.toString()
                        val newDesignation = designationSpinner.selectedItem.toString()

                        val updates = mapOf(
                            "department" to newDepartment,
                            "designation" to newDesignation
                        )

                        db.collection("users").document(userId).update(updates)
                            .addOnSuccessListener {
                                try {
                                    Toast.makeText(context, "Profile updated successfully.", Toast.LENGTH_SHORT).show()
                                    // Update the displayed department and designation
                                    textViewDepartment.text = "$newDepartment • $newDesignation"
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
    
    private fun fetchMeetingStatistics(userId: String, textViewAttended: TextView, textViewMissed: TextView, textViewMinutesOfMeeting: TextView) {
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
                            // This is a past meeting
                            // Check if the meeting has an end time (indicating it was conducted)
                            if (meeting.endTime != null) {
                                // Meeting was conducted, count as attended
                                meetingsAttended++
                                // Calculate meeting duration in minutes
                                val durationMillis = meeting.endTime!!.toDate().time - meeting.dateTime.toDate().time
                                val durationMinutes = durationMillis / (1000 * 60)
                                totalMeetingMinutes += durationMinutes
                            } else {
                                // Meeting has passed but no end time is recorded, count as missed
                                meetingsMissed++
                            }
                        }
                        // For future meetings, we don't count them in either category
                        // They will be counted only after they occur
                    }
                }
                
                // Update the UI with the statistics
                textViewAttended.text = meetingsAttended.toString()
                textViewMissed.text = meetingsMissed.toString()
                
                // Convert total minutes to hours and display
                val totalHours = totalMeetingMinutes / 60
                val remainingMinutes = totalMeetingMinutes % 60
                textViewMinutesOfMeeting.text = if (remainingMinutes > 0) {
                    "$totalHours hrs $remainingMinutes mins"
                } else {
                    "$totalHours hrs"
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileFragment", "Error fetching meetings: ", exception)
                textViewAttended.text = "0"
                textViewMissed.text = "0"
                textViewMinutesOfMeeting.text = "0 hrs"
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
        
        // For group meetings (All Faculty, All HODs, etc.), we would need to check if the user 
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
            
            // Set up edit button
            editButton.setOnClickListener {
                dialog.dismiss()
                showProfilePictureOptions()
            }
            
            // Make dialog cancelable by touch outside
            dialog.setCanceledOnTouchOutside(true)
            
            dialog.show()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error showing enlarged image", e)
            Toast.makeText(context, "Error displaying enlarged image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
