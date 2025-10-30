package com.bmsit.faculty

import android.app.AlertDialog
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class AdminFragment : Fragment(), UserAdapter.OnItemClickListener {

    private lateinit var db: FirebaseFirestore
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private val userList = mutableListOf<User>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            Log.d("AdminFragment", "onCreateView called")
            return inflater.inflate(R.layout.fragment_admin, container, false)
        } catch (e: Exception) {
            Log.e("AdminFragment", "Error in onCreateView", e)
            Toast.makeText(context, "Error initializing admin panel: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            super.onViewCreated(view, savedInstanceState)
            Log.d("AdminFragment", "onViewCreated called")

            db = FirebaseFirestore.getInstance()
            usersRecyclerView = view.findViewById(R.id.usersRecyclerView)
            usersRecyclerView.layoutManager = LinearLayoutManager(context)

            userAdapter = UserAdapter(userList, this)
            usersRecyclerView.adapter = userAdapter

            setHasOptionsMenu(true)
            fetchUsers()
            Log.d("AdminFragment", "onViewCreated completed successfully")
        } catch (e: Exception) {
            Log.e("AdminFragment", "Error in onViewCreated", e)
            Toast.makeText(context, "Error setting up admin panel: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onItemClick(user: User) {
        try {
            showEditUserDialog(user)
        } catch (e: Exception) {
            Log.e("AdminFragment", "Error in onItemClick", e)
            Toast.makeText(context, "Error editing user: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditUserDialog(user: User) {
        try {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_user, null)

            val nameEditText = dialogView.findViewById<EditText>(R.id.editTextUserName)
            val departmentSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDepartment)
            val designationSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDesignation)

            // --- UPDATED: New list of departments ---
            val departments = arrayOf("Unassigned", "AIML", "CS", "CSBS", "EEE", "ETE", "ECE", "Mech", "Civil", "ISE")
            // Order by authority level (high -> low), then include Others and Unassigned at end
            val designations = arrayOf("ADMIN", "DEAN", "HOD", "Associate Professor", "Assistant Professor", "Lab Assistant", "Others", "Unassigned")

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
                                    Log.e("AdminFragment", "Error after successful update", e)
                                    Toast.makeText(context, "Profile updated but error refreshing list: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error updating profile.", Toast.LENGTH_SHORT).show()
                                Log.w("AdminFragment", "Error updating user document", e)
                            }
                    } catch (e: Exception) {
                        Log.e("AdminFragment", "Error in update dialog", e)
                        Toast.makeText(context, "Error processing update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e("AdminFragment", "Error in showEditUserDialog", e)
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
                        Log.d("AdminFragment", "Successfully fetched ${userList.size} users")
                    } catch (e: Exception) {
                        Log.e("AdminFragment", "Error processing fetched users", e)
                        Toast.makeText(context, "Error processing user data: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.w("AdminFragment", "Error getting documents.", exception)
                    Toast.makeText(context, "Error loading users: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e("AdminFragment", "Error in fetchUsers", e)
            Toast.makeText(context, "Error initiating user fetch: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun designationRank(designation: String?): Int {
        return when (designation?.uppercase()) {
            "ADMIN" -> 7
            "DEAN" -> 7
            "HOD" -> 5
            "ASSOCIATE PROFESSOR" -> 3
            "ASSISTANT PROFESSOR" -> 2
            "LAB ASSISTANT" -> 1
            "OTHERS" -> 1
            else -> 0 // Unassigned or unknown
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        try {
            super.onCreateOptionsMenu(menu, inflater)
            inflater.inflate(R.menu.admin_menu, menu)
        } catch (e: Exception) {
            Log.e("AdminFragment", "Error in onCreateOptionsMenu", e)
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
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
                                            Log.e("AdminFragment", "Error after successful backfill", e)
                                            Toast.makeText(context, "Backfill completed but error refreshing list: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "No users required backfill.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("AdminFragment", "Error processing backfill", e)
                                Toast.makeText(context, "Error processing backfill: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("AdminFragment", "Error initiating backfill", exception)
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
                                        Log.e("AdminFragment", "Error after successful role removal", e)
                                        Toast.makeText(context, "Role removal completed but error showing message: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "No 'role' field found.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("AdminFragment", "Error processing role removal", e)
                            Toast.makeText(context, "Error processing role removal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("AdminFragment", "Error initiating role removal", exception)
                        Toast.makeText(context, "Error initiating role removal: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
        } catch (e: Exception) {
            Log.e("AdminFragment", "Error in onOptionsItemSelected", e)
            Toast.makeText(context, "Error handling menu option: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}