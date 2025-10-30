package com.bmsit.faculty

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SelectableUserAdapter(private var allUsers: List<User>) : RecyclerView.Adapter<SelectableUserAdapter.UserViewHolder>() {

    private var filteredUsers = mutableListOf<User>()
    private val selectedUsers = mutableSetOf<User>()

    private var currentDepartment: String = "All"
    private var currentDesignation: String = "All"
    private var currentQuery: String = ""
    private var sortBy: SortBy = SortBy.DESIGNATION

    init {
        filteredUsers.addAll(allUsers)
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxAttendee)
        val nameTextView: TextView = itemView.findViewById(R.id.textViewAttendeeName)
        val detailsTextView: TextView = itemView.findViewById(R.id.textViewAttendeeDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendee_selectable, parent, false)
        return UserViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = filteredUsers[position]
        holder.nameTextView.text = user.name
        holder.detailsTextView.text = "${user.department}, ${user.designation}"

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedUsers.contains(user)

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedUsers.add(user)
            } else {
                selectedUsers.remove(user)
            }
        }

        // Also allow tapping the entire row to toggle selection for better usability
        holder.itemView.setOnClickListener {
            val currentlySelected = selectedUsers.contains(user)
            if (currentlySelected) {
                selectedUsers.remove(user)
                holder.checkBox.isChecked = false
            } else {
                selectedUsers.add(user)
                holder.checkBox.isChecked = true
            }
        }
    }

    override fun getItemCount() = filteredUsers.size

    enum class SortBy { DESIGNATION, DEPARTMENT }

    private fun designationRank(designation: String?): Int {
        return when (designation?.uppercase()) {
            "ADMIN" -> 7
            "DEAN" -> 6
            "HOD" -> 5
            "ASSOCIATE PROFESSOR" -> 3
            "ASSISTANT PROFESSOR" -> 2
            "LAB ASSISTANT" -> 1
            "OTHERS" -> 1
            else -> 0
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSortBy(sortBy: SortBy) {
        this.sortBy = sortBy
        applyFilters()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSearchQuery(query: String) {
        currentQuery = query.trim()
        applyFilters()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setFilters(department: String, designation: String) {
        currentDepartment = department
        currentDesignation = designation
        applyFilters()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyFilters() {
        val q = currentQuery.lowercase()
        val wantedDept = currentDepartment.trim()
        val wantedDesig = currentDesignation.trim()

        fun matchesDepartment(userDept: String): Boolean {
            if (wantedDept == "All") return true
            if (userDept.equals(wantedDept, ignoreCase = true)) return true
            // Tolerant contains both ways to handle abbreviations vs full names (e.g., CS vs Computer Science)
            val ud = userDept.lowercase()
            val wd = wantedDept.lowercase()
            return ud.contains(wd) || wd.contains(ud)
        }

        fun matchesDesignation(userDesig: String): Boolean {
            if (wantedDesig == "All") return true
            // Normalize to uppercase for robust comparison (e.g., Dean vs DEAN)
            return userDesig.equals(wantedDesig, ignoreCase = true)
        }

        val tmp = allUsers.filter { user ->
            val departmentMatches = matchesDepartment(user.department)
            val designationMatches = matchesDesignation(user.designation)
            val nameMatches = q.isEmpty() || user.name.lowercase().contains(q)
            departmentMatches && designationMatches && nameMatches
        }
        val sorted = when (sortBy) {
            SortBy.DESIGNATION -> tmp.sortedWith(compareByDescending<User> { designationRank(it.designation) }
                .thenBy { it.department }
                .thenBy { it.name })
            SortBy.DEPARTMENT -> tmp.sortedWith(compareBy<User> { it.department }
                .thenByDescending { designationRank(it.designation) }
                .thenBy { it.name })
        }
        filteredUsers.clear()
        filteredUsers.addAll(sorted)
        notifyDataSetChanged()
    }

    fun getSelectedUsers(): List<User> {
        return selectedUsers.toList()
    }
}
