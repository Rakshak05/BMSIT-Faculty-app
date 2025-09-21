package com.bmsit.faculty

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val editTextEmail = findViewById<EditText>(R.id.editTextEmail)
        val editTextPassword = findViewById<EditText>(R.id.editTextPassword)
        val buttonLogin = findViewById<Button>(R.id.buttonLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        buttonLogin.setOnClickListener {
            val email = editTextEmail.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            if (email.isEmpty()) {
                editTextEmail.error = "Email is required"
                editTextEmail.requestFocus()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                editTextEmail.error = "Please enter a valid email"
                editTextEmail.requestFocus()
                return@setOnClickListener
            }
            if (!email.endsWith("@bmsit.in")) {
                editTextEmail.error = "Only @bmsit.in emails are allowed"
                editTextEmail.requestFocus()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                editTextPassword.error = "Password is required"
                editTextPassword.requestFocus()
                return@setOnClickListener
            }
            if (password.length < 6) {
                editTextPassword.error = "Password should be at least 6 characters"
                editTextPassword.requestFocus()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            checkAndCreateUserProfile(user.uid, user.email!!)
                        } else {
                            progressBar.visibility = View.GONE
                            Toast.makeText(baseContext, "Login failed, user not found.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(baseContext, "Authentication failed. Check your credentials.",
                            Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun checkAndCreateUserProfile(uid: String, email: String) {
        val userDocument = db.collection("users").document(uid)

        userDocument.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    val newUser = hashMapOf(
                        "uid" to uid,
                        "email" to email,
                        "role" to "Faculty",
                        "name" to "New User"
                    )

                    userDocument.set(newUser)
                        .addOnSuccessListener {
                            Log.d("Firestore", "New user profile created successfully.")
                            navigateToMain()
                        }
                        .addOnFailureListener { e ->
                            Log.w("Firestore", "Error creating user profile", e)
                            navigateToMain()
                        }
                } else {
                    Log.d("Firestore", "User profile already exists.")
                    navigateToMain()
                }
            }
            .addOnFailureListener { exception ->
                Log.w("Firestore", "Error getting user document:", exception)
                navigateToMain()
            }
    }

    private fun navigateToMain() {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
        Toast.makeText(baseContext, "Login Successful.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        // These flags are important to create a new, clean task for the main activity
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

