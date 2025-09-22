package com.bmsit.faculty

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import java.time.LocalDate
import java.util.Calendar

class DashboardFragment : Fragment(), MeetingAdapter.OnMeetingInteractionListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var meetingsRecyclerView: RecyclerView
    private lateinit var meetingAdapter: MeetingAdapter
    private val meetingList = mutableListOf<Meeting>()
    private var currentUserDesignation: String? = null
    private lateinit var scheduleMeetingButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateTextView: TextView
    private lateinit var dashboardTitle: TextView
    private var selectedDate: LocalDate? = null

    private var pendingMeetingForAlarm: Meeting? = null
    private var pendingReminderMinutes: Int = 0
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                pendingMeetingForAlarm?.let { scheduleAlarm(it, pendingReminderMinutes) }
            } else {
                Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        arguments?.getString("SELECTED_DATE")?.let {
            selectedDate = LocalDate.parse(it)
        }
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        scheduleMeetingButton = view.findViewById(R.id.buttonScheduleMeeting)
        progressBar = view.findViewById(R.id.progressBarDashboard)
        emptyStateTextView = view.findViewById(R.id.textViewEmptyState)
        dashboardTitle = view.findViewById(R.id.textViewTitle)
        scheduleMeetingButton.setOnClickListener {
            startActivity(Intent(activity, ScheduleMeetingActivity::class.java))
        }
        meetingsRecyclerView = view.findViewById(R.id.meetingsRecyclerView)
        meetingsRecyclerView.layoutManager = LinearLayoutManager(context)
    }

    override fun onResume() {
        super.onResume()
        fetchUserDesignationAndThenMeetings()
    }

    override fun onSetReminderClick(meeting: Meeting) {
        // ... (This function remains the same)
    }

    override fun onEditClick(meeting: Meeting) {
        // ... (This function remains the same)
    }

    override fun onCancelClick(meeting: Meeting) {
        // ... (This function remains the same)
    }

    private fun checkPermissionsAndScheduleAlarm(meeting: Meeting, reminderMinutes: Int) {
        // ... (This function remains the same)
    }

    private fun scheduleAlarm(meeting: Meeting, reminderMinutes: Int) {
        // ... (This function remains the same)
    }

    private fun cancelAlarm(meeting: Meeting) {
        // ... (This function remains the same)
    }

    private fun fetchUserDesignationAndThenMeetings() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            meetingAdapter = MeetingAdapter(meetingList, this, currentUser.uid)
            meetingsRecyclerView.adapter = meetingAdapter
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        currentUserDesignation = document.getString("designation")
                        if (currentUserDesignation == "ADMIN" || currentUserDesignation == "HOD") {
                            scheduleMeetingButton.visibility = View.VISIBLE
                        } else {
                            scheduleMeetingButton.visibility = View.GONE
                        }
                        fetchMeetings()
                    }
                }
        }
    }

    // --- THIS FUNCTION IS NOW MORE ROBUST ---
    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchMeetings() {
        if (currentUserDesignation == null) return
        val currentUid = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        meetingsRecyclerView.visibility = View.GONE
        emptyStateTextView.visibility = View.GONE

        var query: Query = db.collection("meetings")

        if (selectedDate != null) {
            dashboardTitle.text = "Meetings on ${selectedDate!!.dayOfMonth}/${selectedDate!!.monthValue}"
            val calendar = Calendar.getInstance()
            calendar.set(selectedDate!!.year, selectedDate!!.monthValue - 1, selectedDate!!.dayOfMonth, 0, 0, 0)
            val startOfDay = Timestamp(calendar.time)
            calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59)
            val endOfDay = Timestamp(calendar.time)
            query = query.whereGreaterThanOrEqualTo("dateTime", startOfDay)
                .whereLessThanOrEqualTo("dateTime", endOfDay)
                .orderBy("dateTime", Query.Direction.ASCENDING)
        } else {
            dashboardTitle.text = "Upcoming Meetings"
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
            val startOfToday = Timestamp(calendar.time)
            query = query.whereGreaterThanOrEqualTo("dateTime", startOfToday)
                .orderBy("dateTime", Query.Direction.ASCENDING)
        }

        query.get()
            .addOnSuccessListener { result ->
                progressBar.visibility = View.GONE
                meetingList.clear()
                for (document in result) {
                    try {
                        // This conversion can fail if data in Firestore is bad
                        val meeting = document.toObject(Meeting::class.java)
                        val validDesignations = listOf("Faculty", "HOD", "Assistant Professor", "Associate Professor", "Professor", "Lab Assistant", "ADMIN")
                        val canSeeMeeting = when (meeting.attendees) {
                            "All Faculty" -> currentUserDesignation in validDesignations
                            "All HODs" -> currentUserDesignation == "HOD" || currentUserDesignation == "ADMIN"
                            "Custom" -> meeting.customAttendeeUids.contains(currentUid)
                            else -> false
                        }
                        if (canSeeMeeting) {
                            meetingList.add(meeting)
                        }
                    } catch (e: Exception) {
                        // This will log an error for the specific bad document and continue
                        Log.e("DashboardFragment", "Error converting meeting document: ${document.id}", e)
                    }
                }
                if (meetingList.isEmpty()) {
                    val emptyMessage = if (selectedDate != null) "No meetings on this day." else "No upcoming meetings."
                    emptyStateTextView.text = emptyMessage
                    emptyStateTextView.visibility = View.VISIBLE
                    meetingsRecyclerView.visibility = View.GONE
                } else {
                    emptyStateTextView.visibility = View.GONE
                    meetingsRecyclerView.visibility = View.VISIBLE
                }
                meetingAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                // This block runs if the whole query fails (e.g., missing index)
                progressBar.visibility = View.GONE
                // Show the actual error message from Firebase
                emptyStateTextView.text = "Error: ${exception.message}"
                emptyStateTextView.visibility = View.VISIBLE
                Log.e("DashboardFragment", "Error getting meetings: ", exception)
            }
    }
}

