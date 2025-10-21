package com.bmsit.faculty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class CalendarAdapter(
    private val daysOfMonth: ArrayList<Date?>,
    private val meetingsForMonth: List<Meeting>,
    private val onItemListener: (Date) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    inner class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayOfMonth: TextView = itemView.findViewById(R.id.textViewDayOfMonth)
        val meetingIndicator: View = itemView.findViewById(R.id.meetingIndicator)

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
            holder.meetingIndicator.visibility = View.INVISIBLE
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

            val hasMeeting = meetingsForMonth.any { meeting ->
                val meetingCalendar = Calendar.getInstance()
                meetingCalendar.time = meeting.dateTime.toDate()
                isSameDay(meetingCalendar, calendar)
            }
            holder.meetingIndicator.visibility = if (hasMeeting) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun getItemCount(): Int {
        return daysOfMonth.size
    }
    
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}