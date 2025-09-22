package com.bmsit.faculty

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SelectAttendeesActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: SelectableUserAdapter
    private val allUsers = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_attendees)

        db = FirebaseFirestore.getInstance()

        val departmentSpinner = findViewById<Spinner>(R.id.spinnerFilterDepartment)
        val designationSpinner = findViewById<Spinner>(R.id.spinnerFilterDesignation)
        val recyclerView = findViewById<RecyclerView>(R.id.attendeesRecyclerView)
        val doneButton = findViewById<Button>(R.id.buttonDone)

        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchAllUsers {
            adapter = SelectableUserAdapter(allUsers)
            recyclerView.adapter = adapter
            setupSpinners(departmentSpinner, designationSpinner)
        }

        doneButton.setOnClickListener {
            val selected = adapter.getSelectedUsers()
            if (selected.isEmpty()) {
                Toast.makeText(this, "Please select at least one attendee.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // --- UPDATED: Return the list of UIDs ---
            val selectedUids = ArrayList(selected.map { it.uid })
            val resultIntent = Intent()
            resultIntent.putStringArrayListExtra("SELECTED_UIDS", selectedUids)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun setupSpinners(departmentSpinner: Spinner, designationSpinner: Spinner) {
        val departments = arrayOf("All", "AIML", "CS", "CSBS", "EEE", "ETE", "ECE", "Mech", "Civil", "ISE")
        val designations = arrayOf("All", "Assistant Professor", "Associate Professor", "Professor", "Lab Assistant", "HOD")

        departmentSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, departments)
        designationSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, designations)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (::adapter.isInitialized) {
                    adapter.filter(departmentSpinner.selectedItem.toString(), designationSpinner.selectedItem.toString())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        departmentSpinner.onItemSelectedListener = listener
        designationSpinner.onItemSelectedListener = listener
    }

    private fun fetchAllUsers(onComplete: () -> Unit) {
        db.collection("users")
            .orderBy("name", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                allUsers.clear()
                for (document in result) {
                    allUsers.add(document.toObject(User::class.java))
                }
                onComplete()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load users.", Toast.LENGTH_SHORT).show()
            }
    }
}

