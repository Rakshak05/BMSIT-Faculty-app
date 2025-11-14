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
        fun onSetReminderClick(meeting: Meeting)
        fun onEditClick(meeting: Meeting)
        fun onCancelClick(meeting: Meeting)
        // Removed onMinutesOfMeetingClick method since we're removing the button
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
        // Removed minutesOfMeetingButton reference
        private val expandedSection: LinearLayout = itemView.findViewById(R.id.expandedSection)
        private val reminderButton: Button = itemView.findViewById(R.id.buttonSetReminder)
        private val schedulerActionsLayout: LinearLayout = itemView.findViewById(R.id.layoutSchedulerActions)
        private val editButton: Button = itemView.findViewById(R.id.buttonEditMeeting)
        private val cancelButton: Button = itemView.findViewById(R.id.buttonCancelMeeting)

        init {
            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onMeetingClick(meetingList[pos])
                }
            }
            reminderButton.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onSetReminderClick(meetingList[pos])
                }
            }
            editButton.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onEditClick(meetingList[pos])
                }
            }
            cancelButton.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onCancelClick(meetingList[pos])
                }
            }
            // Removed minutesOfMeetingButton click listener
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
            
            expandedSection.visibility = if (meeting.isExpanded) View.VISIBLE else View.GONE
            val isScheduler = currentUserId == meeting.scheduledBy
            schedulerActionsLayout.visibility = if (isScheduler) View.VISIBLE else View.GONE
            
            // Display who hosted the meeting using the user names map
            val hostName = userNamesMap[meeting.scheduledBy] ?: "Unknown User"
            hostTextView.text = "Hosted by: $hostName"
            hostTextView.visibility = View.VISIBLE
        }
    }
}