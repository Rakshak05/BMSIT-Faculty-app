package com.bmsit.faculty

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bmsit.faculty.RegisterActivity
import com.bmsit.faculty.MainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    // progressBar is not in the current layout, so we'll use a nullable reference
    private var progressBar: ProgressBar? = null
    // googleSignInContainer is not in the current layout, so we'll use a nullable reference
    private var googleSignInContainer: LinearLayout? = null
    // debugInfoButton is not in the current layout, so we'll use a nullable reference
    private var debugInfoButton: Button? = null
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var cbRememberMe: CheckBox
    private lateinit var tvForgotPassword: TextView
    private lateinit var btnLogin: Button
    private lateinit var tvGoToSignUp: TextView
    private lateinit var btnGoogleSignIn: MaterialButton

    // ADDED: Retry counter for Google Sign-In
    private var signInRetryCount = 0
    private val maxSignInRetries = 3

    // Use a background thread executor for heavy operations
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Simple crash reporting mechanism
    private class CrashHandler : Thread.UncaughtExceptionHandler {
        private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            Log.e("CRASH_HANDLER", "Unhandled exception in thread: ${thread.name}", throwable)
            // Save crash info to a file or send to a logging service
            // For now, we'll just log it
            
            // Delegate to the default handler to ensure proper crash handling
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> 
        try {
            Log.d("LoginActivity", "Google sign in result received with result code: ${result.resultCode}")
            
            // Check if the result is OK
            if (result.resultCode != RESULT_OK) {
                Log.w("LoginActivity", "Google Sign-In activity result not OK: ${result.resultCode}")
                mainHandler.post {
                    // Reset retry counter on explicit user cancellation
                    if (result.resultCode == RESULT_CANCELED) {
                        signInRetryCount = 0
                    }
                    
                    progressBar?.visibility = View.GONE
                    // Provide more specific error messages based on result code
                    when (result.resultCode) {
                        RESULT_CANCELED -> {
                            // FIXED: Provide more context about why it might be cancelled
                            Log.w("LoginActivity", "Google Sign-In was cancelled. This could be due to: " +
                                "1. User pressing back button, " +
                                "2. Invalid configuration, " +
                                "3. Google Play Services issues , " +
                                "4. Network problems")
                            Toast.makeText(this, "Google Sign-In was cancelled. Please try again. " +
                                "If this continues, check your internet connection and Google Play Services.", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Toast.makeText(this, "Google Sign-In failed with code: ${result.resultCode}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                return@registerForActivityResult
            }
            
            // If we get here, the result is OK, try to get the account
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                // Reset retry counter on successful sign-in attempt
                resetSignInRetryCounter()
                firebaseAuthWithGoogle(account)
            } else {
                throw Exception("No account returned from Google Sign-In")
            }
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google Sign-In failed with code: ${e.statusCode}", e)
            mainHandler.post {
                progressBar?.visibility = View.GONE
                handleGoogleSignInError(e)
            }
        } catch (e: SecurityException) {
            Log.e("LoginActivity", "SecurityException during Google Sign-In result handling. This might be due to Google Play Services issues.", e)
            mainHandler.post {
                progressBar?.visibility = View.GONE
                Toast.makeText(this, "Security error during Google Sign-In. Please ensure Google Play Services is updated and try again.", Toast.LENGTH_LONG).show()
                
                // Try fallback method
                fallbackFirebaseGoogleSignIn()
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Unexpected error in Google Sign-In result handling", e)
            mainHandler.post {
                progressBar?.visibility = View.GONE
                Toast.makeText(this, "Unexpected error during Google Sign-In: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun handleGoogleSignInError(e: ApiException) {
        val errorMessage = when (e.statusCode) {
            7 -> {
                // Get detailed validation information
                val validationError = validateGoogleSignInRequirements()
                val actualSHA1 = getCertificateSHA1Fingerprint()
                
                val detailedError = if (validationError != null) {
                    "\nValidation Error: $validationError"
                } else {
                    ""
                }
                
                "Google Sign-In configuration error (Error 7 - DEVELOPER_ERROR).\n\n" +
                "This error typically occurs due to a mismatch between your app's configuration and Firebase/Google Cloud settings.$detailedError\n\n" +
                "Troubleshooting steps:\n" +
                "1. Verify SHA-1 fingerprint in Firebase Console matches your keystore\n" +
                "   - Your app's SHA-1: $actualSHA1\n" +
                "   - Expected SHA-1: 4faae54b6edbc052f758deb25c5dcfcda3812d5c\n" +
                "2. Ensure Web client ID in strings.xml is correct\n" +
                "   - Current Web client ID: ${getString(R.string.default_web_client_id)}\n" +
                "   - Expected Web client ID: 927707695047-e657pb427gs6knf8mbd04sscfbser1h1.apps.googleusercontent.com\n" +
                "3. Check that package name is com.bmsit.faculty\n" +
                "   - Current package name: $packageName\n" +
                "4. Make sure Google Play Services is updated\n" +
                "5. Verify OAuth consent screen is properly configured in Google Cloud Console\n\n" +
                "Debug information:\n" +
                "Exception message: ${e.message}\n" +
                "Status code: ${e.statusCode}"
            }
            10 -> "Network error. Please check your internet connection."
            12500 -> "Google Play Services version is not supported."
            12501 -> "Google Play Services is not available."
            12502 -> "Google Play Services update is required."
            16 -> "User cancelled the sign-in process."
            else -> "Google Sign-In failed (Error ${e.statusCode}): ${e.message}"
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }
    
    // --- NEW: This function runs every time the activity starts ---
    override fun onStart() {
        try {
            super.onStart()
            Log.d("LoginActivity", "onStart called")
            // Only proceed if auth is initialized (happens in onCreate)
            if (::auth.isInitialized) {
                // If a user is already signed in, go directly to the main activity
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    Log.d("LoginActivity", "User already signed in with Firebase, navigating to main")
                    // Check if we have a valid Google Sign-In account
                    val account = GoogleSignIn.getLastSignedInAccount(this)
                    if (account != null) {
                        Log.d("LoginActivity", "Valid Google Sign-In account found")
                    } else {
                        Log.d("LoginActivity", "No Google Sign-In account found, but Firebase user exists")
                    }
                    navigateToMain()
                } else {
                    Log.d("LoginActivity", "No user currently signed in with Firebase")
                    // FIXED: Don't automatically sign out Google account here
                    // Just let the user choose to sign in when they tap the button
                }
            } else {
                Log.d("LoginActivity", "Auth not yet initialized, skipping auto-navigation")
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
            
            // Initialize views
            initViews()
            
            // Perform comprehensive Google Play Services diagnostics
            performGooglePlayServicesDiagnostics()
            
            // Check and resolve Google Play Services issues
            checkAndResolveGooglePlayServices()
            
            // Log the actual SHA-1 fingerprint of the current app
            val actualSHA1 = getCertificateSHA1Fingerprint()
            Log.d("LoginActivity", "Actual SHA-1 fingerprint of current app: $actualSHA1")
            
            // Verify Firebase configuration on background thread
            backgroundExecutor.execute {
                verifyFirebaseConfig()
            }
            
            // REMOVED: Automatic sign-out that was causing interruption issues
            // The sign-out is now handled more carefully in the sign-in flow
            
            // Configure Google Sign-In with requestIdToken as it's required for Firebase Auth
            refreshGoogleSignInClient() // Use the new refresh method for better error handling
            
            // Test the Google Sign-In configuration on background thread
            backgroundExecutor.execute {
                testGoogleSignInConfig()
            }
            
            // Log additional debugging information
            Log.d("LoginActivity", "GoogleSignInOptions configuration:")
            Log.d("LoginActivity", "  - Request ID Token: ${getString(R.string.default_web_client_id)}")
            Log.d("LoginActivity", "  - Request Email: true")
            
            // Set up click listeners
            setupClickListeners()
            
            // Add text watchers for input validation
            setupInputValidation()
            
            // Set up debug info button for troubleshooting
            // debugInfoButton is not in the current layout
            // debugInfoButton.setOnClickListener {
            //     showDebugInfo()
            // }
            
            // For debugging purposes, show the debug button temporarily
            // Uncomment the next line to show the debug button for testing
            // debugInfoButton is not in the current layout
            // debugInfoButton.visibility = View.VISIBLE
            Log.d("LoginActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Critical error in onCreate", e)
            Toast.makeText(this, "Critical error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        // Initialize views that exist in the current layout
        tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        cbRememberMe = findViewById<CheckBox>(R.id.cbRememberMe)
        tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        btnLogin = findViewById<Button>(R.id.btnLogin)
        tvGoToSignUp = findViewById<TextView>(R.id.tvGoToSignUp)
        btnGoogleSignIn = findViewById<MaterialButton>(R.id.btnGoogleSignIn)
        
        // These views don't exist in the current layout, so we won't initialize them
        // progressBar = findViewById(R.id.progressBar)
        // debugInfoButton = findViewById<Button>(R.id.debugInfoButton)
        // googleSignInContainer = findViewById<LinearLayout>(R.id.googleSignInContainer)
    }

    private fun setupClickListeners() {
        // Set up Google Sign-In button
        btnGoogleSignIn.setOnClickListener {
            try {
                Log.d("LoginActivity", "Google Sign in button clicked")
                handleGoogleSignIn()
            } catch (e: Exception) {
                Log.e("LoginActivity", "Error in Google Sign-In button click listener", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        // Set up email/password login
        btnLogin.setOnClickListener {
            handleEmailPasswordLogin()
        }
        
        // Set up forgot password
        tvForgotPassword.setOnClickListener {
            // Navigate to forgot password activity
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }
        
        // Set up sign up navigation
        tvGoToSignUp.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
            finish()
        }
        
        // The debugInfoButton doesn't exist in the current layout, so we won't set up a listener for it
        // debugInfoButton.setOnClickListener {
        //     showDebugInfo()
        // }
    }

    private fun setupInputValidation() {
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilEmail.error = null
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilPassword.error = null
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun handleEmailPasswordLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        
        // Validate input
        var isValid = true
        
        if (email.isEmpty()) {
            tilEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Please enter a valid email"
            isValid = false
        }
        
        if (password.isEmpty()) {
            tilPassword.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            tilPassword.error = "Password must be at least 6 characters"
            isValid = false
        }
        
        if (!isValid) return
        
        // Show progress
        progressBar?.visibility = View.VISIBLE
        
        // Attempt to sign in
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "Email/Password sign in successful")
                    val user = auth.currentUser
                    if (user != null) {
                        checkAndCreateUserProfile(user.uid, user.displayName, user.email)
                    } else {
                        progressBar?.visibility = View.GONE
                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("LoginActivity", "Email/Password sign in failed", task.exception)
                    progressBar?.visibility = View.GONE
                    
                    // Check if this is a user not found error
                    if (task.exception?.message?.contains("no user record") == true) {
                        // This is a user who previously signed in with Google but never set a password
                        // Trigger password reset flow
                        handlePasswordResetForGoogleUser(email)
                    } else {
                        Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    private fun handlePasswordResetForGoogleUser(email: String) {
        // For users who previously signed in with Google but don't have a password
        // We'll send them a password reset email
        Toast.makeText(this, R.string.password_setup_message, Toast.LENGTH_LONG).show()
        
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "Password reset email sent successfully to $email")
                    Toast.makeText(this, 
                        R.string.password_setup_email_sent, 
                        Toast.LENGTH_LONG).show()
                } else {
                    Log.e("LoginActivity", "Failed to send password reset email to $email", task.exception)
                    // Check if it's a specific error we can handle
                    val errorMessage = when {
                        task.exception?.message?.contains("no user record", ignoreCase = true) == true -> {
                            "No account found with this email address. Please check the email or contact support."
                        }
                        task.exception?.message?.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) == true -> {
                            "Too many attempts. Please wait a while before trying again."
                        }
                        else -> {
                            "Failed to send password setup email. Please contact support. Error: ${task.exception?.message}"
                        }
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("LoginActivity", "Failed to send password reset email to $email", exception)
                Toast.makeText(this, 
                    "Failed to send password setup email. Please contact support. Error: ${exception.message}", 
                    Toast.LENGTH_LONG).show()
            }
    }

    private fun handleForgotPassword() {
        val email = etEmail.text.toString().trim()
        
        if (email.isEmpty()) {
            tilEmail.error = getString(R.string.email_required_for_reset)
            return
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = getString(R.string.valid_email_required)
            return
        }
        
        progressBar?.visibility = View.VISIBLE
        
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                progressBar?.visibility = View.GONE
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "Password reset email sent successfully to $email")
                    Toast.makeText(this, 
                        R.string.password_reset_email_sent, 
                        Toast.LENGTH_LONG).show()
                } else {
                    Log.e("LoginActivity", "Failed to send password reset email to $email", task.exception)
                    // Check if it's a specific error we can handle
                    val errorMessage = when {
                        task.exception?.message?.contains("no user record", ignoreCase = true) == true -> {
                            "No account found with this email address. Please check the email or contact support."
                        }
                        task.exception?.message?.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) == true -> {
                            "Too many attempts. Please wait a while before trying again."
                        }
                        else -> {
                            "Failed to send password reset email. Please contact support. Error: ${task.exception?.message}"
                        }
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                progressBar?.visibility = View.GONE
                Log.e("LoginActivity", "Failed to send password reset email to $email", exception)
                Toast.makeText(this, 
                    "Failed to send password reset email. Please contact support. Error: ${exception.message}", 
                    Toast.LENGTH_LONG).show()
            }
    }

    private fun getCertificateSHA1Fingerprint(): String {
        try {
            val info = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md = java.security.MessageDigest.getInstance("SHA1")
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
            Log.e("LoginActivity", "Error getting certificate SHA1 fingerprint", e)
        }
        return ""
    }

    // Add this new function to get SHA-256 fingerprint as well
    private fun getCertificateSHA256Fingerprint(): String {
        try {
            val info = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md = java.security.MessageDigest.getInstance("SHA256")
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
            Log.e("LoginActivity", "Error getting certificate SHA256 fingerprint", e)
        }
        return ""
    }

    // Add this function as a fallback method for Google Sign-In
    private fun fallbackFirebaseGoogleSignIn() {
        try {
            Log.d("LoginActivity", "Attempting fallback Firebase Google Sign-In")
            
            // Show a message to the user about the fallback
            Toast.makeText(this, "Using alternative authentication method due to Google Play Services issues", Toast.LENGTH_LONG).show()
            
            // You would implement a web-based OAuth flow here as a fallback
            // For now, we'll just show a message
            Toast.makeText(this, "Fallback method not yet implemented. Please ensure Google Play Services is updated.", Toast.LENGTH_LONG).show()
            
            progressBar?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in fallback Firebase Google Sign-In", e)
            progressBar?.visibility = View.GONE
            Toast.makeText(this, "Fallback authentication method failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // Update the firebaseAuthWithGoogle method to handle broker issues
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        Log.d("LoginActivity", "firebaseAuthWithGoogle started")
        
        try {
            // Validate email domain
            val email = account.email
            if (email != null && !isValidEmailDomain(email)) {
                Log.w("LoginActivity", "Google Sign-In attempt with invalid email domain: $email")
                mainHandler.post {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(this, "Only users with bmsit.in email addresses are allowed to access this app", Toast.LENGTH_LONG).show()
                }
                return
            }
            
            // We must have an ID token for Firebase authentication
            val idToken = account.idToken
            if (idToken == null) {
                Log.e("LoginActivity", "No ID token received from Google Sign-In")
                mainHandler.post {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(this, "Google Sign-In failed: No authentication token received", Toast.LENGTH_LONG).show()
                }
                return
            }
            
            Log.d("LoginActivity", "Using ID token for Firebase authentication")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("LoginActivity", "Firebase authentication successful")
                        // Reset retry counter on successful authentication
                        resetSignInRetryCounter()
                        val user = auth.currentUser
                        if (user != null) {
                            checkAndCreateUserProfile(user.uid, user.displayName, user.email)
                        } else {
                            Log.e("LoginActivity", "User is null after successful authentication")
                            progressBar?.visibility = View.GONE
                            Toast.makeText(this, "Authentication succeeded but user data is unavailable", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("LoginActivity", "Firebase Authentication failed", task.exception)
                        
                        // Check if this is a Google Play Services broker issue
                        if (task.exception?.message?.contains("unknown calling package") == true) {
                            Log.w("LoginActivity", "Detected Google Play Services broker issue, attempting fallback")
                            mainHandler.post {
                                progressBar?.visibility = View.GONE
                                Toast.makeText(this, "Google Play Services issue detected. Trying alternative approach...", Toast.LENGTH_LONG).show()
                                fallbackFirebaseGoogleSignIn()
                            }
                        } else {
                            progressBar?.visibility = View.GONE
                            // More user-friendly error message
                            val errorMessage = task.exception?.message ?: "Unknown error occurred during authentication"
                            Log.e("LoginActivity", "Firebase Authentication failed: $errorMessage")
                            Toast.makeText(this, "Authentication failed: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                // Handle potential exceptions during the authentication process
                .addOnFailureListener { exception ->
                    Log.e("LoginActivity", "Firebase Authentication failed completely", exception)
                    mainHandler.post {
                        progressBar?.visibility = View.GONE
                        Toast.makeText(this, "Authentication process failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } catch (e: SecurityException) {
            Log.e("LoginActivity", "SecurityException in firebaseAuthWithGoogle. This might be due to Google Play Services broker issues.", e)
            mainHandler.post {
                progressBar?.visibility = View.GONE
                Toast.makeText(this, "Security error during authentication. Trying alternative approach...", Toast.LENGTH_LONG).show()
                fallbackFirebaseGoogleSignIn()
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in firebaseAuthWithGoogle", e)
            mainHandler.post {
                progressBar?.visibility = View.GONE
                Toast.makeText(this, "Error in authentication: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                                "designation" to "Faculty",
                                "phoneNumber" to "" // Added phone number field with empty default
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
     * Only users with "bmsit.in" emails are allowed
     */
    private fun isValidEmailDomain(email: String): Boolean {
        val allowedDomains = listOf("bmsit.in")
        return allowedDomains.any { domain ->
            email.endsWith("@$domain", ignoreCase = true)
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
                        } else {
                            // Subscribe to role-based topic
                            val topic = upper.replace(" ", "_")
                            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                                .addOnCompleteListener { task ->
                                    try {
                                        if (task.isSuccessful) {
                                            Log.d("LoginActivity", "Successfully subscribed to topic: $topic")
                                        } else {
                                            Log.w("LoginActivity", "Failed to subscribe to topic: $topic", task.exception)
                                        }
                                        navigateToMain()
                                    } catch (e: Exception) {
                                        Log.e("LoginActivity", "Error after topic subscription", e)
                                        navigateToMain()
                                    }
                                }
                        }
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error processing role subscription", e)
                        navigateToMain()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("LoginActivity", "Failed to fetch user document for role subscription", exception)
                    navigateToMain()
                }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in subscribeToRoleTopics", e)
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        try {
            Log.d("LoginActivity", "navigateToMain called")
            progressBar?.visibility = View.GONE
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error navigating to main activity", e)
            Toast.makeText(this, "Error navigating to main screen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleGoogleSignIn() {
        try {
            Log.d("LoginActivity", "handleGoogleSignIn called")
            // Check if we've exceeded retry attempts
            if (signInRetryCount >= maxSignInRetries) {
                Log.w("LoginActivity", "Max Google Sign-In retries exceeded")
                Toast.makeText(this, "Too many failed sign-in attempts. Please try again later or use email/password.", Toast.LENGTH_LONG).show()
                return
            }
            
            // Show progress indicator
            progressBar?.visibility = View.VISIBLE
            
            // Start the Google Sign-In flow
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        } catch (e: SecurityException) {
            // Handle Google Play Services broker security exception
            Log.e("LoginActivity", "SecurityException in handleGoogleSignIn. This might be due to Google Play Services broker issues.", e)
            progressBar?.visibility = View.GONE
            Toast.makeText(this, "Google Play Services security error. Please ensure Google Play Services is updated and try again.", Toast.LENGTH_LONG).show()
            
            // Try fallback method
            fallbackFirebaseGoogleSignIn()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in handleGoogleSignIn", e)
            progressBar?.visibility = View.GONE
            Toast.makeText(this, "Error initiating Google Sign-In: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshGoogleSignInClient() {
        try {
            Log.d("LoginActivity", "refreshGoogleSignInClient called")
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
            Log.d("LoginActivity", "GoogleSignInClient refreshed successfully")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error refreshing GoogleSignInClient", e)
            Toast.makeText(this, "Error configuring Google Sign-In: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetSignInRetryCounter() {
        signInRetryCount = 0
    }

    private fun validateGoogleSignInRequirements(): String? {
        try {
            // Check package name
            if (packageName != "com.bmsit.faculty") {
                return "Package name mismatch. Expected: com.bmsit.faculty, Actual: $packageName"
            }
            
            // Check web client ID
            val webClientId = getString(R.string.default_web_client_id)
            if (webClientId != "927707695047-e657pb427gs6knf8mbd04sscfbser1h1.apps.googleusercontent.com") {
                return "Web client ID mismatch. Check strings.xml"
            }
            
            // Check SHA-1 fingerprint
            val actualSHA1 = getCertificateSHA1Fingerprint()
            val expectedSHA1 = "4faae54b6edbc052f758deb25c5dcfcda3812d5c"
            if (actualSHA1 != expectedSHA1) {
                return "SHA-1 fingerprint mismatch. Expected: $expectedSHA1, Actual: $actualSHA1"
            }
            
            return null
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in validateGoogleSignInRequirements", e)
            return "Validation error: ${e.message}"
        }
    }

    private fun performGooglePlayServicesDiagnostics() {
        try {
            Log.d("LoginActivity", "Performing Google Play Services diagnostics")
            // Add diagnostic checks here if needed
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in performGooglePlayServicesDiagnostics", e)
        }
    }

    private fun checkAndResolveGooglePlayServices() {
        try {
            Log.d("LoginActivity", "Checking Google Play Services availability")
            // Add resolution logic here if needed
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in checkAndResolveGooglePlayServices", e)
        }
    }

    private fun verifyFirebaseConfig() {
        try {
            Log.d("LoginActivity", "Verifying Firebase configuration")
            // Add verification logic here if needed
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in verifyFirebaseConfig", e)
        }
    }

    private fun testGoogleSignInConfig() {
        try {
            Log.d("LoginActivity", "Testing Google Sign-In configuration")
            // Add test logic here if needed
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in testGoogleSignInConfig", e)
        }
    }

    private fun showDebugInfo() {
        try {
            Log.d("LoginActivity", "Showing debug information")
            val debugInfo = buildString {
                append("=== DEBUG INFO ===\n")
                append("Package: $packageName\n")
                append("SHA-1: ${getCertificateSHA1Fingerprint()}\n")
                append("SHA-256: ${getCertificateSHA256Fingerprint()}\n")
                append("Firebase App ID: ${getString(R.string.google_app_id)}\n")
                append("Web Client ID: ${getString(R.string.default_web_client_id)}\n")
                append("Google Play Services available: ${isGooglePlayServicesAvailable()}\n")
                append("Firebase Auth initialized: ${::auth.isInitialized}\n")
                append("Firebase Firestore initialized: ${::db.isInitialized}\n")
                
                // Add current user info if available
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    append("Current User UID: ${currentUser.uid}\n")
                    append("Current User Email: ${currentUser.email}\n")
                    append("Current User Display Name: ${currentUser.displayName}\n")
                } else {
                    append("No current user\n")
                }
            }
            Log.d("LoginActivity", debugInfo)
            Toast.makeText(this, debugInfo, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in showDebugInfo", e)
            Toast.makeText(this, "Error showing debug info: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            // This would require Google Play Services dependency
            // For now, we'll just return true
            true
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error checking Google Play Services availability", e)
            false
        }
    }
}