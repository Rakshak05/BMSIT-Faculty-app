package com.bmsit.faculty

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.security.MessageDigest

/**
 * Debug activity to test email sending functionality with detailed logging
 * This activity is for debugging purposes only and should not be included in production builds
 */
class EmailDebugActivity : AppCompatActivity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var sendEmailButton: Button
    private lateinit var debugInfoTextView: TextView
    private lateinit var progressBar: ProgressBar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_debug)
        
        auth = Firebase.auth
        
        emailEditText = findViewById(R.id.emailEditText)
        sendEmailButton = findViewById(R.id.sendEmailButton)
        debugInfoTextView = findViewById(R.id.debugInfoTextView)
        progressBar = findViewById(R.id.progressBar)
        
        sendEmailButton.setOnClickListener {
            sendDebugEmail()
        }
        
        // Display initial debug information
        displayDebugInfo()
    }
    
    private fun displayDebugInfo() {
        val debugInfo = buildString {
            append("=== Firebase Auth Debug Info ===\n")
            append("Firebase Auth initialized: ${::auth.isInitialized}\n")
            
            val currentUser = auth.currentUser
            if (currentUser != null) {
                append("Current User UID: ${currentUser.uid}\n")
                append("Current User Email: ${currentUser.email}\n")
                append("Current User Display Name: ${currentUser.displayName}\n")
                append("Current User isEmailVerified: ${currentUser.isEmailVerified}\n")
            } else {
                append("No current user\n")
            }
            
            append("\n=== App Info ===\n")
            append("Package Name: $packageName\n")
            
            // Get certificate fingerprints
            append("\n=== Certificate Fingerprints ===\n")
            append("SHA-1: ${getCertificateSHA1Fingerprint()}\n")
            append("SHA-256: ${getCertificateSHA256Fingerprint()}\n")
            
            append("\n=== Firebase Configuration ===\n")
            try {
                append("Web Client ID: ${getString(R.string.default_web_client_id)}\n")
            } catch (e: Exception) {
                append("Web Client ID: Error retrieving - ${e.message}\n")
            }
        }
        
        debugInfoTextView.text = debugInfo
        Log.d("EmailDebugActivity", debugInfo)
    }
    
    private fun getCertificateSHA1Fingerprint(): String {
        try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md = MessageDigest.getInstance("SHA1")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val hexString = StringBuilder()
                for (byte in digest) {
                    val hex = Integer.toHexString(0xFF and byte.toInt())
                    if (hex.length == 1) {
                        hexString.append('0')
                    }
                    hexString.append(hex)
                }
                return hexString.toString()
            }
        } catch (e: Exception) {
            Log.e("EmailDebugActivity", "Error getting certificate SHA1 fingerprint", e)
        }
        return "Error retrieving SHA-1"
    }

    private fun getCertificateSHA256Fingerprint(): String {
        try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md = MessageDigest.getInstance("SHA256")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val hexString = StringBuilder()
                for (byte in digest) {
                    val hex = Integer.toHexString(0xFF and byte.toInt())
                    if (hex.length == 1) {
                        hexString.append('0')
                    }
                    hexString.append(hex)
                }
                return hexString.toString()
            }
        } catch (e: Exception) {
            Log.e("EmailDebugActivity", "Error getting certificate SHA256 fingerprint", e)
        }
        return "Error retrieving SHA-256"
    }
    
    private fun sendDebugEmail() {
        val email = emailEditText.text.toString().trim()
        
        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter an email address", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }
        
        progressBar.visibility = View.VISIBLE
        debugInfoTextView.append("\n\n=== Sending Email ===\n")
        debugInfoTextView.append("Sending password reset email to: $email\n")
        
        Log.d("EmailDebugActivity", "Attempting to send password reset email to: $email")
        
        // Test sending password reset email with detailed error handling
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    val successMessage = "SUCCESS: Password reset email sent to $email"
                    Log.d("EmailDebugActivity", successMessage)
                    debugInfoTextView.append("$successMessage\n")
                    Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
                } else {
                    val errorMessage = "ERROR: Failed to send password reset email to $email. Exception: ${task.exception?.message}"
                    Log.e("EmailDebugActivity", errorMessage, task.exception)
                    debugInfoTextView.append("$errorMessage\n")
                    
                    // Try to provide more specific error information
                    when {
                        task.exception?.message?.contains("no user record", ignoreCase = true) == true -> {
                            debugInfoTextView.append("REASON: No account found with this email address\n")
                        }
                        task.exception?.message?.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) == true -> {
                            debugInfoTextView.append("REASON: Too many attempts. Rate limited.\n")
                        }
                        task.exception?.message?.contains("INVALID_EMAIL", ignoreCase = true) == true -> {
                            debugInfoTextView.append("REASON: Invalid email address format\n")
                        }
                        else -> {
                            debugInfoTextView.append("REASON: Unknown error. Check logs for details.\n")
                        }
                    }
                    
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                progressBar.visibility = View.GONE
                val failureMessage = "FAILURE: Failed to send password reset email to $email. Exception: ${exception.message}"
                Log.e("EmailDebugActivity", failureMessage, exception)
                debugInfoTextView.append("$failureMessage\n")
                Toast.makeText(this, failureMessage, Toast.LENGTH_LONG).show()
            }
            .addOnCanceledListener {
                val cancelMessage = "CANCEL: Password reset email request was canceled for $email"
                Log.w("EmailDebugActivity", cancelMessage)
                debugInfoTextView.append("$cancelMessage\n")
                Toast.makeText(this, cancelMessage, Toast.LENGTH_LONG).show()
            }
    }
}