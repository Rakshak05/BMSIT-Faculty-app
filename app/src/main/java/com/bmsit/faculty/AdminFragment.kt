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

class AdminFragment : Fragment(), UserAdapter.OnItemClickListener {

    private lateinit var db: FirebaseFirestore
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private val userList = mutableListOf<User>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        usersRecyclerView = view.findViewById(R.id.usersRecyclerView)
        usersRecyclerView.layoutManager = LinearLayoutManager(context)

        userAdapter = UserAdapter(userList, this)
        usersRecyclerView.adapter = userAdapter

        fetchUsers()
    }

    override fun onItemClick(user: User) {
        showEditUserDialog(user)
    }

    private fun showEditUserDialog(user: User) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_user, null)

        val nameEditText = dialogView.findViewById<EditText>(R.id.editTextUserName)
        val departmentSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDepartment)
        val designationSpinner = dialogView.findViewById<Spinner>(R.id.spinnerUserDesignation)

        // --- UPDATED: New list of departments ---
        val departments = arrayOf("Unassigned", "AIML", "CS", "CSBS", "EEE", "ETE", "ECE", "Mech", "Civil", "ISE")
        val designations = arrayOf("Unassigned", "Assistant Professor", "Associate Professor", "Professor", "Lab Assistant")

        departmentSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, departments)
        designationSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, designations)

        nameEditText.setText(user.name)
        departmentSpinner.setSelection(departments.indexOf(user.department).coerceAtLeast(0))
        designationSpinner.setSelection(designations.indexOf(user.designation).coerceAtLeast(0))

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Update") { dialog, which ->
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
                        Toast.makeText(context, "${user.name}'s profile updated.", Toast.LENGTH_SHORT).show()
                        fetchUsers()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Error updating profile.", Toast.LENGTH_SHORT).show()
                        Log.w("AdminFragment", "Error updating user document", e)
                    }
            }
            .show()
    }


    private fun fetchUsers() {
        db.collection("users")
            .get()
            .addOnSuccessListener { result ->
                userList.clear()
                for (document in result) {
                    val user = document.toObject(User::class.java)
                    userList.add(user)
                }
                userAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w("AdminFragment", "Error getting documents.", exception)
            }
    }
}

