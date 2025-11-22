package com.bmsit.faculty

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSendEmail: Button
    private lateinit var btnBack: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        
        // Initialize Firebase instance
        auth = FirebaseAuth.getInstance()
        
        // Initialize views
        initViews()
        
        // Set up click listeners
        setupClickListeners()
        
        // Set up text validation
        setupTextValidation()
    }
    
    private fun initViews() {
        tilEmail = findViewById(R.id.tilEmail)
        etEmail = findViewById(R.id.etEmail)
        btnSendEmail = findViewById(R.id.btnSendEmail)
        btnBack = findViewById(R.id.btnBack)
    }
    
    private fun setupClickListeners() {
        btnSendEmail.setOnClickListener {
            handleSendPasswordResetEmail()
        }
        
        btnBack.setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupTextValidation() {
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilEmail.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun handleSendPasswordResetEmail() {
        val email = etEmail.text.toString().trim()
        
        // Validate input
        if (email.isEmpty()) {
            tilEmail.error = "Email is required"
            return
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Please enter a valid email"
            return
        }
        
        // Show progress (in a real app, you might show a progress dialog)
        
        // Send password reset email
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("ForgotPasswordActivity", "Password reset email sent successfully")
                    Toast.makeText(this, "Password reset email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
                    // Optionally navigate back to login
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Log.e("ForgotPasswordActivity", "Failed to send password reset email", task.exception)
                    Toast.makeText(this, "Failed to send password reset email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}