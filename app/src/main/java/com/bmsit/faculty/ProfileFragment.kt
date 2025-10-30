package com.bmsit.faculty

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    // Get instances of Firebase services
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Get references to the UI elements from the layout
        val textViewName = view.findViewById<TextView>(R.id.textViewProfileName)
        val textViewEmail = view.findViewById<TextView>(R.id.textViewProfileEmail)
        val textViewDepartment = view.findViewById<TextView>(R.id.textViewProfileDepartment)
        val buttonSignOut = view.findViewById<Button>(R.id.buttonSignOut)

        // --- Fetch User Data ---
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Get the user's unique ID
            val uid = currentUser.uid
            // Create a reference to this user's document in the 'users' collection
            val userDocRef = db.collection("users").document(uid)

            // Fetch the document from Firestore
            userDocRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // If the document exists, get the data
                        val name = document.getString("name") ?: "No Name"
                        val email = document.getString("email") ?: "No Email"
                        val department = document.getString("department") ?: "No Department"
                        val designation = document.getString("designation") ?: "Unassigned"

                        // Set the data into our TextViews
                        textViewName.text = name
                        textViewEmail.text = email
                        // Show designation next to department
                        textViewDepartment.text = "$department • $designation"
                    } else {
                        Log.d("ProfileFragment", "No such document")
                        textViewName.text = "User Not Found"
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("ProfileFragment", "get failed with ", exception)
                    Toast.makeText(activity, "Error fetching profile.", Toast.LENGTH_SHORT).show()
                }
        } else {
            // This case should not happen if user is logged in, but it's good practice
            textViewName.text = "Not Logged In"
        }


        // --- Sign Out Button Logic ---
        buttonSignOut.setOnClickListener {
            auth.signOut()
            // Go back to the Login screen
            val intent = Intent(activity, LoginActivity::class.java)
            // These flags prevent the user from going back to the main activity after logging out
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        }
    }
}
