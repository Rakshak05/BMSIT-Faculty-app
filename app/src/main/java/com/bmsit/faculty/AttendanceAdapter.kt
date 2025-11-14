package com.bmsit.faculty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AttendanceAdapter(
    private val attendees: List<Attendee>
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    inner class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.textViewAttendeeName)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxAttendance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendee, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val attendee = attendees[position]
        holder.nameText.text = attendee.name
        holder.checkBox.isChecked = attendee.isPresent
        
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            attendee.isPresent = isChecked
        }
    }

    override fun getItemCount(): Int = attendees.size
}