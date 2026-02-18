package com.bmsit.faculty

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import java.text.SimpleDateFormat
import java.util.*

class MeetingAdapter(
    private val meetingList: List<Meeting>,
    private val listener: OnMeetingInteractionListener,
    private val currentUserId: String // Pass in the current user's ID to check against the scheduler
) : RecyclerView.Adapter<MeetingAdapter.MeetingViewHolder>() {

    // The contract is expanded to include edit and cancel clicks
    interface OnMeetingInteractionListener {
        fun onSetReminderClick(meeting: Meeting)
        fun onEditClick(meeting: Meeting)
        fun onCancelClick(meeting: Meeting)
    }

    inner class MeetingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Get references to all the views in the layout
        val titleTextView: TextView = itemView.findViewById(R.id.textViewMeetingTitle)
        val dateTimeTextView: TextView = itemView.findViewById(R.id.textViewMeetingDateTime)
        val locationTextView: TextView = itemView.findViewById(R.id.textViewMeetingLocation)
        val attendeesTextView: TextView = itemView.findViewById(R.id.textViewMeetingAttendees)
        val timeTextView: TextView? = try {
            itemView.findViewById(R.id.textViewMeetingTime)
        } catch (e: Exception) {
            Log.e("MeetingAdapter", "Error finding textViewMeetingTime", e)
            null
        }
        val expandedSection: LinearLayout = itemView.findViewById(R.id.expandedSection)
        val reminderButton: Button = itemView.findViewById(R.id.buttonSetReminder)
        val schedulerActionsLayout: LinearLayout = itemView.findViewById(R.id.layoutSchedulerActions)
        val editButton: Button = itemView.findViewById(R.id.buttonEditMeeting)
        val cancelButton: Button = itemView.findViewById(R.id.buttonCancelMeeting)


        init {
            // Set a listener on the whole card to handle expanding/collapsing
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val meeting = meetingList[position]
                    meeting.isExpanded = !meeting.isExpanded
                    // Animate expand/collapse
                    (itemView.parent as? ViewGroup)?.let { parent ->
                        TransitionManager.beginDelayedTransition(parent, AutoTransition())
                    }
                    notifyItemChanged(position) // Refresh this item to show/hide the section
                }
            }

            // Set listeners for all the buttons inside the card
            reminderButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) listener.onSetReminderClick(meetingList[position])
            }
            editButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) listener.onEditClick(meetingList[position])
            }
            cancelButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) listener.onCancelClick(meetingList[position])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeetingViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_meeting, parent, false)
        return MeetingViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MeetingViewHolder, position: Int) {
        val currentMeeting = meetingList[position]

        // Set the basic meeting info
        holder.titleTextView.text = currentMeeting.title
        holder.locationTextView.text = "Location: ${currentMeeting.location}"
        holder.attendeesTextView.text = "For: ${currentMeeting.attendees}"

        // Format and set the date/time
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        holder.dateTimeTextView.text = sdf.format(currentMeeting.dateTime.toDate())
        
        // Format and display meeting duration or hide for future meetings
        holder.timeTextView?.let { timeView ->
            try {
                if (currentMeeting.endTime != null) {
                    // For past meetings, show duration in HH:MM format
                    val startTime = currentMeeting.dateTime.toDate()
                    val endTime = currentMeeting.endTime.toDate()
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
                Log.e("MeetingAdapter", "Error formatting meeting duration", e)
                timeView.visibility = View.GONE
            }
        }

        // Show or hide the entire expanded section based on the user's click
        holder.expandedSection.visibility = if (currentMeeting.isExpanded) View.VISIBLE else View.GONE

        // IMPORTANT: Show scheduler actions only if the logged-in user is the one who created the meeting
        val isScheduler = currentUserId == currentMeeting.scheduledBy
        holder.schedulerActionsLayout.visibility = if (isScheduler) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = meetingList.size
}