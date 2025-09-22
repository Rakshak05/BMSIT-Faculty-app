package com.bmsit.faculty

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var progressBar: ProgressBar

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- NEW: This function runs every time the activity starts ---
    override fun onStart() {
        super.onStart()
        // If a user is already signed in, go directly to the main activity
        if (auth.currentUser != null) {
            navigateToMain()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        progressBar = findViewById(R.id.progressBar)
        val signInButton = findViewById<SignInButton>(R.id.signInButton)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        signInButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser!!
                    checkAndCreateUserProfile(user.uid, user.displayName, user.email)
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Firebase Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkAndCreateUserProfile(uid: String, name: String?, email: String?) {
        val userDocument = db.collection("users").document(uid)

        userDocument.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    val newUser = hashMapOf(
                        "uid" to uid,
                        "email" to (email ?: "No Email"),
                        "name" to (name ?: "New User"),
                        // --- UPDATED: Use 'designation' with default 'Faculty' ---
                        "department" to "Unassigned",
                        "designation" to "Faculty"
                    )

                    userDocument.set(newUser)
                        .addOnSuccessListener { navigateToMain() }
                        .addOnFailureListener { navigateToMain() }
                } else {
                    navigateToMain()
                }
            }
            .addOnFailureListener { navigateToMain() }
    }

    private fun navigateToMain() {
        progressBar.visibility = View.GONE
        // No toast message here, as it can be annoying on auto-login
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

