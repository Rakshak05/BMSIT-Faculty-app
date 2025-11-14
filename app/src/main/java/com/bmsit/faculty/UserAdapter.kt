package com.bmsit.faculty

import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.storage.FirebaseStorage

class UserAdapter(
    private val userList: List<User>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(user: User)
        // Removed onItemLongClick since we're removing long-press functionality
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val profileImageView: ImageView = itemView.findViewById(R.id.imageViewProfilePicture)
        val nameTextView: TextView = itemView.findViewById(R.id.textViewUserName)
        // REMOVED: emailTextView since email is no longer displayed
        // Get references to the new TextViews from the layout
        val departmentTextView: TextView = itemView.findViewById(R.id.textViewUserDepartment)
        val designationTextView: TextView = itemView.findViewById(R.id.textViewUserDesignation)

        init {
            itemView.setOnClickListener(this)
            // Removed long click listener since we're removing long-press functionality
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
        
        // Removed onLongClick since we're removing long-press functionality
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
            // REMOVED: Setting email text since email is no longer displayed
            // Set the text for the new fields
            holder.departmentTextView.text = "Dept: ${currentUser.department}"
            holder.designationTextView.text = "Designation: ${currentUser.designation}"
            
            // Set default profile image
            holder.profileImageView.setImageResource(R.drawable.universalpp)
            
            // Load profile picture
            loadProfilePicture(currentUser.uid, holder.profileImageView)
        } catch (e: Exception) {
            Log.e("UserAdapter", "Error in onBindViewHolder at position $position", e)
            // Set default values to prevent crashes
            holder.nameTextView.text = "Error loading user"
            // REMOVED: Setting email text since email is no longer displayed
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
    
    private fun loadProfilePicture(userId: String, imageView: ImageView) {
        try {
            // Reference to the profile picture in Firebase Storage with explicit bucket
            val storage = FirebaseStorage.getInstance("gs://bmsit-faculty-30834.firebasestorage.app")
            val storageRef = storage.getReference("profile_pictures/$userId.jpg")
            
            val ONE_MEGABYTE: Long = 1024 * 1024
            storageRef.getBytes(ONE_MEGABYTE).addOnSuccessListener { bytes ->
                // Successfully downloaded data, convert to bitmap and display
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imageView.setImageBitmap(bitmap)
            }.addOnFailureListener {
                // Handle any errors - default image is already set
                Log.d("UserAdapter", "No profile picture found for user: $userId, using default")
            }
        } catch (e: Exception) {
            Log.e("UserAdapter", "Error loading profile picture", e)
            // Default image is already set
        }
    }
}