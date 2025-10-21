package com.bmsit.faculty

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

sealed class MeetingListItem {
    data class Header(val title: String): MeetingListItem()
    data class Info(val message: String): MeetingListItem()
    data class Item(val meeting: Meeting): MeetingListItem()
}

class SectionedMeetingAdapter(
    private val items: List<MeetingListItem>,
    private val listener: MeetingAdapter.OnMeetingInteractionListener,
    private val currentUserId: String
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_HEADER = 0
    private val TYPE_INFO = 1
    private val TYPE_MEETING = 2

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MeetingListItem.Header -> TYPE_HEADER
            is MeetingListItem.Info -> TYPE_INFO
            is MeetingListItem.Item -> TYPE_MEETING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false))
            TYPE_INFO -> InfoVH(LayoutInflater.from(parent.context).inflate(R.layout.item_info_row, parent, false))
            else -> MeetingVH(LayoutInflater.from(parent.context).inflate(R.layout.item_meeting, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MeetingListItem.Header -> (holder as HeaderVH).bind(item)
            is MeetingListItem.Info -> (holder as InfoVH).bind(item)
            is MeetingListItem.Item -> (holder as MeetingVH).bind(item.meeting)
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
            expandedSection.visibility = if (meeting.isExpanded) View.VISIBLE else View.GONE
            val isScheduler = currentUserId == meeting.scheduledBy
            schedulerActionsLayout.visibility = if (isScheduler) View.VISIBLE else View.GONE
        }
    }
}
