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
    }

    override fun getItemCount() = filteredUsers.size

    @SuppressLint("NotifyDataSetChanged")
    fun filter(department: String, designation: String) {
        filteredUsers.clear()
        val tempUsers = allUsers.filter { user ->
            val departmentMatches = department == "All" || user.department == department
            val designationMatches = designation == "All" || user.designation == designation
            departmentMatches && designationMatches
        }
        filteredUsers.addAll(tempUsers)
        notifyDataSetChanged()
    }

    fun getSelectedUsers(): List<User> {
        return selectedUsers.toList()
    }
}
