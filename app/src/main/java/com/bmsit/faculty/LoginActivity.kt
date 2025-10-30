package com.bmsit.faculty

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var showPasswordCheckBox: CheckBox
    private lateinit var loginButton: Button
    private lateinit var createAccountButton: Button
    private lateinit var googleSignInContainer: LinearLayout

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
            Log.d("LoginActivity", "Google sign in result received with result code: ${result.resultCode}")
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google Sign-In failed with code: ${e.statusCode}", e)
            progressBar.visibility = View.GONE
            // Handle specific error codes with detailed guidance
            val errorMessage = when (e.statusCode) {
                7 -> {
                    "Google Sign-In configuration error (Error 7 - DEVELOPER_ERROR).\n\n" +
                    "Troubleshooting steps:\n" +
                    "1. Verify SHA-1 fingerprint in Firebase Console matches your keystore\n" +
                    "2. Ensure Web client ID in strings.xml is correct\n" +
                    "3. Check that package name is com.bmsit.faculty\n" +
                    "4. Make sure Google Play Services is updated\n" +
                    "5. Verify OAuth consent screen is properly configured in Google Cloud Console"
                }
                10 -> "Network error. Please check your internet connection."
                12500 -> "Google Play Services version is not supported."
                12501 -> "Google Play Services is not available."
                12502 -> "Google Play Services update is required."
                else -> "Google Sign-In failed (Error ${e.statusCode}): ${e.message}"
            }
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Unexpected error in Google Sign-In", e)
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
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
            
            // Log Firebase configuration for debugging
            Log.d("LoginActivity", "Firebase app ID: ${getString(R.string.google_app_id)}")
            
            progressBar = findViewById(R.id.progressBar)
            emailEditText = findViewById(R.id.emailEditText)
            passwordEditText = findViewById(R.id.passwordEditText)
            showPasswordCheckBox = findViewById(R.id.showPasswordCheckBox)
            loginButton = findViewById(R.id.loginButton)
            createAccountButton = findViewById(R.id.createAccountButton)
            googleSignInContainer = findViewById(R.id.googleSignInContainer)
            Log.d("LoginActivity", "Views found successfully")
            
            // Check Google Play Services availability
            val googlePlayAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googlePlayAvailability.isGooglePlayServicesAvailable(this)
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.w("LoginActivity", "Google Play Services not available. Result code: $resultCode")
                if (googlePlayAvailability.isUserResolvableError(resultCode)) {
                    val errorDialog = googlePlayAvailability.getErrorDialog(this, resultCode, 9000)
                    errorDialog?.show()
                } else {
                    Toast.makeText(this, "Google Play Services not available", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.d("LoginActivity", "Google Play Services available")
            }
            
            // Set up password visibility toggle
            showPasswordCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Show password
                    passwordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                } else {
                    // Hide password
                    passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                }
                // Move cursor to the end of the text
                passwordEditText.setSelection(passwordEditText.text.length)
            }

            // Set up manual login button click listener
            loginButton.setOnClickListener {
                performEmailPasswordLogin()
            }

            // Set up create account button click listener
            createAccountButton.setOnClickListener {
                createEmailPasswordAccount()
            }

            // Configure Google Sign-In with requestIdToken as it's required for Firebase Auth
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
            Log.d("LoginActivity", "GoogleSignInClient initialized WITH Web Client ID")
            Log.d("LoginActivity", "OAuth client ID: ${getString(R.string.default_web_client_id)}")
            
            // Test the Google Sign-In configuration
            testGoogleSignInConfig()

            googleSignInContainer.setOnClickListener {
                try {
                    Log.d("LoginActivity", "Custom Google Sign in button clicked")
                    // Log configuration information for debugging
                    Log.d("LoginActivity", "Web client ID: ${getString(R.string.default_web_client_id)}")
                    Log.d("LoginActivity", "Package name: ${packageName}")
                    
                    // Verify package name matches expected value
                    if (packageName != "com.bmsit.faculty") {
                        Log.w("LoginActivity", "Package name mismatch. Expected: com.bmsit.faculty, Actual: $packageName")
                        Toast.makeText(this, "App package name configuration issue", Toast.LENGTH_LONG).show()
                    }
                    
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

    private fun performEmailPasswordLogin() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        // Validate input
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate email domain
        if (!isValidEmailDomain(email)) {
            Toast.makeText(this, "Only users with bmsit.in or meetinghubapp@gmail.com email addresses are allowed to access this app", Toast.LENGTH_LONG).show()
            return
        }

        // Show progress indicator
        progressBar.visibility = View.VISIBLE

        // Perform Firebase authentication with email and password
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "Email/Password authentication successful")
                    val user = auth.currentUser!!
                    checkAndCreateUserProfile(user.uid, user.displayName, user.email)
                } else {
                    Log.e("LoginActivity", "Email/Password authentication failed", task.exception)
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    // Method to test Google Sign-In configuration
    private fun testGoogleSignInConfig() {
        Log.d("LoginActivity", "Testing Google Sign-In configuration")
        try {
            // Check if Google Play Services is available
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.w("LoginActivity", "Google Play Services not available. Result code: $resultCode")
                return
            }
            
            // Log configuration details
            Log.d("LoginActivity", "Package name: $packageName")
            Log.d("LoginActivity", "Web client ID: ${getString(R.string.default_web_client_id)}")
            
            // Verify Web client ID format
            val webClientId = getString(R.string.default_web_client_id)
            if (!webClientId.endsWith(".apps.googleusercontent.com")) {
                Log.w("LoginActivity", "Web client ID format appears incorrect: $webClientId")
            }
            
            // Test if we can get the GoogleSignInClient
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            val testClient = GoogleSignIn.getClient(this, gso)
            Log.d("LoginActivity", "GoogleSignInClient created successfully")
            
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error testing Google Sign-In configuration", e)
        }
    }

    private fun createEmailPasswordAccount() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        // Validate input
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate email domain
        if (!isValidEmailDomain(email)) {
            Toast.makeText(this, "Only users with bmsit.in or meetinghubapp@gmail.com email addresses are allowed to access this app", Toast.LENGTH_LONG).show()
            return
        }

        // Show progress indicator
        progressBar.visibility = View.VISIBLE

        // Create account with Firebase authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "Account creation successful")
                    val user = auth.currentUser!!
                    checkAndCreateUserProfile(user.uid, user.displayName, user.email)
                } else {
                    Log.e("LoginActivity", "Account creation failed", task.exception)
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Account creation failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        try {
            Log.d("LoginActivity", "firebaseAuthWithGoogle started")
            
            // Validate email domain
            val email = account.email
            if (email != null && !isValidEmailDomain(email)) {
                Log.w("LoginActivity", "Google Sign-In attempt with invalid email domain: $email")
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Only users with bmsit.in or meetinghubapp@gmail.com email addresses are allowed to access this app", Toast.LENGTH_LONG).show()
                return
            }
            
            // We must have an ID token for Firebase authentication
            val idToken = account.idToken
            if (idToken == null) {
                Log.e("LoginActivity", "No ID token received from Google Sign-In")
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Google Sign-In failed: No authentication token received", Toast.LENGTH_LONG).show()
                return
            }
            
            Log.d("LoginActivity", "Using ID token for Firebase authentication")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
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
                                    Log.e("LoginActivity", "Error in subscribeToRoleTopics after token save", e)
                                    navigateToMain()
                                }
                            }
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error processing FCM token", e)
                        navigateToMain()
                    }
                }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in saveFcmToken", e)
            navigateToMain()
        }
    }

    /**
     * Validates if the email belongs to allowed domains
     * Only users with "bmsit.in" or "meetinghubapp@gmail.com" emails are allowed
     */
    private fun isValidEmailDomain(email: String): Boolean {
        val allowedDomains = listOf("bmsit.in", "meetinghubapp@gmail.com")
        return allowedDomains.any { domain ->
            if (domain.contains("@")) {
                email.equals(domain, ignoreCase = true)
            } else {
                email.endsWith("@$domain", ignoreCase = true)
            }
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
            // Schedule periodic checks
            schedulePeriodicCheck()
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
    
    private fun schedulePeriodicCheck() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, PeriodicCheckReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Check every 15 minutes
            val interval = 15 * 60 * 1000L
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                interval,
                pendingIntent
            )
            Log.d("LoginActivity", "Periodic check scheduled")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error scheduling periodic check", e)
        }
    }
}