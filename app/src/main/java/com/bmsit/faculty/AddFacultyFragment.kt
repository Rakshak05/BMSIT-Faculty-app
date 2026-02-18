package com.bmsit.faculty

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.regex.Pattern
import java.util.Random

class AddFacultyFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    // UI Elements
    private lateinit var toolbar: MaterialToolbar
    private lateinit var nameEditText: TextInputEditText
    private lateinit var nameInputLayout: TextInputLayout
    private lateinit var emailEditText: TextInputEditText
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var departmentAutoComplete: AutoCompleteTextView
    private lateinit var departmentInputLayout: TextInputLayout
    private lateinit var designationAutoComplete: AutoCompleteTextView
    private lateinit var designationInputLayout: TextInputLayout
    private lateinit var addButton: Button
    private lateinit var progressBar: ProgressBar
    
    // Department and designation arrays
    // Updated to include all departments in alphabetical order
    private val departments = arrayOf(
        "Artificial Intelligence & Machine Learning (AIML)",
        "Civil",
        "Computer Science (CSE)",
        "Computer Science & Business Systems (CSBS)",
        "Electrical & Electronics (EEE)",
        "Electronics & Comm (ECE)",
        "Electronics & Telecomm (ETE)",
        "Information Science (ISE)",
        "Mechanical (MECH)"
    )
    
    private val designations = arrayOf(
        "Professor",
        "Associate Professor",
        "Assistant Professor",
        "Lecturer",
        "Lab Instructor"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_faculty, container, false)
        
        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Initialize UI elements
        initializeViews(view)
        
        // Setup spinners
        setupSpinners()
        
        // Setup button click listener
        addButton.setOnClickListener {
            addFacultyMember()
        }
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Check if current user is admin
        checkIfCurrentUserIsAdmin { isAdmin ->
            if (!isAdmin) {
                // If not admin, show error and go back
                Toast.makeText(context, "Only administrators can access this page.", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
        }
    }
    
    private fun checkIfCurrentUserIsAdmin(callback: (Boolean) -> Unit) {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                db.collection("users").document(currentUser.uid).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val userDesignation = document.getString("designation")
                            // Allow access to add faculty only for ADMIN users
                            val isAdmin = (userDesignation == "ADMIN")
                            callback(isAdmin)
                        } else {
                            callback(false)
                        }
                    }
                    .addOnFailureListener {
                        callback(false)
                    }
            } else {
                callback(false)
            }
        } catch (e: Exception) {
            Log.e("AddFacultyFragment", "Error checking admin status", e)
            callback(false)
        }
    }
    
    private fun initializeViews(view: View) {
        toolbar = view.findViewById(R.id.toolbarAddFaculty)
        nameEditText = view.findViewById(R.id.editTextName)
        nameInputLayout = nameEditText.parent.parent as TextInputLayout
        emailEditText = view.findViewById(R.id.editTextEmail)
        emailInputLayout = emailEditText.parent.parent as TextInputLayout
        departmentAutoComplete = view.findViewById(R.id.spinnerDepartment)
        departmentInputLayout = departmentAutoComplete.parent.parent as TextInputLayout
        designationAutoComplete = view.findViewById(R.id.spinnerDesignation)
        designationInputLayout = designationAutoComplete.parent.parent as TextInputLayout
        addButton = view.findViewById(R.id.buttonAddFaculty)
        progressBar = view.findViewById(R.id.progressBar)
        
        // Hide progress bar initially
        progressBar.visibility = View.GONE
        
        // Set up toolbar navigation
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
    
    private fun setupSpinners() {
        // Setup department spinner
        val departmentAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            departments
        )
        departmentAutoComplete.setAdapter(departmentAdapter)
        
        // Setup designation spinner
        val designationAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            designations
        )
        designationAutoComplete.setAdapter(designationAdapter)
    }
    
    private fun addFacultyMember() {
        // Get input values
        val name = nameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val department = departmentAutoComplete.text.toString().trim()
        val designation = designationAutoComplete.text.toString().trim()
        
        Log.d("AddFacultyFragment", "Attempting to add faculty member: $name, $email, $department, $designation")
        
        // Validate inputs
        if (!validateInputs(name, email)) {
            return
        }
        
        // Additional validation for department and designation
        if (department.isEmpty()) {
            departmentInputLayout.error = "Please select a department"
            return
        } else {
            departmentInputLayout.error = null
        }
        
        if (designation.isEmpty()) {
            designationInputLayout.error = "Please select a designation"
            return
        } else {
            designationInputLayout.error = null
        }
        
        // Show progress
        progressBar.visibility = View.VISIBLE
        addButton.isEnabled = false
        
        // Generate a secure temporary password
        val tempPassword = generateTemporaryPassword()
        Log.d("AddFacultyFragment", "Generated temporary password for user")
        
        // Create user in Firebase Authentication
        Log.d("AddFacultyFragment", "Creating user in Firebase Authentication")
        auth.createUserWithEmailAndPassword(email, tempPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("AddFacultyFragment", "User created successfully in Firebase Authentication")
                    // User created successfully, now add to Firestore
                    val userId = auth.currentUser?.uid ?: ""
                    saveFacultyData(userId, name, email, department, designation)
                } else {
                    // Handle registration error
                    progressBar.visibility = View.GONE
                    addButton.isEnabled = true
                    val exception = task.exception
                    Log.e("AddFacultyFragment", "Error creating user", exception)
                    
                    // Provide more detailed error information
                    when {
                        exception is com.google.firebase.FirebaseNetworkException -> {
                            Toast.makeText(
                                context,
                                "Network error: Please check your internet connection and try again. " +
                                "This could also be due to Firebase reCAPTCHA verification issues.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        exception?.message?.contains("network", ignoreCase = true) == true -> {
                            Toast.makeText(
                                context,
                                "Network error: Please check your internet connection and try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        exception?.message?.contains("email", ignoreCase = true) == true -> {
                            Toast.makeText(
                                context,
                                "Email error: This email may already be registered or is invalid.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            Toast.makeText(
                                context,
                                "Error creating user: ${exception?.message ?: "Unknown error occurred"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                // Additional failure listener for extra safety
                progressBar.visibility = View.GONE
                addButton.isEnabled = true
                Log.e("AddFacultyFragment", "Failed to create user", exception)
                
                // Handle specific Firebase network exceptions
                if (exception is com.google.firebase.FirebaseNetworkException) {
                    Toast.makeText(
                        context,
                        "Network error: Please check your internet connection and try again. " +
                        "This could also be due to Firebase reCAPTCHA verification issues.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Failed to create user account: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
    
    private fun generateTemporaryPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        val random = Random()
        val sb = StringBuilder(12)
        for (i in 0 until 12) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }
    
    private fun saveFacultyData(
        userId: String,
        name: String,
        email: String,
        department: String,
        designation: String
    ) {
        Log.d("AddFacultyFragment", "Saving faculty data to Firestore for user ID: $userId")
        
        val userData = hashMapOf(
            "name" to name,
            "email" to email,
            "department" to department,
            "designation" to designation,
            "meetingsAttended" to 0,
            "meetingsMissed" to 0,
            "totalMinutes" to 0
        )
        
        Log.d("AddFacultyFragment", "User data to save: $userData")
        
        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                // Success
                Log.d("AddFacultyFragment", "Faculty data saved successfully to Firestore")
                progressBar.visibility = View.GONE
                addButton.isEnabled = true
                
                Toast.makeText(
                    context,
                    "Faculty member added successfully!",
                    Toast.LENGTH_LONG
                ).show()
                
                // Clear form
                clearForm()
            }
            .addOnFailureListener { exception ->
                // Handle Firestore error
                progressBar.visibility = View.GONE
                addButton.isEnabled = true
                Log.e("AddFacultyFragment", "Error saving user data", exception)
                
                // Provide more detailed error information
                when {
                    exception.message?.contains("network", ignoreCase = true) == true -> {
                        Toast.makeText(
                            context,
                            "Network error: Please check your internet connection and try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    exception.message?.contains("permission", ignoreCase = true) == true -> {
                        Toast.makeText(
                            context,
                            "Permission error: You don't have permission to add faculty members.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            context,
                            "Error saving user data: ${exception.message ?: "Unknown error occurred"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
                // Delete the created user account since we couldn't save data
                auth.currentUser?.delete()?.addOnCompleteListener { deleteTask ->
                    if (deleteTask.isSuccessful) {
                        Log.d("AddFacultyFragment", "Rolled back user creation due to data save failure")
                    } else {
                        Log.e("AddFacultyFragment", "Error rolling back user creation", deleteTask.exception)
                        Toast.makeText(
                            context,
                            "Critical error: User account created but data not saved. Please contact admin.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
    }
    
    private fun validateInputs(name: String, email: String): Boolean {
        var isValid = true
        
        // Validate name
        if (name.isEmpty()) {
            nameInputLayout.error = "Name is required"
            isValid = false
        } else {
            nameInputLayout.error = null
        }
        
        // Validate email
        if (email.isEmpty()) {
            emailInputLayout.error = "Email is required"
            isValid = false
        } else if (!isValidEmail(email)) {
            emailInputLayout.error = "Invalid email format"
            isValid = false
        } else if (!email.endsWith("@bmsit.in")) {
            emailInputLayout.error = "Email must be from bmsit.in domain"
            isValid = false
        } else {
            emailInputLayout.error = null
        }
        
        return isValid
    }
    
    private fun isValidEmail(email: String): Boolean {
        return Pattern.matches(
            "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+",
            email
        )
    }
    
    private fun clearForm() {
        nameEditText.setText("")
        emailEditText.setText("")
        departmentAutoComplete.setText("")
        designationAutoComplete.setText("")
        
        // Clear errors
        nameInputLayout.error = null
        emailInputLayout.error = null
        departmentInputLayout.error = null
        designationInputLayout.error = null
        
        nameEditText.requestFocus()
    }
}