package com.bmsit.faculty

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
            loadProfilePicture(currentUser.uid, holder.profileImageView, currentUser.name)
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
    
    private fun loadProfilePicture(userId: String, imageView: ImageView, userName: String) {
        try {
            // Show user initials instead of profile picture
            showUserInitials(imageView, userName)
        } catch (e: Exception) {
            Log.e("UserAdapter", "Error loading profile picture", e)
            // Show user initials as fallback
            showUserInitials(imageView, "U")
        }
    }
    
    private fun showUserInitials(imageView: ImageView, userName: String) {
        try {
            // Extract initials from the user's name
            val initials = getUserInitials(userName)
            
            // Create a bitmap with the initials
            val bitmap = createInitialsBitmap(initials)
            
            // Set the bitmap to the ImageView
            imageView.setImageBitmap(bitmap)
            imageView.background = null // Remove any background
        } catch (e: Exception) {
            Log.e("UserAdapter", "Error creating initials bitmap", e)
            // Fallback to default image if there's an error
            imageView.setImageResource(R.drawable.universalpp)
        }
    }
    
    private fun getUserInitials(name: String): String {
        return try {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) return "U"
            
            val names = trimmedName.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            when (names.size) {
                0 -> "U"
                1 -> names[0].firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                else -> {
                    val firstInitial = names[0].firstOrNull()?.uppercaseChar()?.toString() ?: ""
                    val lastInitial = names[names.size - 1].firstOrNull()?.uppercaseChar()?.toString() ?: ""
                    firstInitial + lastInitial
                }
            }
        } catch (e: Exception) {
            Log.e("UserAdapter", "Error extracting initials from name: $name", e)
            "U"
        }
    }
    
    private fun createInitialsBitmap(initials: String): Bitmap {
        // Define the size of the bitmap (in pixels)
        val size = 200
        
        // Create a bitmap and canvas
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background (blue color) - using a fixed color instead of context
        val backgroundPaint = Paint().apply {
            color = Color.parseColor("#FF6200EE") // Using purple_500 color directly
            isAntiAlias = true
        }
        canvas.drawCircle((size / 2).toFloat(), (size / 2).toFloat(), (size / 2).toFloat(), backgroundPaint)
        
        // Draw text (initials)
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size / 2.toFloat()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        
        // Measure text to center it
        val textBounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, textBounds)
        
        // Draw the text centered
        val x = size / 2.toFloat()
        val y = (size / 2 + (textBounds.bottom - textBounds.top) / 2).toFloat()
        canvas.drawText(initials, x, y, textPaint)
        
        return bitmap
    }
}