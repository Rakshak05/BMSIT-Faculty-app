package com.bmsit.faculty

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AttendedMeeting(
    val id: String,
    val title: String,
    val dateTime: Date,
    val hostId: String,
    val location: String,
    val startTime: Date,
    val endTime: Date?
)

class AttendedMeetingsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: AttendedMeetingsAdapter
    private val attendedMeetings = mutableListOf<AttendedMeeting>()
    private var targetUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attended_meetings)

        // Get the target user ID from intent extras
        targetUserId = intent.getStringExtra("TARGET_USER_ID")

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewAttendedMeetings)
        val progressBar = findViewById<View>(R.id.progressBar)
        val emptyState = findViewById<TextView>(R.id.textViewEmptyState)

        adapter = AttendedMeetingsAdapter(attendedMeetings)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fetchAttendedMeetings(progressBar, emptyState)
    }

    private fun fetchAttendedMeetings(progressBar: View, emptyState: TextView) {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        // Use target user ID if provided, otherwise use current user ID
        val userId = targetUserId ?: auth.currentUser?.uid ?: return

        db.collection("meetings")
            .get()
            .addOnSuccessListener { result ->
                attendedMeetings.clear()

                for (document in result) {
                    val meeting = document.toObject(Meeting::class.java)
                    val meetingDate = meeting.dateTime.toDate()
                    val currentDate = Date()

                    // Check if user was involved in this meeting (either as host or attendee)
                    val isUserHost = meeting.scheduledBy == userId
                    val isUserAttendee = meeting.attendees == "Custom" && meeting.customAttendeeUids.contains(userId)
                    val isUserInvolved = isUserHost || isUserAttendee

                    // If user was involved and meeting has an end time (indicating it was conducted), it's an attended meeting
                    // Only include past meetings that have actually ended (have an end time)
                    if (isUserInvolved && meetingDate.before(currentDate) && meeting.endTime != null) {
                        attendedMeetings.add(
                            AttendedMeeting(
                                id = meeting.id,
                                title = meeting.title,
                                dateTime = meetingDate,
                                hostId = meeting.scheduledBy,
                                location = meeting.location,
                                startTime = meetingDate,
                                endTime = meeting.endTime.toDate()
                            )
                        )
                    }
                }

                // Sort meetings by date (most recent first)
                attendedMeetings.sortByDescending { it.dateTime }

                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE

                if (attendedMeetings.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { exception ->
                Log.e("AttendedMeetingsActivity", "Error fetching meetings: ", exception)
                progressBar.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            }
    }
}

class AttendedMeetingsAdapter(private val meetings: List<AttendedMeeting>) :
    RecyclerView.Adapter<AttendedMeetingsAdapter.AttendedMeetingViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

    class AttendedMeetingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.textViewMeetingTitle)
        val dateTimeText: TextView = view.findViewById(R.id.textViewMeetingDateTime)
        val hostText: TextView = view.findViewById(R.id.textViewMeetingHost)
        val locationText: TextView = view.findViewById(R.id.textViewMeetingLocation)
        val durationText: TextView = view.findViewById(R.id.textViewMeetingDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendedMeetingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attended_meeting, parent, false)
        return AttendedMeetingViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendedMeetingViewHolder, position: Int) {
        val meeting = meetings[position]
        holder.titleText.text = meeting.title
        holder.dateTimeText.text = dateFormat.format(meeting.dateTime)
        holder.locationText.text = "Location: ${meeting.location}"

        // Calculate and display duration if meeting has ended
        if (meeting.endTime != null) {
            val durationMillis = meeting.endTime.time - meeting.startTime.time
            val durationHours = durationMillis / (1000 * 60 * 60)
            val durationMinutes = (durationMillis / (1000 * 60)) % 60
            
            // Format duration as HH:MM
            val durationFormatted = String.format("%02d:%02d", durationHours, durationMinutes)
            holder.durationText.text = "Duration: $durationFormatted (HH:MM)"
        } else {
            holder.durationText.text = "Duration: Not yet completed"
        }

        // Fetch host name
        db.collection("users").document(meeting.hostId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name") ?: "Unknown User"
                    holder.hostText.text = "Hosted by: $name"
                } else {
                    holder.hostText.text = "Hosted by: Unknown User"
                }
            }
            .addOnFailureListener {
                holder.hostText.text = "Hosted by: Unknown User"
            }
    }

    override fun getItemCount() = meetings.size
}