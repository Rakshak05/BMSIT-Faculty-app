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

data class MissedMeeting(
    val id: String,
    val title: String,
    val dateTime: Date,
    val hostId: String,
    val location: String
)

class MissedMeetingsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MissedMeetingsAdapter
    private val missedMeetings = mutableListOf<MissedMeeting>()
    private var targetUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_missed_meetings)

        // Get the target user ID from intent extras
        targetUserId = intent.getStringExtra("TARGET_USER_ID")

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewMissedMeetings)
        val progressBar = findViewById<View>(R.id.progressBar)
        val emptyState = findViewById<TextView>(R.id.textViewEmptyState)

        adapter = MissedMeetingsAdapter(missedMeetings)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fetchMissedMeetings(progressBar, emptyState)
    }

    private fun fetchMissedMeetings(progressBar: View, emptyState: TextView) {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        // Use target user ID if provided, otherwise use current user ID
        val userId = targetUserId ?: auth.currentUser?.uid ?: return

        db.collection("meetings")
            .get()
            .addOnSuccessListener { result ->
                missedMeetings.clear()

                for (document in result) {
                    val meeting = document.toObject(Meeting::class.java)
                    val meetingDate = meeting.dateTime.toDate()
                    val currentDate = Date()

                    // Check if user is involved in this meeting (either as host or attendee)
                    val isUserInvolved = isUserInvolvedInMeeting(meeting, userId)

                    // If user is involved and meeting has passed but no end time is recorded, it's a missed meeting
                    if (isUserInvolved && meetingDate.before(currentDate) && meeting.endTime == null) {
                        missedMeetings.add(
                            MissedMeeting(
                                id = meeting.id,
                                title = meeting.title,
                                dateTime = meetingDate,
                                hostId = meeting.scheduledBy,
                                location = meeting.location
                            )
                        )
                    }
                }

                // Sort meetings by date (most recent first)
                missedMeetings.sortByDescending { it.dateTime }

                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE

                if (missedMeetings.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MissedMeetingsActivity", "Error fetching meetings: ", exception)
                progressBar.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            }
    }
    
    // Function to check if a user is involved in a meeting
    private fun isUserInvolvedInMeeting(meeting: Meeting, userId: String): Boolean {
        // User scheduled the meeting
        if (meeting.scheduledBy == userId) {
            return true
        }
        
        // User is a custom attendee
        if (meeting.attendees == "Custom" && meeting.customAttendeeUids.contains(userId)) {
            return true
        }
        
        // For group meetings (All Associate Prof, All Assistant Prof, etc.), we would need to check if the user 
        // belongs to that group, but for simplicity, we'll skip this for now
        // In a production app, you would implement group membership checking here
        
        return false
    }
}

class MissedMeetingsAdapter(private val meetings: List<MissedMeeting>) :
    RecyclerView.Adapter<MissedMeetingsAdapter.MissedMeetingViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

    class MissedMeetingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.textViewMeetingTitle)
        val dateTimeText: TextView = view.findViewById(R.id.textViewMeetingDateTime)
        val hostText: TextView = view.findViewById(R.id.textViewMeetingHost)
        val locationText: TextView = view.findViewById(R.id.textViewMeetingLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MissedMeetingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_missed_meeting, parent, false)
        return MissedMeetingViewHolder(view)
    }

    override fun onBindViewHolder(holder: MissedMeetingViewHolder, position: Int) {
        val meeting = meetings[position]
        holder.titleText.text = meeting.title
        holder.dateTimeText.text = dateFormat.format(meeting.dateTime)
        holder.locationText.text = "Location: ${meeting.location}"

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