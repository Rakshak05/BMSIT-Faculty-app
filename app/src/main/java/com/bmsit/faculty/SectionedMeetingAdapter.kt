package com.bmsit.faculty

import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import android.view.LayoutInflater
import com.bmsit.faculty.R
import com.bmsit.faculty.Meeting
import com.bmsit.faculty.CurrentMeeting
import com.bmsit.faculty.MeetingAdapter

sealed class MeetingListItem {
    data class Header(val title: String): MeetingListItem()
    data class Info(val message: String): MeetingListItem()
    data class Item(val meeting: Meeting): MeetingListItem()
    data class CurrentMeetingItem(val currentMeeting: CurrentMeeting): MeetingListItem()
}

class SectionedMeetingAdapter(
    private val items: List<MeetingListItem>,
    private val listener: MeetingAdapter.OnMeetingInteractionListener,
    private val currentUserId: String,
    private val userNamesMap: Map<String, String> // Map of user IDs to display names
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_HEADER = 0
    private val TYPE_INFO = 1
    private val TYPE_MEETING = 2
    private val TYPE_CURRENT_MEETING = 3

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MeetingListItem.Header -> TYPE_HEADER
            is MeetingListItem.Info -> TYPE_INFO
            is MeetingListItem.Item -> TYPE_MEETING
            is MeetingListItem.CurrentMeetingItem -> TYPE_CURRENT_MEETING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false))
            TYPE_INFO -> InfoVH(LayoutInflater.from(parent.context).inflate(R.layout.item_info_row, parent, false))
            TYPE_CURRENT_MEETING -> CurrentMeetingVH(LayoutInflater.from(parent.context).inflate(R.layout.item_current_meeting, parent, false))
            else -> MeetingVH(LayoutInflater.from(parent.context).inflate(R.layout.item_meeting, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MeetingListItem.Header -> (holder as HeaderVH).bind(item)
            is MeetingListItem.Info -> (holder as InfoVH).bind(item)
            is MeetingListItem.Item -> (holder as MeetingVH).bind(item.meeting)
            is MeetingListItem.CurrentMeetingItem -> (holder as CurrentMeetingVH).bind(item.currentMeeting)
        }
    }

    inner class HeaderVH(itemView: View): RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.textViewHeader)
        fun bind(header: MeetingListItem.Header) { title.text = header.title }
    }

    inner class InfoVH(itemView: View): RecyclerView.ViewHolder(itemView) {
        private val msg: TextView = itemView.findViewById(R.id.textViewInfo)
        fun bind(info: MeetingListItem.Info) { msg.text = info.message }
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
            Log.e("SectionedMeetingAdapter", "Error finding textViewMeetingTime", e)
            null
        }
        private val expandedSection: LinearLayout = itemView.findViewById(R.id.expandedSection)
        private val reminderButton: Button = itemView.findViewById(R.id.buttonSetReminder)
        private val schedulerActionsLayout: LinearLayout = itemView.findViewById(R.id.layoutSchedulerActions)
        private val editButton: Button = itemView.findViewById(R.id.buttonEditMeeting)
        private val cancelButton: Button = itemView.findViewById(R.id.buttonCancelMeeting)

        init {
            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val itm = items[pos]
                    if (itm is MeetingListItem.Item) {
                        // Check if this adapter is being used in MeetingsForDateActivity
                        // We can check the context or use a different approach
                        itm.meeting.isExpanded = !itm.meeting.isExpanded
                        // Animate expand/collapse
                        (itemView.parent as? ViewGroup)?.let { parent ->
                            TransitionManager.beginDelayedTransition(parent, AutoTransition())
                        }
                        notifyItemChanged(pos)
                    }
                }
            }
            reminderButton.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val itm = items[pos]
                    if (itm is MeetingListItem.Item) listener.onSetReminderClick(itm.meeting)
                }
            }
            editButton.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val itm = items[pos]
                    if (itm is MeetingListItem.Item) listener.onEditClick(itm.meeting)
                }
            }
            cancelButton.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val itm = items[pos]
                    if (itm is MeetingListItem.Item) listener.onCancelClick(itm.meeting)
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
                        // Meeting has ended, show duration
                        val startTime = meeting.dateTime.toDate()
                        val endTime = meeting.endTime!!.toDate()
                        val durationMillis = endTime.time - startTime.time
                        val hours = durationMillis / (1000 * 60 * 60)
                        val minutes = (durationMillis % (1000 * 60 * 60)) / (1000 * 60)
                        
                        // Format as HH:MM
                        timeView.text = String.format("%02d:%02d", hours, minutes)
                        timeView.visibility = View.VISIBLE
                    } else {
                        // Meeting hasn't ended yet, hide the time view
                        timeView.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e("SectionedMeetingAdapter", "Error calculating meeting duration", e)
                    timeView.visibility = View.GONE
                }
            }
            
            // Set host name
            val hostName = userNamesMap[meeting.scheduledBy] ?: "Unknown User"
            hostTextView.text = "Hosted by: $hostName"

            // Show scheduler actions only if current user is the scheduler
            schedulerActionsLayout.visibility = if (meeting.scheduledBy == currentUserId) View.VISIBLE else View.GONE

            // Show expanded section based on meeting.isExpanded
            expandedSection.visibility = if (meeting.isExpanded) View.VISIBLE else View.GONE
        }
    }

    inner class CurrentMeetingVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.textViewMeetingTitle)
        private val timeText: TextView = itemView.findViewById(R.id.textViewMeetingTime)
        private val dateTimeText: TextView = itemView.findViewById(R.id.textViewMeetingDateTime)
        private val locationText: TextView = itemView.findViewById(R.id.textViewMeetingLocation)
        private val attendeesText: TextView = itemView.findViewById(R.id.textViewMeetingAttendees)
        private val hostText: TextView = itemView.findViewById(R.id.textViewMeetingHost)
        private val endButton: Button = itemView.findViewById(R.id.buttonEndMeeting)
        private val expandedContent: View = itemView.findViewById(R.id.expandedContent)

        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val dateTimeFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        
        // Track expanded positions within this view holder
        private var isExpanded = false

        init {
            // Set click listener for expand/collapse on the entire item
            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    isExpanded = !isExpanded
                    expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    // Notify the adapter that this item has changed
                    // notifyItemChanged(pos)
                }
            }

            // Set click listener for the end meeting button
            endButton.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val item = items[pos]
                    if (item is MeetingListItem.CurrentMeetingItem) {
                        // Show confirmation dialog and then end the meeting
                        showEndMeetingConfirmation(item.currentMeeting, pos)
                    }
                }
            }
        }

        private fun showEndMeetingConfirmation(meeting: CurrentMeeting, position: Int) {
            val context = itemView.context
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("End Meeting")
                .setMessage("Do you want to end this meeting early?")
                .setPositiveButton("End Early") { _, _ -> 
                    endMeeting(meeting, position, context)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun endMeeting(meeting: CurrentMeeting, position: Int, context: android.content.Context) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val meetingRef = db.collection("meetings").document(meeting.id)
                
                android.util.Log.d("SectionedMeetingAdapter", "Attempting to end meeting: ${meeting.id}")
                
                // First check if the document exists
                meetingRef.get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            android.util.Log.d("SectionedMeetingAdapter", "Meeting document found: ${meeting.id}")
                            val endTime = com.google.firebase.Timestamp.now()
                            
                            meetingRef.update("endTime", endTime)
                                .addOnSuccessListener {
                                    android.widget.Toast.makeText(context, "Meeting ended successfully", android.widget.Toast.LENGTH_SHORT).show()
                                    android.util.Log.d("SectionedMeetingAdapter", "Meeting ended successfully: ${meeting.id}")
                                    
                                    // Prompt host to take attendance immediately
                                    showAttendancePrompt(context, meeting)
                                    
                                    // You might want to notify the adapter or refresh the data source
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("SectionedMeetingAdapter", "Error updating meeting: ${meeting.id}", e)
                                    android.widget.Toast.makeText(context, "Error updating meeting: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                        } else {
                            android.util.Log.e("SectionedMeetingAdapter", "Meeting document not found: ${meeting.id}")
                            android.widget.Toast.makeText(context, "Meeting not found in database", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("SectionedMeetingAdapter", "Error checking meeting existence: ${meeting.id}", e)
                        android.widget.Toast.makeText(context, "Error checking meeting: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                android.util.Log.e("SectionedMeetingAdapter", "Exception while ending meeting: ${meeting.id}", e)
                android.widget.Toast.makeText(context, "Exception ending meeting: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        private fun showAttendancePrompt(context: android.content.Context, meeting: CurrentMeeting) {
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Take Attendance")
                .setMessage("Would you like to take attendance for this meeting now?")
                .setPositiveButton("Take Attendance") { _, _ ->
                    showAttendanceDialog(context, meeting)
                }
                .setNegativeButton("Later") { _, _ ->
                    // User chose to take attendance later, do nothing
                    android.widget.Toast.makeText(context, "You can take attendance later from the calendar", android.widget.Toast.LENGTH_SHORT).show()
                }
                .show()
        }
        
        private fun showAttendanceDialog(context: android.content.Context, meeting: CurrentMeeting) {
            // For custom meetings, fetch the attendee UIDs from the meeting document
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("meetings").document(meeting.id).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val meetingData = document.toObject(com.bmsit.faculty.Meeting::class.java)
                        if (meetingData != null) {
                            when (meetingData.attendees) {
                                "Custom" -> {
                                    showAttendanceActivity(context, meeting, meetingData.customAttendeeUids)
                                }
                                "All Associate Prof" -> {
                                    fetchAndShowAttendance(context, meeting, listOf("Associate Professor"))
                                }
                                "All Assistant Prof" -> {
                                    fetchAndShowAttendance(context, meeting, listOf("Assistant Professor"))
                                }
                                "All Faculty" -> {
                                    fetchAndShowAttendance(context, meeting, listOf("Faculty", "Assistant Professor", "Associate Professor", "Lab Assistant", "HOD", "ADMIN", "Unassigned"))
                                }
                                else -> {
                                    android.widget.Toast.makeText(context, "Attendance can only be taken for custom meetings with specific attendees.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Error loading meeting data", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Meeting not found", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("SectionedMeetingAdapter", "Error loading meeting data", e)
                    android.widget.Toast.makeText(context, "Error loading meeting data: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
        }
        
        private fun fetchAndShowAttendance(context: android.content.Context, meeting: CurrentMeeting, designations: List<String>) {
            // Show loading message
            android.widget.Toast.makeText(context, "Fetching attendee list...", android.widget.Toast.LENGTH_SHORT).show()
            
            // Fetch users with specified designations
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users")
                .whereIn("designation", designations)
                .get()
                .addOnSuccessListener { result ->
                    val attendeeUids = result.map { it.id }
                    showAttendanceActivity(context, meeting, attendeeUids)
                }
                .addOnFailureListener { exception ->
                    android.widget.Toast.makeText(context, "Error fetching attendees: ${exception.message}", android.widget.Toast.LENGTH_LONG).show()
                }
        }
        
        private fun showAttendanceActivity(context: android.content.Context, meeting: CurrentMeeting, attendeeUids: List<String>) {
            if (attendeeUids.isEmpty()) {
                android.widget.Toast.makeText(context, "No attendees found for this meeting.", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            // Show attendance dialog/activity
            val intent = android.content.Intent(context, com.bmsit.faculty.AttendanceActivity::class.java).apply {
                putExtra("MEETING_ID", meeting.id)
                putExtra("MEETING_TITLE", meeting.title)
                putStringArrayListExtra("ATTENDEE_UIDS", ArrayList(attendeeUids))
            }
            context.startActivity(intent)
        }

        fun bind(meeting: CurrentMeeting) {
            titleText.text = meeting.title
            
            // Format time similar to upcoming meetings
            val meetingTime = meeting.dateTime
            timeText.text = timeFormat.format(meetingTime)
            dateTimeText.text = dateTimeFormat.format(meetingTime)
            
            locationText.text = "Location: ${meeting.location}"
            attendeesText.text = "For: ${meeting.attendees}"

            // Set host name
            val hostName = userNamesMap[meeting.scheduledBy] ?: "Unknown User"
            hostText.text = "Hosted by: $hostName"

            // Show end meeting button only for the host
            if (meeting.scheduledBy == currentUserId) {
                // Show end meeting button for host
                endButton.visibility = View.VISIBLE
            } else {
                endButton.visibility = View.GONE
            }
            
            // Handle expand/collapse state
            expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
    }
}