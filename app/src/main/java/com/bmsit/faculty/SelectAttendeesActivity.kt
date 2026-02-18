package com.bmsit.faculty

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.text.Editable
import android.text.TextWatcher

class SelectAttendeesActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: SelectableUserAdapter
    private val allUsers = mutableListOf<User>()
    private var preSelectedUids: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_attendees)

        db = FirebaseFirestore.getInstance()
        
        // Get pre-selected UIDs from intent if available
        preSelectedUids = intent.getStringArrayListExtra("SELECTED_UIDS")

        val departmentSpinner = findViewById<Spinner>(R.id.spinnerFilterDepartment)
        val designationSpinner = findViewById<Spinner>(R.id.spinnerFilterDesignation)
        val recyclerView = findViewById<RecyclerView>(R.id.attendeesRecyclerView)
        val doneButton = findViewById<Button>(R.id.buttonDone)
        val searchEditText = findViewById<EditText>(R.id.editTextSearchUser)
        val sortGroup = findViewById<RadioGroup>(R.id.radioGroupSort)
        val selectAllButton = findViewById<Button>(R.id.buttonSelectAll)

        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchAllUsers {
            adapter = SelectableUserAdapter(allUsers)
            recyclerView.adapter = adapter
            
            // Pre-select users if UIDs were provided
            if (preSelectedUids != null) {
                preSelectUsers(preSelectedUids!!)
            }
            
            setupSpinners(departmentSpinner, designationSpinner)
            // Defaults
            adapter.setFilters(department = "All", designation = "All")
            adapter.setSortBy(SelectableUserAdapter.SortBy.DESIGNATION)
            
            // Update select all button visibility
            updateSelectAllButtonVisibility(selectAllButton)
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

        selectAllButton.setOnClickListener {
            adapter.selectAllFilteredUsers()
            updateSelectAllButtonVisibility(selectAllButton)
        }

        // Search wiring
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (::adapter.isInitialized) {
                    adapter.setSearchQuery(s?.toString() ?: "")
                    updateSelectAllButtonVisibility(selectAllButton)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Sort wiring
        sortGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!::adapter.isInitialized) return@setOnCheckedChangeListener
            when (checkedId) {
                R.id.radioSortDesignation -> {
                    adapter.setSortBy(SelectableUserAdapter.SortBy.DESIGNATION)
                    updateSelectAllButtonVisibility(selectAllButton)
                }
                R.id.radioSortDepartment -> {
                    adapter.setSortBy(SelectableUserAdapter.SortBy.DEPARTMENT)
                    updateSelectAllButtonVisibility(selectAllButton)
                }
            }
        }
    }
    
    private fun updateSelectAllButtonVisibility(selectAllButton: Button) {
        if (::adapter.isInitialized) {
            if (adapter.areFiltersApplied() && adapter.getFilteredUsersCount() > 0) {
                selectAllButton.visibility = View.VISIBLE
            } else {
                selectAllButton.visibility = View.GONE
            }
        }
    }
    
    private fun preSelectUsers(uids: ArrayList<String>) {
        // Find users with matching UIDs and pre-select them
        val usersToSelect = allUsers.filter { uids.contains(it.uid) }
        for (user in usersToSelect) {
            adapter.selectUser(user)
        }
    }

    private fun setupSpinners(departmentSpinner: Spinner, designationSpinner: Spinner) {
        // Updated to include all departments in alphabetical order
        val departments = arrayOf("All", "AIML", "Civil", "CS", "CSBS", "ECE", "EEE", "ETE", "ISE", "MECH")

        // Updated designation options - added "unassigned" and removed "dean"
        val designations = arrayOf("All", "Associate Professor", "Assistant Professor", "Faculty", "Unassigned", "Custom")

        departmentSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, departments)
        designationSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, designations)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (::adapter.isInitialized) {
                    adapter.setFilters(departmentSpinner.selectedItem.toString(), designationSpinner.selectedItem.toString())
                    updateSelectAllButtonVisibility(findViewById(R.id.buttonSelectAll))
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