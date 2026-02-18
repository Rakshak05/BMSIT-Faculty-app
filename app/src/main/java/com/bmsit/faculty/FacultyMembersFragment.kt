package com.bmsit.faculty

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class FacultyMembersFragment : Fragment(), UserAdapter.OnItemClickListener {

    private lateinit var db: FirebaseFirestore
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private val userList = mutableListOf<User>()
    private var currentUserIsAdmin: Boolean = false
    private val EDIT_FACULTY_REQUEST_CODE = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            Log.d("FacultyMembersFragment", "onCreateView called")
            return inflater.inflate(R.layout.fragment_admin, container, false)
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error in onCreateView", e)
            Toast.makeText(context, "Error initializing faculty members panel: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            super.onViewCreated(view, savedInstanceState)
            Log.d("FacultyMembersFragment", "onViewCreated called")

            db = FirebaseFirestore.getInstance()
            usersRecyclerView = view.findViewById(R.id.usersRecyclerView)
            usersRecyclerView.layoutManager = LinearLayoutManager(context)

            userAdapter = UserAdapter(userList, this)
            usersRecyclerView.adapter = userAdapter

            setHasOptionsMenu(true)
            
            // Check if current user is admin
            checkIfCurrentUserIsAdmin { isAdmin ->
                currentUserIsAdmin = isAdmin
                fetchUsers()
            }
            
            Log.d("FacultyMembersFragment", "onViewCreated completed successfully")
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error in onViewCreated", e)
            Toast.makeText(context, "Error setting up faculty members panel: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onItemClick(user: User) {
        try {
            // If current user is admin, allow editing; otherwise, open profile
            if (currentUserIsAdmin) {
                openEditFacultyProfile(user)
            } else {
                openUserProfile(user)
            }
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error in onItemClick", e)
            Toast.makeText(context, "Error handling item click: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openEditFacultyProfile(user: User) {
        try {
            val intent = Intent(activity, ProfileActivity::class.java)
            intent.putExtra("USER_ID", user.uid)
            intent.putExtra("IS_EDIT_MODE", true)
            startActivityForResult(intent, EDIT_FACULTY_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error opening edit faculty profile", e)
            Toast.makeText(context, "Error opening edit faculty profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openUserProfile(user: User) {
        try {
            // Create and start the profile activity for the selected user
            val intent = Intent(activity, ProfileActivity::class.java)
            intent.putExtra("USER_ID", user.uid)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error opening user profile", e)
            Toast.makeText(context, "Error opening profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkIfCurrentUserIsAdmin(callback: (Boolean) -> Unit) {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                db.collection("users").document(currentUser.uid).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val userDesignation = document.getString("designation")
                            // Allow access to faculty members panel only for HODs and HOD's Assistants
                            val isAdmin = (userDesignation == "HOD" || userDesignation == "HOD'S ASSISTANT")
                            callback(isAdmin)
                        } else {
                            callback(false)
                        }
                    }
                    .addOnFailureListener {
                        callback(false)
                    }
            } else {
                callback(false)
            }
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error checking admin status", e)
            callback(false)
        }
    }

    private fun showEditUserDialog(user: User) {
        try {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_user, null)

            val nameEditText = dialogView.findViewById<EditText>(R.id.editTextUserName)
            val departmentSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDepartment)
            val designationSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDesignation)

            // --- UPDATED: New list of departments ---
            // Updated to include all departments in alphabetical order
            val departments = arrayOf("AIML", "Civil", "CS", "CSBS", "ECE", "EEE", "ETE", "ISE", "MECH", "Unassigned")
            
            // For future reference, other designations were:
            // "ADMIN", "Others", "Unassigned"
            // Order by authority level (high -> low), then include Others and Unassigned at end
            val designations = arrayOf("HOD", "Associate Professor", "Assistant Professor", "Lab Assistant", "HOD's Assistant", "Unassigned")

            departmentSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, departments)
            designationSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, designations)

            nameEditText.setText(user.name)
            departmentSpinner.setSelection(departments.indexOf(user.department).coerceAtLeast(0))
            designationSpinner.setSelection(designations.indexOf(user.designation).coerceAtLeast(0))

            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update") { dialog, which ->
                    try {
                        val newName = nameEditText.text.toString().trim()
                        val newDepartment = departmentSpinner.selectedItem.toString()
                        val newDesignation = designationSpinner.selectedItem.toString()

                        val updates = mapOf(
                            "name" to newName,
                            "department" to newDepartment,
                            "designation" to newDesignation
                        )

                        db.collection("users").document(user.uid).update(updates)
                            .addOnSuccessListener {
                                try {
                                    Toast.makeText(context, "${user.name}'s profile updated.", Toast.LENGTH_SHORT).show()
                                    fetchUsers()
                                } catch (e: Exception) {
                                    Log.e("FacultyMembersFragment", "Error after successful update", e)
                                    Toast.makeText(context, "Profile updated but error refreshing list: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error updating profile.", Toast.LENGTH_SHORT).show()
                                Log.w("FacultyMembersFragment", "Error updating user document", e)
                            }
                    } catch (e: Exception) {
                        Log.e("FacultyMembersFragment", "Error in update dialog", e)
                        Toast.makeText(context, "Error processing update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error in showEditUserDialog", e)
            Toast.makeText(context, "Error showing edit dialog: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun fetchUsers() {
        try {
            db.collection("users")
                .get()
                .addOnSuccessListener { result ->
                    try {
                        userList.clear()
                        for (document in result) {
                            val user = document.toObject(User::class.java)
                            userList.add(user)
                        }
                        // Sort by designation level desc, then department asc, then name asc
                        userList.sortWith(compareByDescending<User> { designationRank(it.designation) }
                            .thenBy { it.department }
                            .thenBy { it.name })
                        userAdapter.notifyDataSetChanged()
                        
                        // Show a message if there are no users
                        if (userList.isEmpty()) {
                            Toast.makeText(context, "No users found in the system.", Toast.LENGTH_SHORT).show()
                        }
                        Log.d("FacultyMembersFragment", "Successfully fetched ${userList.size} users")
                    } catch (e: Exception) {
                        Log.e("FacultyMembersFragment", "Error processing fetched users", e)
                        Toast.makeText(context, "Error processing user data: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.w("FacultyMembersFragment", "Error getting documents.", exception)
                    Toast.makeText(context, "Error loading users: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error in fetchUsers", e)
            Toast.makeText(context, "Error initiating user fetch: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun designationRank(designation: String?): Int {
        // Ranking system for sorting users in Faculty Members panel
        // Higher numbers mean higher authority
        // New ranking system as per requirements:
        // 1. HOD
        // 2. HOD's Assistant
        // 3. Associate Professor
        // 4. Assistant Professor
        // 5. Lab Assistant
        val trimmedDesignation = designation?.trim()?.uppercase()
        return when (trimmedDesignation) {
            "HOD" -> 5
            "HOD'S ASSISTANT" -> 4
            "ASSOCIATE PROFESSOR" -> 3
            "ASSISTANT PROFESSOR" -> 2
            "LAB ASSISTANT" -> 1
            else -> 0 // Unassigned or unknown
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        try {
            super.onCreateOptionsMenu(menu, inflater)
            // Only show faculty members menu options to actual admins
            if (currentUserIsAdmin) {
                inflater.inflate(R.menu.admin_menu, menu)
            }
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error in onCreateOptionsMenu", e)
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        // Only allow faculty members menu options for actual admins
        if (!currentUserIsAdmin) {
            return super.onOptionsItemSelected(item)
        }
        
        return try {
            when (item.itemId) {
                R.id.action_backfill_designations -> {
                    // Set 'Others' where designation is null or blank
                    db.collection("users").get()
                        .addOnSuccessListener { result ->
                            try {
                                val batch = db.batch()
                                var count = 0
                                for (doc in result) {
                                    val designation = doc.getString("designation")
                                    if (designation.isNullOrBlank()) {
                                        batch.update(doc.reference, mapOf("designation" to "Others"))
                                        count++
                                    }
                                }
                                if (count > 0) {
                                    batch.commit().addOnSuccessListener {
                                        try {
                                            Toast.makeText(context, "Updated $count user(s) to 'Others'.", Toast.LENGTH_SHORT).show()
                                            fetchUsers()
                                        } catch (e: Exception) {
                                            Log.e("FacultyMembersFragment", "Error after successful backfill", e)
                                            Toast.makeText(context, "Backfill completed but error refreshing list: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "No users required backfill.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("FacultyMembersFragment", "Error processing backfill", e)
                                Toast.makeText(context, "Error processing backfill: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("FacultyMembersFragment", "Error initiating backfill", exception)
                            Toast.makeText(context, "Error initiating backfill: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                    true
                }
                R.id.action_remove_role_field -> {
                    db.collection("users").get().addOnSuccessListener { result ->
                        try {
                            val batch = db.batch()
                            var count = 0
                            for (doc in result) {
                                if (doc.contains("role")) {
                                    batch.update(doc.reference, mapOf("role" to FieldValue.delete()))
                                    count++
                                }
                            }
                            if (count > 0) {
                                batch.commit().addOnSuccessListener {
                                    try {
                                        Toast.makeText(context, "Removed 'role' from $count user(s).", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e("FacultyMembersFragment", "Error after successful role removal", e)
                                        Toast.makeText(context, "Role removal completed but error showing message: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "No 'role' field found.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("FacultyMembersFragment", "Error processing role removal", e)
                            Toast.makeText(context, "Error processing role removal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("FacultyMembersFragment", "Error initiating role removal", exception)
                        Toast.makeText(context, "Error initiating role removal: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
        } catch (e: Exception) {
            Log.e("FacultyMembersFragment", "Error in onOptionsItemSelected", e)
            Toast.makeText(context, "Error handling menu option: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_FACULTY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Refresh the user list after successful edit
            fetchUsers()
        }
    }
}