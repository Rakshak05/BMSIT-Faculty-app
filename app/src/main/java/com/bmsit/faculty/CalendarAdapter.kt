package com.bmsit.faculty

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CalendarAdapter(
    private val daysOfMonth: ArrayList<Date?>,
    private val meetingsForMonth: List<Meeting>,
    private val currentUserId: String,
    private val onItemListener: (Date) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    inner class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayOfMonth: TextView = itemView.findViewById(R.id.textViewDayOfMonth)
        val meetingsContainer: LinearLayout = itemView.findViewById(R.id.meetingsContainer)
        val indicatorContainer: LinearLayout = itemView.findViewById(R.id.indicatorContainer)
        val meetingIndicator: View = itemView.findViewById(R.id.meetingIndicator)
        val durationText: TextView = itemView.findViewById(R.id.textViewDuration)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    daysOfMonth[position]?.let { date ->
                        onItemListener(date)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.calendar_cell, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val date = daysOfMonth[position]

        if (date == null) {
            holder.dayOfMonth.text = ""
            holder.meetingsContainer.visibility = View.GONE
            holder.indicatorContainer.visibility = View.VISIBLE
            holder.meetingIndicator.visibility = View.INVISIBLE
            holder.durationText.visibility = View.GONE
        } else {
            val calendar = Calendar.getInstance()
            calendar.time = date
            holder.dayOfMonth.text = calendar.get(Calendar.DAY_OF_MONTH).toString()

            val today = Calendar.getInstance()
            if (isSameDay(calendar, today)) {
                holder.dayOfMonth.setBackgroundResource(R.drawable.ic_today_background)
            } else {
                holder.dayOfMonth.background = null
            }

            // Check for meetings on this date
            val meetingsOnDate = meetingsForMonth.filter { meeting ->
                val meetingCalendar = Calendar.getInstance()
                meetingCalendar.time = meeting.dateTime.toDate()
                isSameDay(meetingCalendar, calendar)
            }

            // Show detailed meeting information or indicators
            if (meetingsOnDate.isNotEmpty()) {
                holder.indicatorContainer.visibility = View.GONE
                holder.meetingsContainer.visibility = View.VISIBLE
                holder.meetingsContainer.removeAllViews()

                // Show up to 2 meetings with details, more will be indicated with a count
                val meetingsToShow = if (meetingsOnDate.size > 2) meetingsOnDate.take(2) else meetingsOnDate
                val remainingCount = meetingsOnDate.size - meetingsToShow.size

                for (meeting in meetingsToShow) {
                    val meetingView = createMeetingView(holder.itemView.context, meeting)
                    holder.meetingsContainer.addView(meetingView)
                }

                // If there are more meetings, show a count
                if (remainingCount > 0) {
                    val moreTextView = TextView(holder.itemView.context)
                    moreTextView.text = "+$remainingCount more"
                    moreTextView.textSize = 10f
                    moreTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                    holder.meetingsContainer.addView(moreTextView)
                }
            } else {
                // No meetings, show default indicator
                holder.meetingsContainer.visibility = View.GONE
                holder.indicatorContainer.visibility = View.VISIBLE

                // Determine meeting types for coloring
                val hostedMeetings = meetingsOnDate.filter { it.scheduledBy == currentUserId }
                val attendedMeetings = meetingsOnDate.filter { it.scheduledBy != currentUserId }

                // Check for missed meetings (attended meetings that have passed and no attendance record)
                val missedMeetings = attendedMeetings.filter { meeting ->
                    try {
                        meeting.dateTime.toDate().before(Date()) &&
                        !hasAttendanceRecord(meeting) // This would check if attendance was taken
                    } catch (e: Exception) {
                        // If there's an error in date comparison, assume no missed meetings
                        false
                    }
                }

                // Set indicator visibility and color based on meeting types
                when {
                    missedMeetings.isNotEmpty() -> {
                        // Missed meetings - show red indicator
                        holder.meetingIndicator.visibility = View.VISIBLE
                        holder.meetingIndicator.setBackgroundResource(R.drawable.ic_meeting_indicator_missed)
                    }
                    hostedMeetings.isNotEmpty() && attendedMeetings.isNotEmpty() -> {
                        // Both hosted and attended meetings - show special indicator (purple)
                        holder.meetingIndicator.visibility = View.VISIBLE
                        holder.meetingIndicator.setBackgroundResource(R.drawable.ic_meeting_indicator_both)
                    }
                    hostedMeetings.isNotEmpty() -> {
                        // Only hosted meetings - show hosted indicator (green)
                        holder.meetingIndicator.visibility = View.VISIBLE
                        holder.meetingIndicator.setBackgroundResource(R.drawable.ic_meeting_indicator_hosted)
                    }
                    attendedMeetings.isNotEmpty() -> {
                        // Only attended meetings - show attended indicator (blue)
                        holder.meetingIndicator.visibility = View.VISIBLE
                        holder.meetingIndicator.setBackgroundResource(R.drawable.ic_meeting_indicator)
                    }
                    else -> {
                        // No meetings
                        holder.meetingIndicator.visibility = View.INVISIBLE
                    }
                }

                // Show duration information for meetings that have ended
                val endedMeeting = meetingsOnDate.find { it.endTime != null }
                if (endedMeeting != null && endedMeeting.endTime != null) {
                    try {
                        val startTime = endedMeeting.dateTime.toDate()
                        val endTime = endedMeeting.endTime.toDate()
                        val durationMillis = endTime.time - startTime.time
                        val durationMinutes = (durationMillis / (1000 * 60)).toInt()

                        holder.durationText.text = "${durationMinutes}m"
                        holder.durationText.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        // If there's an error in duration calculation, hide the duration text
                        holder.durationText.visibility = View.GONE
                    }
                } else {
                    holder.durationText.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Create a view to display meeting details in the calendar cell
     */
    private fun createMeetingView(context: android.content.Context, meeting: Meeting): View {
        val meetingLayout = LinearLayout(context)
        meetingLayout.orientation = LinearLayout.VERTICAL
        meetingLayout.setPadding(2, 2, 2, 2)

        // Set background color based on meeting type
        val backgroundColor = if (meeting.scheduledBy == currentUserId) {
            // Hosted meeting - green
            ContextCompat.getColor(context, R.color.hosted_meeting_background)
        } else {
            // Attended meeting - blue
            ContextCompat.getColor(context, R.color.attended_meeting_background)
        }
        meetingLayout.setBackgroundColor(backgroundColor)

        // Meeting title
        val titleText = TextView(context)
        titleText.text = meeting.title
        titleText.textSize = 10f
        titleText.setTextColor(Color.WHITE)
        titleText.maxLines = 1
        titleText.ellipsize = android.text.TextUtils.TruncateAt.END

        // Meeting time or duration
        val timeText = TextView(context)
        timeText.textSize = 8f
        timeText.setTextColor(Color.WHITE)

        if (meeting.endTime != null) {
            // For past meetings, show duration in HH:MM format
            try {
                val startTime = meeting.dateTime.toDate()
                val endTime = meeting.endTime.toDate()
                val durationMillis = endTime.time - startTime.time
                val durationHours = durationMillis / (1000 * 60 * 60)
                val durationMinutes = (durationMillis / (1000 * 60)) % 60

                // Format duration as HH:MM
                val durationFormatted = String.format("%02d:%02d", durationHours, durationMinutes)
                timeText.text = "Duration: $durationFormatted"
            } catch (e: Exception) {
                Log.e("CalendarAdapter", "Error calculating meeting duration", e)
                // Fallback to showing start-end time range
                val startTime = meeting.dateTime.toDate()
                val calendar = Calendar.getInstance()
                calendar.time = startTime
                // Ensure duration is valid, default to 60 minutes if not set properly
                val duration = if (meeting.duration > 0) meeting.duration else 60
                calendar.add(Calendar.MINUTE, duration)
                val endTime = calendar.time
                timeText.text = "${timeFormat.format(startTime)} - ${timeFormat.format(endTime)}"
            }
        } else {
            // For future meetings, show the scheduled time range
            val startTime = meeting.dateTime.toDate()
            val calendar = Calendar.getInstance()
            calendar.time = startTime
            // Ensure duration is valid, default to 60 minutes if not set properly
            val duration = if (meeting.duration > 0) meeting.duration else 60
            calendar.add(Calendar.MINUTE, duration)
            val endTime = calendar.time
            timeText.text = "${timeFormat.format(startTime)} - ${timeFormat.format(endTime)}"
        }

        // Add views to layout
        meetingLayout.addView(titleText)
        meetingLayout.addView(timeText)

        // Get host name
        db.collection("users").document(meeting.scheduledBy).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val hostName = document.getString("name") ?: "Unknown Host"
                    val hostText = TextView(context)
                    hostText.text = "by $hostName"
                    hostText.textSize = 8f
                    hostText.setTextColor(Color.WHITE)
                    // Add host text at the end
                    meetingLayout.addView(hostText)
                }
            }
            .addOnFailureListener {
                // If we can't get host info, just show "Unknown Host"
                val hostText = TextView(context)
                hostText.text = "by Unknown Host"
                hostText.textSize = 8f
                hostText.setTextColor(Color.WHITE)
                // Add host text at the end
                meetingLayout.addView(hostText)
            }

        return meetingLayout
    }

    // Placeholder function - in a real implementation, this would check if attendance was recorded
    private fun hasAttendanceRecord(@Suppress("UNUSED_PARAMETER") meeting: Meeting): Boolean {
        // For now, we'll assume no attendance record exists
        // In a real implementation, this would check Firestore for attendance data
        return false
    }

    override fun getItemCount(): Int {
        return daysOfMonth.size
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}