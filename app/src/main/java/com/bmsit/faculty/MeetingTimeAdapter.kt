package com.bmsit.faculty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MeetingTimeAdapter(
    private val meetings: List<Meeting>
) : RecyclerView.Adapter<MeetingTimeAdapter.MeetingTimeViewHolder>() {

    inner class MeetingTimeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeText: TextView = itemView.findViewById(R.id.textViewMeetingTime)
        val titleText: TextView = itemView.findViewById(R.id.textViewMeetingTitle)
        val locationText: TextView = itemView.findViewById(R.id.textViewMeetingLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeetingTimeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meeting_time, parent, false)
        return MeetingTimeViewHolder(view)
    }

    override fun onBindViewHolder(holder: MeetingTimeViewHolder, position: Int) {
        val meeting = meetings[position]
        
        // Format time range
        val startTime = meeting.dateTime.toDate()
        val calendar = Calendar.getInstance()
        calendar.time = startTime
        calendar.add(Calendar.MINUTE, meeting.duration)
        val endTime = calendar.time
        
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        holder.timeText.text = "${timeFormat.format(startTime)} - ${timeFormat.format(endTime)}"
        
        // Set meeting title
        holder.titleText.text = meeting.title
        
        // Set location
        holder.locationText.text = "Location: ${meeting.location}"
    }

    override fun getItemCount(): Int = meetings.size
}