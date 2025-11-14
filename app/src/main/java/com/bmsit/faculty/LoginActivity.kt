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
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.Executors

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
    private lateinit var standardGoogleSignInButton: SignInButton
    private lateinit var debugInfoButton: Button

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
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Google Sign-In was cancelled or failed", Toast.LENGTH_LONG).show()
                }
                return@registerForActivityResult
            }
            
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                firebaseAuthWithGoogle(account)
            } else {
                throw Exception("No account returned from Google Sign-In")
            }
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google Sign-In failed with code: ${e.statusCode}", e)
            mainHandler.post {
                progressBar.visibility = View.GONE
                handleGoogleSignInError(e)
            }
        } catch (e: SecurityException) {
            Log.e("LoginActivity", "SecurityException during Google Sign-In result handling. This might be due to Google Play Services issues.", e)
            mainHandler.post {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Security error during Google Sign-In. Please ensure Google Play Services is updated and try again.", Toast.LENGTH_LONG).show()
                
                // Try fallback method
                fallbackFirebaseGoogleSignIn()
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Unexpected error in Google Sign-In result handling", e)
            mainHandler.post {
                progressBar.visibility = View.GONE
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
                    Log.d("LoginActivity", "User already signed in, navigating to main")
                    // Check if we have a valid Google Sign-In account
                    val account = GoogleSignIn.getLastSignedInAccount(this)
                    if (account != null) {
                        Log.d("LoginActivity", "Valid Google Sign-In account found")
                    }
                    navigateToMain()
                } else {
                    Log.d("LoginActivity", "No user currently signed in")
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
            
            progressBar = findViewById(R.id.progressBar)
            emailEditText = findViewById(R.id.emailEditText)
            passwordEditText = findViewById(R.id.passwordEditText)
            showPasswordCheckBox = findViewById(R.id.showPasswordCheckBox)
            loginButton = findViewById(R.id.loginButton)
            createAccountButton = findViewById(R.id.createAccountButton)
            googleSignInContainer = findViewById(R.id.googleSignInContainer)
            standardGoogleSignInButton = findViewById(R.id.standardGoogleSignInButton)
            debugInfoButton = findViewById(R.id.debugInfoButton)
            Log.d("LoginActivity", "Views found successfully")
            
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
            
            // Sign out any existing Google Sign-In session to avoid conflicts
            try {
                GoogleSignIn.getClient(this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()).signOut()
                Log.d("LoginActivity", "Previous Google Sign-In session signed out")
            } catch (e: Exception) {
                Log.w("LoginActivity", "Could not sign out previous Google Sign-In session", e)
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
            val webClientId = getString(R.string.default_web_client_id)
            Log.d("LoginActivity", "Configuring Google Sign-In with Web Client ID: $webClientId")
            
            // Validate the web client ID before using it
            if (!webClientId.endsWith(".apps.googleusercontent.com")) {
                Log.e("LoginActivity", "Invalid Web Client ID format: $webClientId")
                Toast.makeText(this, "Invalid Web Client ID configuration. Please check your strings.xml file.", Toast.LENGTH_LONG).show()
            }
            
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
            Log.d("LoginActivity", "GoogleSignInClient initialized successfully")
            
            // Test the Google Sign-In configuration on background thread
            backgroundExecutor.execute {
                testGoogleSignInConfig()
            }
            
            // Log additional debugging information
            Log.d("LoginActivity", "GoogleSignInOptions configuration:")
            Log.d("LoginActivity", "  - Request ID Token: ${getString(R.string.default_web_client_id)}")
            Log.d("LoginActivity", "  - Request Email: true")
            Log.d("LoginActivity", "  - Scopes: ${gso.scopes}")
            Log.d("LoginActivity", "  - Account name: ${gso.account?.name}")
            Log.d("LoginActivity", "  - Server client ID: ${gso.serverClientId}")
            
            // Configure standard Google Sign-In button
            standardGoogleSignInButton.setSize(SignInButton.SIZE_STANDARD)
            standardGoogleSignInButton.setOnClickListener {
                try {
                    Log.d("LoginActivity", "Standard Google Sign in button clicked")
                    handleGoogleSignIn()
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Error in standard Google Sign-In button click listener", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            googleSignInContainer.setOnClickListener {
                try {
                    Log.d("LoginActivity", "Custom Google Sign in button clicked")
                    handleGoogleSignIn()
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Error in custom Google Sign-In button click listener", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            // Set up debug info button for troubleshooting
            debugInfoButton.setOnClickListener {
                showDebugInfo()
            }
            
            // For debugging purposes, show the debug button temporarily
            // Uncomment the next line to show the debug button for testing
            // debugInfoButton.visibility = View.VISIBLE
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
            
            // Additional debugging: Check if the web client ID matches what's expected
            val expectedWebClientId = "927707695047-e657pb427gs6knf8mbd04sscfbser1h1.apps.googleusercontent.com"
            if (webClientId != expectedWebClientId) {
                Log.w("LoginActivity", "Web client ID mismatch. Expected: $expectedWebClientId, Actual: $webClientId")
            }
            
            // Log the SHA-1 certificate hash from google-services.json for verification
            Log.d("LoginActivity", "Expected SHA-1 from google-services.json: 4faae54b6edbc052f758deb25c5dcfcda3812d5c")
            
            // Test if we can get the sign-in intent without crashing
            try {
                val testIntent = testClient.signInIntent
                Log.d("LoginActivity", "Google Sign-In intent created successfully")
            } catch (e: Exception) {
                Log.e("LoginActivity", "Error creating Google Sign-In intent", e)
            }
            
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error testing Google Sign-In configuration", e)
        }
    }

    // Method to verify Firebase configuration
    private fun verifyFirebaseConfig() {
        try {
            Log.d("LoginActivity", "Verifying Firebase configuration")
            
            // Check if Firebase is properly initialized
            val firebaseOptions = com.google.firebase.FirebaseApp.getInstance().options
            Log.d("LoginActivity", "Firebase App ID: ${firebaseOptions.applicationId}")
            Log.d("LoginActivity", "Firebase Project ID: ${firebaseOptions.projectId}")
            Log.d("LoginActivity", "Firebase API Key: ${firebaseOptions.apiKey}")
            
            // Verify that we can access Firestore
            db.collection("test").limit(1).get()
                .addOnSuccessListener {
                    Log.d("LoginActivity", "Firestore access successful")
                }
                .addOnFailureListener { e ->
                    Log.w("LoginActivity", "Firestore access failed", e)
                }
                
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error verifying Firebase configuration", e)
        }
    }

    // Method to get the actual SHA-1 of the current app (for debugging)
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

    // Add this function as a fallback method for Google Sign-In
    private fun fallbackFirebaseGoogleSignIn() {
        try {
            Log.d("LoginActivity", "Attempting fallback Firebase Google Sign-In")
            
            // Show a message to the user about the fallback
            Toast.makeText(this, "Using alternative authentication method due to Google Play Services issues", Toast.LENGTH_LONG).show()
            
            // You would implement a web-based OAuth flow here as a fallback
            // For now, we'll just show a message
            Toast.makeText(this, "Fallback method not yet implemented. Please ensure Google Play Services is updated.", Toast.LENGTH_LONG).show()
            
            progressBar.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in fallback Firebase Google Sign-In", e)
            progressBar.visibility = View.GONE
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
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Only users with bmsit.in or meetinghubapp@gmail.com email addresses are allowed to access this app", Toast.LENGTH_LONG).show()
                }
                return
            }
            
            // We must have an ID token for Firebase authentication
            val idToken = account.idToken
            if (idToken == null) {
                Log.e("LoginActivity", "No ID token received from Google Sign-In")
                mainHandler.post {
                    progressBar.visibility = View.GONE
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
                        val user = auth.currentUser
                        if (user != null) {
                            checkAndCreateUserProfile(user.uid, user.displayName, user.email)
                        } else {
                            Log.e("LoginActivity", "User is null after successful authentication")
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, "Authentication succeeded but user data is unavailable", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("LoginActivity", "Firebase Authentication failed", task.exception)
                        
                        // Check if this is a Google Play Services broker issue
                        if (task.exception?.message?.contains("unknown calling package") == true) {
                            Log.w("LoginActivity", "Detected Google Play Services broker issue, attempting fallback")
                            mainHandler.post {
                                progressBar.visibility = View.GONE
                                Toast.makeText(this, "Google Play Services issue detected. Trying alternative approach...", Toast.LENGTH_LONG).show()
                                fallbackFirebaseGoogleSignIn()
                            }
                        } else {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, "Firebase Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        } catch (e: SecurityException) {
            Log.e("LoginActivity", "SecurityException in firebaseAuthWithGoogle. This might be due to Google Play Services broker issues.", e)
            mainHandler.post {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Security error during authentication. Trying alternative approach...", Toast.LENGTH_LONG).show()
                fallbackFirebaseGoogleSignIn()
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in firebaseAuthWithGoogle", e)
            mainHandler.post {
                progressBar.visibility = View.GONE
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
            // Safety check to ensure we're not calling this before views are initialized
            if (!::progressBar.isInitialized) {
                Log.w("LoginActivity", "Progress bar not initialized, delaying navigation")
                mainHandler.postDelayed({
                    navigateToMain()
                }, 100)
                return
            }
            
            progressBar.visibility = View.GONE
            // Schedule periodic checks with a delay to avoid blocking
            mainHandler.postDelayed({
                schedulePeriodicCheck()
            }, 2000)
            
            // No toast message here, as it can be annoying on auto-login
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            Log.d("LoginActivity", "Navigation to MainActivity initiated")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in navigateToMain", e)
            // Show error on UI thread
            mainHandler.post {
                Toast.makeText(this, "Error navigating to main screen: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
    
    // Add this function to get detailed Google Play Services information
    private fun getGooglePlayServicesInfo(): String {
        return try {
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
            
            val gmsVersion = try {
                val gmsPackageInfo = packageManager.getPackageInfo("com.google.android.gms", 0)
                gmsPackageInfo.versionName ?: "Unknown"
            } catch (e: Exception) {
                "Not installed"
            }
            
            val gsfVersion = try {
                val gsfPackageInfo = packageManager.getPackageInfo("com.google.android.gsf", 0)
                gsfPackageInfo.versionName ?: "Unknown"
            } catch (e: Exception) {
                "Not installed"
            }
            
            "Google Play Services Status: $resultCode\n" +
            "Google Play Services Version: $gmsVersion\n" +
            "Google Services Framework Version: $gsfVersion"
        } catch (e: Exception) {
            "Error getting Google Play Services info: ${e.message}"
        }
    }
    
    // Update the showDebugInfo method to include Google Play Services information
    private fun showDebugInfo() {
        try {
            val actualSHA1 = getCertificateSHA1Fingerprint()
            val actualSHA256 = getCertificateSHA256Fingerprint()
            val webClientId = getString(R.string.default_web_client_id)
            val expectedSHA1 = "4faae54b6edbc052f758deb25c5dcfcda3812d5c"
            val expectedWebClientId = "927707695047-e657pb427gs6knf8mbd04sscfbser1h1.apps.googleusercontent.com"
            
            val googlePlayServicesInfo = getGooglePlayServicesInfo()
            
            val debugInfo = """
                Google Sign-In Debug Information:
                
                Package Name: $packageName
                Expected Package Name: com.bmsit.faculty
                Package Match: ${packageName == "com.bmsit.faculty"}
                
                Current SHA-1: $actualSHA1
                Expected SHA-1: $expectedSHA1
                SHA-1 Match: ${actualSHA1.equals(expectedSHA1, ignoreCase = true)}
                
                Current SHA-256: $actualSHA256
                
                Current Web Client ID: $webClientId
                Expected Web Client ID: $expectedWebClientId
                Web Client ID Match: ${webClientId == expectedWebClientId}
                
                Google Play Services Available: ${isGooglePlayServicesAvailable()}
                
                $googlePlayServicesInfo
                
                Firebase App ID: ${getString(R.string.google_app_id)}
            """.trimIndent()
            
            // Show the debug info in a scrollable dialog
            val scrollView = android.widget.ScrollView(this)
            val textView = android.widget.TextView(this).apply {
                text = debugInfo
                setTextIsSelectable(true)
                setPadding(16, 16, 16, 16)
            }
            scrollView.addView(textView)
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Debug Information")
                .setView(scrollView)
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error showing debug info", e)
            Toast.makeText(this, "Error showing debug info: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
            resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
    }
    
    private fun validateGoogleSignInRequirements(): String? {
        try {
            // Check package name
            if (packageName != "com.bmsit.faculty") {
                return "Package name mismatch. Expected: com.bmsit.faculty, Actual: $packageName"
            }
            
            // Check SHA-1 fingerprint
            val actualSHA1 = getCertificateSHA1Fingerprint()
            val expectedSHA1 = "4faae54b6edbc052f758deb25c5dcfcda3812d5c"
            if (!actualSHA1.equals(expectedSHA1, ignoreCase = true)) {
                return "SHA-1 fingerprint mismatch. Expected: $expectedSHA1, Actual: $actualSHA1"
            }
            
            // Check Web client ID
            val webClientId = getString(R.string.default_web_client_id)
            val expectedWebClientId = "927707695047-e657pb427gs6knf8mbd04sscfbser1h1.apps.googleusercontent.com"
            if (webClientId != expectedWebClientId) {
                return "Web client ID mismatch. Expected: $expectedWebClientId, Actual: $webClientId"
            }
            
            // Check Google Play Services
            if (!isGooglePlayServicesAvailable()) {
                return "Google Play Services not available or outdated"
            }
            
            // All validations passed
            return null
        } catch (e: Exception) {
            return "Error during validation: ${e.message}"
        }
    }
    
    // Add this new function to check for Google Play Services updates
    private fun checkForGooglePlayServicesUpdate() {
        try {
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
            
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                if (googleApiAvailability.isUserResolvableError(resultCode)) {
                    // Show a dialog to resolve the error
                    val dialog = googleApiAvailability.getErrorDialog(this, resultCode, 9000)
                    dialog?.show()
                } else {
                    // Google Play Services is not available
                    Toast.makeText(this, "Google Play Services is not available on this device", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error checking for Google Play Services update", e)
        }
    }
    
    // Update the checkAndResolveGooglePlayServices method
    private fun checkAndResolveGooglePlayServices() {
        try {
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
            
            when (resultCode) {
                com.google.android.gms.common.ConnectionResult.SUCCESS -> {
                    Log.d("LoginActivity", "Google Play Services is available and up to date")
                }
                com.google.android.gms.common.ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                    Log.w("LoginActivity", "Google Play Services update is required")
                    Toast.makeText(this, "Google Play Services update is required. Please update Google Play Services from the Play Store.", Toast.LENGTH_LONG).show()
                    // Optionally, you can prompt the user to update
                    val dialog = googleApiAvailability.getErrorDialog(this, resultCode, 1000)
                    dialog?.show()
                }
                com.google.android.gms.common.ConnectionResult.SERVICE_MISSING -> {
                    Log.w("LoginActivity", "Google Play Services is missing")
                    Toast.makeText(this, "Google Play Services is missing. Please install Google Play Services from the Play Store.", Toast.LENGTH_LONG).show()
                }
                com.google.android.gms.common.ConnectionResult.SERVICE_DISABLED -> {
                    Log.w("LoginActivity", "Google Play Services is disabled")
                    Toast.makeText(this, "Google Play Services is disabled. Please enable Google Play Services in system settings.", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Log.w("LoginActivity", "Google Play Services is not available. Result code: $resultCode")
                    Toast.makeText(this, "Google Play Services is not available. Please check your device settings.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error checking Google Play Services availability", e)
        }
    }
    
    // Add this function to check for Google Play Services issues more comprehensively
    private fun performGooglePlayServicesDiagnostics() {
        try {
            Log.d("LoginActivity", "Performing Google Play Services diagnostics")
            
            // Check Google Play Services availability
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
            
            Log.d("LoginActivity", "Google Play Services result code: $resultCode")
            
            // Get package info for Google Play Services
            try {
                val gmsPackageInfo = packageManager.getPackageInfo("com.google.android.gms", 0)
                Log.d("LoginActivity", "Google Play Services version: ${gmsPackageInfo.versionName}")
            } catch (e: Exception) {
                Log.w("LoginActivity", "Could not get Google Play Services package info", e)
            }
            
            // Get package info for Google Services Framework
            try {
                val gsfPackageInfo = packageManager.getPackageInfo("com.google.android.gsf", 0)
                Log.d("LoginActivity", "Google Services Framework version: ${gsfPackageInfo.versionName}")
            } catch (e: Exception) {
                Log.w("LoginActivity", "Could not get Google Services Framework package info", e)
            }
            
            // Check if we can access Google Play Services
            try {
                val context = this
                val gmsContext = context.createPackageContext("com.google.android.gms", 0)
                Log.d("LoginActivity", "Successfully created Google Play Services context")
                
                // Try to get the package name from the context
                val gmsPackageName = gmsContext.packageName
                Log.d("LoginActivity", "Google Play Services package name: $gmsPackageName")
            } catch (e: Exception) {
                Log.e("LoginActivity", "Could not create Google Play Services context", e)
            }
            
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error during Google Play Services diagnostics", e)
        }
    }
    
    private fun handleGoogleSignIn() {
        try {
            Log.d("LoginActivity", "handleGoogleSignIn started")
            
            // Validate Google Sign-In requirements before proceeding
            val validationError = validateGoogleSignInRequirements()
            if (validationError != null) {
                Log.w("LoginActivity", "Google Sign-In validation failed: $validationError")
                Toast.makeText(this, "Configuration error: $validationError", Toast.LENGTH_LONG).show()
                return
            }
            
            // Additional debugging information
            Log.d("LoginActivity", "Current configuration:")
            Log.d("LoginActivity", "  Package name: $packageName")
            Log.d("LoginActivity", "  Web client ID: ${getString(R.string.default_web_client_id)}")
            Log.d("LoginActivity", "  SHA-1 fingerprint: ${getCertificateSHA1Fingerprint()}")
            
            progressBar.visibility = View.VISIBLE
            
            // Try to get the sign-in intent
            val signInIntent = googleSignInClient.signInIntent
            Log.d("LoginActivity", "Launching Google Sign-In intent")
            googleSignInLauncher.launch(signInIntent)
            Log.d("LoginActivity", "Google Sign-In intent launched successfully")
        } catch (e: SecurityException) {
            Log.e("LoginActivity", "SecurityException during Google Sign-In launch. This might be due to Google Play Services broker issues.", e)
            progressBar.visibility = View.GONE
            
            // Try alternative approach without using the broker
            tryAlternativeGoogleSignIn()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error launching Google Sign-In", e)
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error launching Google Sign-In: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // Add this function to try an alternative Google Sign-In approach
    private fun tryAlternativeGoogleSignIn() {
        try {
            Log.d("LoginActivity", "Attempting alternative Google Sign-In approach")
            
            // Create a new GoogleSignInClient with different options
            val webClientId = getString(R.string.default_web_client_id)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            
            val alternativeClient = GoogleSignIn.getClient(this, gso)
            
            // Try to sign out first to clear any problematic state
            alternativeClient.signOut()
                .addOnCompleteListener {
                    try {
                        Log.d("LoginActivity", "Alternative client sign out completed")
                        progressBar.visibility = View.VISIBLE
                        
                        val signInIntent = alternativeClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error with alternative Google Sign-In approach", e)
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Alternative Google Sign-In approach failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("LoginActivity", "Alternative client sign out failed", e)
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to reset Google Sign-In state: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error setting up alternative Google Sign-In", e)
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Could not set up alternative Google Sign-In: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Shutdown background executor
        backgroundExecutor.shutdown()
        // Remove any pending callbacks
        mainHandler.removeCallbacksAndMessages(null)
    }
}