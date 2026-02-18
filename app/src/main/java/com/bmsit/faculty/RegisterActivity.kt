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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    
    private lateinit var tilName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnRegister: Button
    private lateinit var tvGoToSignIn: TextView
    private lateinit var btnGoogleSignUp: MaterialButton

    private val googleSignUpLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    firebaseAuthWithGoogle(account)
                } else {
                    Toast.makeText(this, "Google Sign-Up failed", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Google Sign-Up was cancelled", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            Log.e("RegisterActivity", "Google Sign-Up failed with code: ${e.statusCode}", e)
            Toast.makeText(this, "Google Sign-Up failed: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("RegisterActivity", "Unexpected error in Google Sign-Up result handling", e)
            Toast.makeText(this, "Unexpected error during Google Sign-Up: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        
        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Configure Google Sign-In
        configureGoogleSignIn()
        
        // Initialize views
        initViews()
        
        // Set up click listeners
        setupClickListeners()
        
        // Set up text validation
        setupTextValidation()
    }
    
    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }
    
    private fun initViews() {
        tilName = findViewById(R.id.tilName)
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvGoToSignIn = findViewById(R.id.tvGoToSignIn)
        btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp)
    }
    
    private fun setupClickListeners() {
        btnRegister.setOnClickListener {
            handleRegister()
        }
        
        tvGoToSignIn.setOnClickListener {
            // Navigate to login activity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
        
        // Set up Google Sign-Up button
        btnGoogleSignUp.setOnClickListener {
            handleGoogleSignUp()
        }
    }
    
    private fun handleGoogleSignUp() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignUpLauncher.launch(signInIntent)
    }
    
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        saveUserInfoToFirestore(user.uid, user.displayName ?: "", user.email ?: "")
                    }
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
    
    private fun setupTextValidation() {
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilName.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
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
    
    private fun handleRegister() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        
        // Validate input
        var isValid = true
        
        if (name.isEmpty()) {
            tilName.error = "Name is required"
            isValid = false
        }
        
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
        
        // Show progress (in a real app, you might show a progress dialog)
        
        // Create user with Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("RegisterActivity", "User registration successful")
                    val user = auth.currentUser
                    if (user != null) {
                        // Save additional user info to Firestore
                        saveUserInfoToFirestore(user.uid, name, email)
                    }
                } else {
                    Log.e("RegisterActivity", "User registration failed", task.exception)
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
    
    private fun saveUserInfoToFirestore(uid: String, name: String, email: String) {
        val userDocument = db.collection("users").document(uid)
        val newUser = hashMapOf(
            "uid" to uid,
            "email" to email,
            "name" to name,
            "department" to "Unassigned",
            "designation" to "Faculty",
            "phoneNumber" to ""
        )

        userDocument.set(newUser)
            .addOnSuccessListener { 
                Log.d("RegisterActivity", "User document created successfully")
                // Navigate to main activity
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { exception ->
                Log.e("RegisterActivity", "Failed to create user document", exception)
                Toast.makeText(this, "Failed to save user info: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }
}