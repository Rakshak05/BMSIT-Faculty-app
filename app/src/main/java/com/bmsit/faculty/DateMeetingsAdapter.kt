package com.bmsit.faculty

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import android.view.ViewGroup
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import java.util.*

class DateMeetingsAdapter(
    private val meetingList: List<Meeting>,
    private val listener: OnMeetingInteractionListener,
    private val currentUserId: String,
    private val userNamesMap: Map<String, String> // Map of user IDs to display names
): RecyclerView.Adapter<DateMeetingsAdapter.MeetingVH>() {

    interface OnMeetingInteractionListener {
        fun onMeetingClick(meeting: Meeting)
        fun onTakeAttendanceClick(meeting: Meeting) // Keep only this method
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeetingVH {
        return MeetingVH(LayoutInflater.from(parent.context).inflate(R.layout.item_meeting, parent, false))
    }

    override fun getItemCount(): Int = meetingList.size

    override fun onBindViewHolder(holder: MeetingVH, position: Int) {
        holder.bind(meetingList[position])
    }

    inner class MeetingVH(itemView: View): RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.textViewMeetingTitle)
        private val dateTimeTextView: TextView = itemView.findViewById(R.id.textViewMeetingDateTime)
        private val locationTextView: TextView = itemView.findViewById(R.id.textViewMeetingLocation)
        private val attendeesTextView: TextView = itemView.findViewById(R.id.textViewMeetingAttendees)
        private val hostTextView: TextView = itemView.findViewById(R.id.textViewMeetingHost)
        private val timeTextView: TextView? = try {
            itemView.findViewById(R.id.textViewMeetingTime)
        } catch (e: Exception) {
            Log.e("DateMeetingsAdapter", "Error finding textViewMeetingTime", e)
            null
        }
        private val expandedSection: LinearLayout = itemView.findViewById(R.id.expandedSection)
        
        // Add Take Attendance button
        private val takeAttendanceButton: Button = Button(itemView.context).apply {
            setText("Take Attendance")
            setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onTakeAttendanceClick(meetingList[pos])
                }
            }
        }

        init {
            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    // Check if meeting has started and ended before allowing expansion
                    val meeting = meetingList[pos]
                    if (canTakeAttendance(meeting)) {
                        // Toggle expanded state only if attendance can be taken
                        meeting.isExpanded = !meeting.isExpanded
                        notifyItemChanged(pos)
                    } else {
                        // Show a message why attendance cannot be taken
                        val context = itemView.context
                        if (!hasMeetingStarted(meeting)) {
                            android.widget.Toast.makeText(context, "Cannot take attendance before meeting starts", android.widget.Toast.LENGTH_SHORT).show()
                        } else if (!hasMeetingEnded(meeting)) {
                            android.widget.Toast.makeText(context, "Cannot take attendance before meeting ends", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        fun bind(meeting: Meeting) {
            titleTextView.text = meeting.title
            locationTextView.text = "Location: ${meeting.location}"
            attendeesTextView.text = "For: ${meeting.attendees}"
            val sdf = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            dateTimeTextView.text = sdf.format(meeting.dateTime.toDate())
            
            // Format and display meeting duration or hide for future meetings
            timeTextView?.let { timeView -> 
                try {
                    if (meeting.endTime != null) {
                        // For past meetings, show duration in HH:MM format
                        val startTime = meeting.dateTime.toDate()
                        val endTime = meeting.endTime.toDate()
                        val durationMillis = endTime.time - startTime.time
                        val durationHours = durationMillis / (1000 * 60 * 60)
                        val durationMinutes = (durationMillis / (1000 * 60)) % 60
                        
                        // Format duration as HH:MM
                        val durationFormatted = String.format("%02d:%02d", durationHours, durationMinutes)
                        timeView.text = "Duration: $durationFormatted"
                        timeView.visibility = View.VISIBLE
                    } else {
                        // For future meetings, hide the time view
                        timeView.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e("DateMeetingsAdapter", "Error formatting meeting duration", e)
                    timeView.visibility = View.GONE
                }
            }
            
            // Check if attendance can be taken before showing expanded section
            if (canTakeAttendance(meeting)) {
                // Handle expand/collapse
                expandedSection.visibility = if (meeting.isExpanded) View.VISIBLE else View.GONE
                val isScheduler = currentUserId == meeting.scheduledBy
                
                // Add Take Attendance button for hosts in the expanded section and center align it
                if (isScheduler) {
                    // Clear the expanded section and add only the Take Attendance button
                    expandedSection.removeAllViews()
                    
                    // Create a container for centering the button
                    val buttonContainer = LinearLayout(itemView.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    
                    // Configure the Take Attendance button
                    takeAttendanceButton.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    
                    // Add the button to the container
                    buttonContainer.addView(takeAttendanceButton)
                    
                    // Add the container to the expanded section
                    expandedSection.addView(buttonContainer)
                } else {
                    // For non-hosts, clear the expanded section
                    expandedSection.removeAllViews()
                }
            } else {
                // Hide expanded section if attendance cannot be taken
                expandedSection.visibility = View.GONE
            }
            
            // Display who hosted the meeting using the user names map
            val hostName = userNamesMap[meeting.scheduledBy] ?: "Unknown User"
            hostTextView.text = "Hosted by: $hostName"
            hostTextView.visibility = View.VISIBLE
        }
        
        // Check if the meeting has started
        private fun hasMeetingStarted(meeting: Meeting): Boolean {
            val currentTime = Date()
            val meetingStartTime = meeting.dateTime.toDate()
            return !meetingStartTime.after(currentTime)
        }
        
        // Check if the meeting has ended
        private fun hasMeetingEnded(meeting: Meeting): Boolean {
            // If meeting has an explicit end time, it has ended
            if (meeting.endTime != null) {
                return true
            }
            
            // Otherwise, check if the expected end time has passed
            val currentTime = Date()
            val meetingStartTime = meeting.dateTime.toDate()
            val calendar = Calendar.getInstance()
            calendar.time = meetingStartTime
            // Ensure duration is valid, default to 60 minutes if not set properly
            val duration = if (meeting.duration > 0) meeting.duration else 60
            calendar.add(Calendar.MINUTE, duration)
            val expectedEndTime = calendar.time
            
            return !currentTime.before(expectedEndTime)
        }
        
        // Check if attendance can be taken (meeting has started and ended)
        private fun canTakeAttendance(meeting: Meeting): Boolean {
            val isScheduler = currentUserId == meeting.scheduledBy
            return isScheduler && hasMeetingStarted(meeting) && hasMeetingEnded(meeting)
        }
    }
}