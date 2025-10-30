package com.bmsit.faculty

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class DiagnosticFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var diagnosticTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_diagnostic, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            super.onViewCreated(view, savedInstanceState)
            Log.d("DiagnosticFragment", "onViewCreated started")
            
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()
            Log.d("DiagnosticFragment", "Firebase instances initialized")
            
            diagnosticTextView = view.findViewById(R.id.textViewDiagnostic)
            Log.d("DiagnosticFragment", "TextView found")
            
            val checkAuthButton = view.findViewById<Button>(R.id.buttonCheckAuth)
            val checkDbButton = view.findViewById<Button>(R.id.buttonCheckDb)
            val createTestMeetingButton = view.findViewById<Button>(R.id.buttonCreateTestMeeting)
            val clearDiagnosticsButton = view.findViewById<Button>(R.id.buttonClearDiagnostics)
            Log.d("DiagnosticFragment", "Buttons found")
            
            checkAuthButton.setOnClickListener { 
                try {
                    Log.d("DiagnosticFragment", "Check Auth button clicked")
                    checkAuthentication()
                } catch (e: Exception) {
                    Log.e("DiagnosticFragment", "Error in checkAuthentication", e)
                    appendDiagnostic("Error in checkAuthentication: ${e.message}")
                }
            }
            checkDbButton.setOnClickListener { 
                try {
                    Log.d("DiagnosticFragment", "Check DB button clicked")
                    checkDatabaseConnection()
                } catch (e: Exception) {
                    Log.e("DiagnosticFragment", "Error in checkDatabaseConnection", e)
                    appendDiagnostic("Error in checkDatabaseConnection: ${e.message}")
                }
            }
            createTestMeetingButton.setOnClickListener { 
                try {
                    Log.d("DiagnosticFragment", "Create Test Meeting button clicked")
                    createTestMeeting()
                } catch (e: Exception) {
                    Log.e("DiagnosticFragment", "Error in createTestMeeting", e)
                    appendDiagnostic("Error in createTestMeeting: ${e.message}")
                }
            }
            clearDiagnosticsButton.setOnClickListener { 
                try {
                    Log.d("DiagnosticFragment", "Clear Diagnostics button clicked")
                    clearDiagnostics()
                } catch (e: Exception) {
                    Log.e("DiagnosticFragment", "Error in clearDiagnostics", e)
                    appendDiagnostic("Error in clearDiagnostics: ${e.message}")
                }
            }
            
            // Run initial checks
            try {
                Log.d("DiagnosticFragment", "Running initial checks")
                checkAuthentication()
                checkDatabaseConnection()
            } catch (e: Exception) {
                Log.e("DiagnosticFragment", "Error in initial checks", e)
                appendDiagnostic("Error in initial checks: ${e.message}")
            }
            
            Log.d("DiagnosticFragment", "onViewCreated completed successfully")
        } catch (e: Exception) {
            Log.e("DiagnosticFragment", "Error in onViewCreated", e)
            Toast.makeText(context, "Error initializing diagnostics: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun clearDiagnostics() {
        diagnosticTextView.text = ""
    }
    
    private fun appendDiagnostic(text: String) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val currentText = diagnosticTextView.text.toString()
        diagnosticTextView.text = "$currentText\n[$currentTime] $text"
        Log.d("DiagnosticFragment", text)
    }
    
    private fun checkAuthentication() {
        appendDiagnostic("=== Authentication Check ===")
        val currentUser = auth.currentUser
        if (currentUser != null) {
            appendDiagnostic("✓ User is authenticated")
            appendDiagnostic("User ID: ${currentUser.uid}")
            appendDiagnostic("User Email: ${currentUser.email}")
            appendDiagnostic("User Display Name: ${currentUser.displayName}")
            
            // Check user document in Firestore
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        appendDiagnostic("✓ User document found in Firestore")
                        appendDiagnostic("Name: ${document.getString("name")}")
                        appendDiagnostic("Email: ${document.getString("email")}")
                        appendDiagnostic("Department: ${document.getString("department")}")
                        appendDiagnostic("Designation: ${document.getString("designation")}")
                    } else {
                        appendDiagnostic("✗ User document not found in Firestore")
                    }
                }
                .addOnFailureListener { exception ->
                    appendDiagnostic("✗ Failed to fetch user document: ${exception.message}")
                    // Check if it's a permission error
                    if (exception.message?.contains("PERMISSION_DENIED") == true) {
                        appendDiagnostic("   🔴 PERMISSION_DENIED ERROR 🔴")
                        appendDiagnostic("   The app needs read access to the 'users' collection.")
                        appendDiagnostic("   🔧 FIX: Update Firebase Firestore security rules.")
                        appendDiagnostic("   📄 See FIREBASE_RULES_FIX.md for detailed instructions.")
                    }
                }
        } else {
            appendDiagnostic("✗ User is not authenticated")
        }
    }
    
    private fun checkDatabaseConnection() {
        appendDiagnostic("=== Database Connection Check ===")
        try {
            db.collection("users").limit(1).get()
                .addOnSuccessListener { 
                    appendDiagnostic("✓ Firestore connection successful")
                }
                .addOnFailureListener { exception ->
                    appendDiagnostic("✗ Firestore connection failed: ${exception.message}")
                    // Check if it's a permission error
                    if (exception.message?.contains("PERMISSION_DENIED") == true) {
                        appendDiagnostic("   🔴 PERMISSION_DENIED ERROR 🔴")
                        appendDiagnostic("   The app needs read access to the 'users' collection.")
                        appendDiagnostic("   🔧 FIX: Update Firebase Firestore security rules.")
                        appendDiagnostic("   📄 See FIREBASE_RULES_FIX.md for detailed instructions.")
                    }
                }
        } catch (e: Exception) {
            appendDiagnostic("✗ Firestore connection failed with exception: ${e.message}")
        }
        
        // Check if there are any meetings
        try {
            db.collection("meetings").limit(5).get()
                .addOnSuccessListener { result ->
                    appendDiagnostic("Found ${result.size()} meetings in database")
                    for (document in result) {
                        val meeting = document.toObject(Meeting::class.java)
                        appendDiagnostic("  - ${meeting.title} (${meeting.dateTime.toDate()})")
                    }
                }
                .addOnFailureListener { exception ->
                    appendDiagnostic("✗ Failed to fetch meetings: ${exception.message}")
                    // Check if it's a permission error
                    if (exception.message?.contains("PERMISSION_DENIED") == true) {
                        appendDiagnostic("   🔴 PERMISSION_DENIED ERROR 🔴")
                        appendDiagnostic("   The app needs read access to the 'meetings' collection.")
                        appendDiagnostic("   🔧 FIX: Update Firebase Firestore security rules.")
                        appendDiagnostic("   📄 See FIREBASE_RULES_FIX.md for detailed instructions.")
                    }
                }
        } catch (e: Exception) {
            appendDiagnostic("✗ Failed to fetch meetings with exception: ${e.message}")
        }
    }
    
    private fun createTestMeeting() {
        appendDiagnostic("=== Creating Test Meeting ===")
        val currentUser = auth.currentUser
        if (currentUser == null) {
            appendDiagnostic("✗ Cannot create test meeting: User not authenticated")
            return
        }
        
        val testMeeting = Meeting(
            id = db.collection("meetings").document().id,
            title = "Test Meeting",
            location = "Test Location",
            dateTime = com.google.firebase.Timestamp(Date(System.currentTimeMillis() + 3600000)), // 1 hour from now
            attendees = "All Faculty",
            scheduledBy = currentUser.uid,
            customAttendeeUids = emptyList(),
            status = "Active"
        )
        
        db.collection("meetings").document(testMeeting.id).set(testMeeting)
            .addOnSuccessListener {
                appendDiagnostic("✓ Test meeting created successfully")
                Toast.makeText(context, "Test meeting created", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                appendDiagnostic("✗ Failed to create test meeting: ${exception.message}")
                // Check if it's a permission error
                if (exception.message?.contains("PERMISSION_DENIED") == true) {
                    appendDiagnostic("   🔴 PERMISSION_DENIED ERROR 🔴")
                    appendDiagnostic("   The app needs write access to the 'meetings' collection.")
                    appendDiagnostic("   🔧 FIX: Update Firebase Firestore security rules.")
                    appendDiagnostic("   📄 See FIREBASE_RULES_FIX.md for detailed instructions.")
                }
                Toast.makeText(context, "Failed to create test meeting: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }
}