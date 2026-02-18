package com.bmsit.faculty

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

/**
 * Test activity to verify email sending functionality
 * This activity is for testing purposes only and should not be included in production builds
 */
class TestEmailActivity : AppCompatActivity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var sendEmailButton: Button
    private lateinit var progressBar: ProgressBar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_email)
        
        auth = Firebase.auth
        
        emailEditText = findViewById(R.id.emailEditText)
        sendEmailButton = findViewById(R.id.sendEmailButton)
        progressBar = findViewById(R.id.progressBar)
        
        sendEmailButton.setOnClickListener {
            sendTestEmail()
        }
    }
    
    private fun sendTestEmail() {
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
        
        // Test sending password reset email
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Log.d("TestEmailActivity", "Password reset email sent successfully to $email")
                    Toast.makeText(this, "Password reset email sent successfully to $email", Toast.LENGTH_LONG).show()
                } else {
                    Log.e("TestEmailActivity", "Failed to send password reset email to $email", task.exception)
                    Toast.makeText(this, "Failed to send password reset email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                progressBar.visibility = View.GONE
                Log.e("TestEmailActivity", "Failed to send password reset email to $email", exception)
                Toast.makeText(this, "Failed to send password reset email: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }
}