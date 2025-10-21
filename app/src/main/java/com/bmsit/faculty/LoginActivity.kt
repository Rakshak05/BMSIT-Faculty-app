package com.bmsit.faculty

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var progressBar: ProgressBar

    // Simple crash reporting mechanism
    private class CrashHandler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            Log.e("CRASH_HANDLER", "Unhandled exception in thread: ${thread.name}", throwable)
            // Save crash info to a file or send to a logging service
            // For now, we'll just log it
        }
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> 
        try {
            Log.d("LoginActivity", "Google sign in result received")
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google Sign-In failed", e)
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Unexpected error in Google Sign-In", e)
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- NEW: This function runs every time the activity starts ---
    override fun onStart() {
        try {
            super.onStart()
            Log.d("LoginActivity", "onStart called")
            // If a user is already signed in, go directly to the main activity
            if (auth.currentUser != null) {
                Log.d("LoginActivity", "User already signed in, navigating to main")
                navigateToMain()
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in onStart", e)
            Toast.makeText(this, "Error in app startup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            // Set up crash handler
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler())
            
            super.onCreate(savedInstanceState)
            Log.d("LoginActivity", "onCreate started")
            setContentView(R.layout.activity_login)
            Log.d("LoginActivity", "Layout inflated successfully")

            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()
            Log.d("LoginActivity", "Firebase instances initialized")
            
            progressBar = findViewById(R.id.progressBar)
            val signInButton = findViewById<SignInButton>(R.id.signInButton)
            Log.d("LoginActivity", "Views found successfully")

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
            Log.d("LoginActivity", "GoogleSignInClient initialized")

            signInButton.setOnClickListener {
                try {
                    Log.d("LoginActivity", "Sign in button clicked")
                    progressBar.visibility = View.VISIBLE
                    val signInIntent = googleSignInClient.signInIntent
                    googleSignInLauncher.launch(signInIntent)
                    Log.d("LoginActivity", "Google Sign-In intent launched")
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Error launching Google Sign-In", e)
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error launching Google Sign-In: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            Log.d("LoginActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Critical error in onCreate", e)
            Toast.makeText(this, "Critical error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        try {
            Log.d("LoginActivity", "firebaseAuthWithGoogle started")
            val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    try {
                        if (task.isSuccessful) {
                            Log.d("LoginActivity", "Firebase authentication successful")
                            val user = auth.currentUser!!
                            checkAndCreateUserProfile(user.uid, user.displayName, user.email)
                        } else {
                            Log.e("LoginActivity", "Firebase Authentication failed", task.exception)
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, "Firebase Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error in auth completion handler", e)
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error in auth completion: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in firebaseAuthWithGoogle", e)
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error in authentication: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndCreateUserProfile(uid: String, name: String?, email: String?) {
        try {
            Log.d("LoginActivity", "checkAndCreateUserProfile started for UID: $uid")
            val userDocument = db.collection("users").document(uid)

            userDocument.get()
                .addOnSuccessListener { document ->
                    try {
                        if (!document.exists()) {
                            Log.d("LoginActivity", "User document doesn't exist, creating new one")
                            val newUser = hashMapOf(
                                "uid" to uid,
                                "email" to (email ?: "No Email"),
                                "name" to (name ?: "New User"),
                                // Changed default designation from "Unassigned" to "Faculty" 
                                // to ensure new users can see meetings by default
                                "department" to "Unassigned",
                                "designation" to "Faculty"
                            )

                            userDocument.set(newUser)
                                .addOnSuccessListener { 
                                    try {
                                        Log.d("LoginActivity", "User document created successfully")
                                        saveFcmToken(uid)
                                    } catch (e: Exception) {
                                        Log.e("LoginActivity", "Error in saveFcmToken after user creation", e)
                                        navigateToMain()
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    // Check if it's a permission error
                                    if (exception.message?.contains("PERMISSION_DENIED") == true) {
                                        Log.e("LoginActivity", "PERMISSION_DENIED error: Check Firebase Firestore rules")
                                        Toast.makeText(this, "Permission Error: Check Firebase Firestore rules. The app needs write access to the 'users' collection.", Toast.LENGTH_LONG).show()
                                    }
                                    navigateToMain()
                                }
                        } else {
                            Log.d("LoginActivity", "User document exists, updating if needed")
                            // Backfill missing fields for existing users (e.g., ensure name is set)
                            val updates = mutableMapOf<String, Any>()
                            val existingName = document.getString("name")
                            val existingEmail = document.getString("email")
                            if ((existingName == null || existingName.isBlank() || existingName == "New User") && !name.isNullOrBlank()) {
                                updates["name"] = name
                            }
                            if ((existingEmail == null || existingEmail.isBlank() || existingEmail == "No Email") && !email.isNullOrBlank()) {
                                updates["email"] = email
                            }
                            // Backfill designation for existing users with "Unassigned" designation
                            val existingDesignation = document.getString("designation")
                            if (existingDesignation == null || existingDesignation == "Unassigned") {
                                updates["designation"] = "Faculty"
                            }
                            if (updates.isNotEmpty()) {
                                userDocument.update(updates as Map<String, Any>)
                                    .addOnCompleteListener { task ->
                                        try {
                                            if (!task.isSuccessful) {
                                                // Check if it's a permission error
                                                task.exception?.let { exception ->
                                                    if (exception.message?.contains("PERMISSION_DENIED") == true) {
                                                        Log.e("LoginActivity", "PERMISSION_DENIED error: Check Firebase Firestore rules")
                                                        Toast.makeText(this, "Permission Error: Check Firebase Firestore rules. The app needs write access to the 'users' collection.", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                            saveFcmToken(uid)
                                        } catch (e: Exception) {
                                            Log.e("LoginActivity", "Error in saveFcmToken after user update", e)
                                            navigateToMain()
                                        }
                                    }
                            } else {
                                saveFcmToken(uid)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error processing user document", e)
                        navigateToMain()
                    }
                }
                .addOnFailureListener { exception ->
                    // Check if it's a permission error
                    if (exception.message?.contains("PERMISSION_DENIED") == true) {
                        Log.e("LoginActivity", "PERMISSION_DENIED error: Check Firebase Firestore rules")
                        Toast.makeText(this, "PERMISSION_DENIED ERROR: The app needs read access to the 'users' collection. Update Firebase Firestore security rules. See FIREBASE_RULES_FIX.md for instructions.", Toast.LENGTH_LONG).show()
                    }
                    navigateToMain()
                }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in checkAndCreateUserProfile", e)
            navigateToMain()
        }
    }

    private fun saveFcmToken(uid: String) {
        try {
            Log.d("LoginActivity", "saveFcmToken started")
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    try {
                        if (!task.isSuccessful) {
                            // Even if token fetch fails, proceed to topic subscription attempt
                            Log.w("LoginActivity", "FCM token fetch failed", task.exception)
                            subscribeToRoleTopics(uid)
                            return@addOnCompleteListener
                        }
                        val token = task.result
                        Log.d("LoginActivity", "FCM token fetched successfully")
                        db.collection("users").document(uid)
                            .update(mapOf("fcmToken" to token))
                            .addOnCompleteListener { 
                                try {
                                    subscribeToRoleTopics(uid)
                                } catch (e: Exception) {
                                    Log.e("LoginActivity", "Error in subscribeToRoleTopics", e)
                                    navigateToMain()
                                }
                            }
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error in FCM token completion handler", e)
                        navigateToMain()
                    }
                }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in saveFcmToken", e)
            navigateToMain()
        }
    }

    private fun subscribeToRoleTopics(uid: String) {
        try {
            Log.d("LoginActivity", "subscribeToRoleTopics started")
            // Fetch designation and subscribe to a topic
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    try {
                        val designation = doc.getString("designation") ?: "Unassigned"
                        val upper = designation.uppercase()
                        if (upper == "UNASSIGNED") {
                            // Do not subscribe Unassigned users to any topic
                            Log.d("LoginActivity", "User has Unassigned designation, not subscribing to topics")
                            navigateToMain()
                            return@addOnSuccessListener
                        }
                        val topic = when (upper) {
                            "DEAN" -> "deans"
                            "HOD" -> "hods"
                            "ADMIN" -> "admins"
                            else -> "faculty"
                        }
                        Log.d("LoginActivity", "Subscribing user to topic: $topic")
                        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                            .addOnCompleteListener { 
                                try {
                                    Log.d("LoginActivity", "Topic subscription completed")
                                    navigateToMain()
                                } catch (e: Exception) {
                                    Log.e("LoginActivity", "Error in navigateToMain after topic subscription", e)
                                    // Still navigate to main even if there's an error
                                    navigateToMain()
                                }
                            }
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error processing user document for topic subscription", e)
                        navigateToMain()
                    }
                }
                .addOnFailureListener { 
                    try {
                        Log.e("LoginActivity", "Failed to fetch user document for topic subscription", it)
                        navigateToMain()
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error in navigateToMain after topic subscription failure", e)
                        navigateToMain()
                    }
                }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in subscribeToRoleTopics", e)
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        try {
            Log.d("LoginActivity", "navigateToMain called")
            progressBar.visibility = View.GONE
            // No toast message here, as it can be annoying on auto-login
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            Log.d("LoginActivity", "Navigation to MainActivity initiated")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in navigateToMain", e)
            Toast.makeText(this, "Error navigating to main screen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}