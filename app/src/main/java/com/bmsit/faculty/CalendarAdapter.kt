package com.bmsit.faculty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate

class CalendarAdapter(
    private val daysOfMonth: ArrayList<LocalDate?>,
    private val meetingsForMonth: List<Meeting>,
    private val onItemListener: (LocalDate) -> Unit
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
            holder.dayOfMonth.text = date.dayOfMonth.toString()
            if (date == LocalDate.now()) {
                holder.dayOfMonth.setBackgroundResource(R.drawable.ic_today_background)
            } else {
                holder.dayOfMonth.background = null
            }

            val hasMeeting = meetingsForMonth.any { meeting ->
                val meetingDate = meeting.dateTime.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                meetingDate.isEqual(date)
            }
            holder.meetingIndicator.visibility = if (hasMeeting) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun getItemCount(): Int {
        return daysOfMonth.size
    }
}
