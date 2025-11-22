package com.bmsit.faculty

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MeetingsForDateActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var meetingsRecyclerView: RecyclerView
    private lateinit var meetingsAdapter: DateMeetingsAdapter
    private val meetingList = mutableListOf<Meeting>()
    private val userNamesMap = mutableMapOf<String, String>()
    private lateinit var dateTitleText: TextView
    private lateinit var emptyStateTextView: TextView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var fabBack: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meetings_for_date)

        // Get the selected date from the intent
        val dateStr = intent.getStringExtra("SELECTED_DATE")
        if (dateStr.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid date", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Parse the date
        val dateParts = dateStr.split("-")
        if (dateParts.size != 3) {
            Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val year = dateParts[0].toInt()
        val month = dateParts[1].toInt() - 1 // Calendar months are 0-based
        val day = dateParts[2].toInt()

        val selectedDate = Calendar.getInstance()
        selectedDate.set(year, month, day)

        // Format and display the date title
        dateTitleText = findViewById(R.id.textViewDateTitle)
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        dateTitleText.text = "Meetings on ${dateFormat.format(selectedDate.time)}"

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        meetingsRecyclerView = findViewById(R.id.meetingsRecyclerView)
        meetingsRecyclerView.layoutManager = LinearLayoutManager(this)
        emptyStateTextView = findViewById(R.id.textViewEmptyState)
        progressBar = findViewById(R.id.progressBar)
        fabBack = findViewById(R.id.fabBack)

        fabBack.setOnClickListener {
            finish()
        }

        fetchMeetingsForDate(selectedDate)
    }

    private fun fetchMeetingsForDate(selectedDate: Calendar) {
        progressBar.visibility = View.VISIBLE
        meetingsRecyclerView.visibility = View.GONE
        emptyStateTextView.visibility = View.GONE

        val currentUser = auth.currentUser ?: return

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                val userDesignation = userDoc.getString("designation")
                db.collection("meetings").get()
                    .addOnSuccessListener { result ->
                        meetingList.clear()

                        for (document in result) {
                            val meeting = document.toObject(Meeting::class.java)
                            // Filter meetings for the selected date
                            val meetingCalendar = Calendar.getInstance()
                            meetingCalendar.time = meeting.dateTime.toDate()

                            if (isSameDay(meetingCalendar, selectedDate)) {
                                // Check if user can see this meeting
                                val canSeeMeeting = when (meeting.attendees) {
                                    "All Associate Prof" -> userDesignation == "Associate Professor"
                                    "All Assistant Prof" -> userDesignation == "Assistant Professor"
                                    "All Faculty" -> userDesignation in listOf("Faculty", "Assistant Professor", "Associate Professor", "Lab Assistant", "HOD", "ADMIN", "Unassigned")
                                    "Custom" -> meeting.customAttendeeUids.contains(currentUser.uid)
                                    else -> false
                                }
                                // Ensure scheduler always sees their own meetings
                                val visibleToUser = canSeeMeeting || meeting.scheduledBy == currentUser.uid
                                if (visibleToUser) meetingList.add(meeting)
                            }
                        }

                        fetchUserNamesForMeetings {
                            updateUI()
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("MeetingsForDateActivity", "Error fetching meetings: ", exception)
                        Toast.makeText(this, "Error loading meetings: ${exception.message}", Toast.LENGTH_LONG).show()
                        progressBar.visibility = View.GONE
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("MeetingsForDateActivity", "Error fetching user data: ", exception)
                Toast.makeText(this, "Error loading user data: ${exception.message}", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun fetchUserNamesForMeetings(callback: () -> Unit) {
        try {
            // Clear the user names map
            userNamesMap.clear()

            // Get unique scheduler IDs from meetings
            val schedulerIds = meetingList.map { it.scheduledBy }.distinct()

            // If no meetings or no scheduler IDs, call callback immediately
            if (schedulerIds.isEmpty()) {
                callback()
                return
            }

            // Counter to track completed requests
            var completedRequests = 0
            val totalRequests = schedulerIds.size

            // Fetch user data for each scheduler ID
            schedulerIds.forEach { userId ->
                db.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        try {
                            if (document != null && document.exists()) {
                                val user = document.toObject(User::class.java)
                                userNamesMap[userId] = user?.name ?: "Unknown User"
                            } else {
                                userNamesMap[userId] = "Unknown User"
                            }

                            completedRequests++
                            // If all requests are completed, call the callback
                            if (completedRequests == totalRequests) {
                                callback()
                            }
                        } catch (e: Exception) {
                            Log.e("MeetingsForDateActivity", "Error processing user data for $userId", e)
                            userNamesMap[userId] = "Unknown User"
                            completedRequests++
                            if (completedRequests == totalRequests) {
                                callback()
                            }
                        }
                    }
                    .addOnFailureListener {
                        try {
                            Log.e("MeetingsForDateActivity", "Failed to fetch user data for $userId", it)
                            userNamesMap[userId] = "Unknown User"
                            completedRequests++
                            if (completedRequests == totalRequests) {
                                callback()
                            }
                        } catch (e: Exception) {
                            Log.e("MeetingsForDateActivity", "Error handling failure for user data fetch for $userId", e)
                            userNamesMap[userId] = "Unknown User"
                            completedRequests++
                            if (completedRequests == totalRequests) {
                                callback()
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("MeetingsForDateActivity", "Error in fetchUserNamesForMeetings", e)
            // Call callback even if there's an error to prevent hanging
            callback()
        }
    }

    private fun updateUI() {
        try {
            progressBar.visibility = View.GONE

            if (meetingList.isEmpty()) {
                meetingsRecyclerView.visibility = View.GONE
                emptyStateTextView.visibility = View.VISIBLE
            } else {
                meetingsRecyclerView.visibility = View.VISIBLE
                emptyStateTextView.visibility = View.GONE

                // Sort meetings by time ascending
                meetingList.sortBy { it.dateTime.toDate().time }

                // Get current user ID
                val currentUid = auth.currentUser?.uid ?: ""

                // Create a custom listener for meeting interactions
                val meetingListener = object : DateMeetingsAdapter.OnMeetingInteractionListener {
                    override fun onMeetingClick(meeting: Meeting) {
                        try {
                            // When a meeting item is clicked, toggle expansion (handled in adapter)
                            // No need to show dialog anymore since we have inline attendance option
                        } catch (e: Exception) {
                            Log.e("MeetingsForDateActivity", "Error handling meeting click", e)
                            Toast.makeText(this@MeetingsForDateActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    override fun onTakeAttendanceClick(meeting: Meeting) {
                        try {
                            showAttendanceDialog(meeting)
                        } catch (e: Exception) {
                            Log.e("MeetingsForDateActivity", "Error handling take attendance click", e)
                            Toast.makeText(this@MeetingsForDateActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Update or create the adapter
                meetingsAdapter = DateMeetingsAdapter(meetingList, meetingListener, currentUid, userNamesMap)
                meetingsRecyclerView.adapter = meetingsAdapter
            }
        } catch (e: Exception) {
            Log.e("MeetingsForDateActivity", "Error in updateUI", e)
            Toast.makeText(this@MeetingsForDateActivity, "Error updating UI: ${e.message}", Toast.LENGTH_LONG).show()
            progressBar.visibility = View.GONE
        }
    }

    private fun fetchAndShowAttendance(meeting: Meeting, designations: List<String>) {
        // Show loading message
        Toast.makeText(this@MeetingsForDateActivity, "Fetching attendee list...", Toast.LENGTH_SHORT).show()
        
        // Fetch users with specified designations
        db.collection("users")
            .whereIn("designation", designations)
            .get()
            .addOnSuccessListener { result ->
                val attendeeUids = result.map { it.id }
                
                if (attendeeUids.isEmpty()) {
                    Toast.makeText(this@MeetingsForDateActivity, "No attendees found for this meeting.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Show attendance dialog
                val intent = android.content.Intent(this, AttendanceActivity::class.java).apply {
                    putExtra("MEETING_ID", meeting.id)
                    putExtra("MEETING_TITLE", meeting.title)
                    putStringArrayListExtra("ATTENDEE_UIDS", ArrayList(attendeeUids))
                }
                startActivity(intent)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this@MeetingsForDateActivity, "Error fetching attendees: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAttendanceDialog(meeting: Meeting) {
        // Get the list of attendees for this meeting
        when (meeting.attendees) {
            "Custom" -> {
                if (meeting.customAttendeeUids.isEmpty()) {
                    Toast.makeText(this@MeetingsForDateActivity, "No attendees found for this meeting.", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Show attendance dialog for custom attendees
                val intent = android.content.Intent(this, AttendanceActivity::class.java).apply {
                    putExtra("MEETING_ID", meeting.id)
                    putExtra("MEETING_TITLE", meeting.title)
                    putStringArrayListExtra("ATTENDEE_UIDS", ArrayList(meeting.customAttendeeUids))
                }
                startActivity(intent)
            }
            "All Associate Prof" -> {
                fetchAndShowAttendance(meeting, listOf("Associate Professor"))
            }
            "All Assistant Prof" -> {
                fetchAndShowAttendance(meeting, listOf("Assistant Professor"))
            }
            "All Faculty" -> {
                fetchAndShowAttendance(meeting, listOf("Faculty", "Assistant Professor", "Associate Professor", "Lab Assistant", "HOD", "ADMIN", "Unassigned"))
            }
            else -> {
                // For unknown meeting types, show a message
                Toast.makeText(this@MeetingsForDateActivity, "Attendance can only be taken for custom meetings with specific attendees.", Toast.LENGTH_LONG).show()
            }
        }
    }

}

