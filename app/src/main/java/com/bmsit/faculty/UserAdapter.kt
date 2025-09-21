package com.bmsit.faculty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// We pass in a new "listener" so the adapter can talk back to the fragment.
class UserAdapter(
    private val userList: List<User>,
    private val listener: OnItemClickListener // <-- NEW
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    // --- NEW: This is an "interface". It's a contract that says
    // whoever uses this adapter must be able to handle an onItemClick event.
    interface OnItemClickListener {
        fun onItemClick(user: User)
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val nameTextView: TextView = itemView.findViewById(R.id.textViewUserName)
        val emailTextView: TextView = itemView.findViewById(R.id.textViewUserEmail)
        val roleTextView: TextView = itemView.findViewById(R.id.textViewUserRole)

        // --- NEW: Make the whole row clickable
        init {
            itemView.setOnClickListener(this)
        }

        // --- NEW: When a click happens, tell our listener
        override fun onClick(v: View?) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) { // Make sure the click is valid
                listener.onItemClick(userList[position])
            }
        }
    }

    // --- The rest of the file is the same as before ---
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val currentUser = userList[position]
        holder.nameTextView.text = currentUser.name
        holder.emailTextView.text = currentUser.email
        holder.roleTextView.text = "Role: ${currentUser.role}"
    }

    override fun getItemCount() = userList.size
}

