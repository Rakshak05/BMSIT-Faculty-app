package com.bmsit.faculty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItemClick(userList[position])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val currentUser = userList[position]
        holder.nameTextView.text = currentUser.name
        holder.emailTextView.text = currentUser.email
        // Set the text for the new fields
        holder.departmentTextView.text = "Dept: ${currentUser.department}"
        holder.designationTextView.text = "Designation: ${currentUser.designation}"
    }

    override fun getItemCount() = userList.size
}

