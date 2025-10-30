package com.bmsit.faculty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.util.Log

class UserAdapter(
    private val userList: List<User>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(user: User)
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val nameTextView: TextView = itemView.findViewById(R.id.textViewUserName)
        val emailTextView: TextView = itemView.findViewById(R.id.textViewUserEmail)
        // Get references to the new TextViews from the layout
        val departmentTextView: TextView = itemView.findViewById(R.id.textViewUserDepartment)
        val designationTextView: TextView = itemView.findViewById(R.id.textViewUserDesignation)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            try {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(userList[position])
                }
            } catch (e: Exception) {
                Log.e("UserAdapter", "Error in onClick", e)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        return try {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
            UserViewHolder(itemView)
        } catch (e: Exception) {
            Log.e("UserAdapter", "Error in onCreateViewHolder", e)
            throw e // Re-throw to let the RecyclerView handle it
        }
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        try {
            val currentUser = userList[position]
            holder.nameTextView.text = currentUser.name
            holder.emailTextView.text = currentUser.email
            // Set the text for the new fields
            holder.departmentTextView.text = "Dept: ${currentUser.department}"
            holder.designationTextView.text = "Designation: ${currentUser.designation}"
        } catch (e: Exception) {
            Log.e("UserAdapter", "Error in onBindViewHolder at position $position", e)
            // Set default values to prevent crashes
            holder.nameTextView.text = "Error loading user"
            holder.emailTextView.text = ""
            holder.departmentTextView.text = ""
            holder.designationTextView.text = ""
        }
    }

    override fun getItemCount() = try {
        userList.size
    } catch (e: Exception) {
        Log.e("UserAdapter", "Error in getItemCount", e)
        0
    }
}

