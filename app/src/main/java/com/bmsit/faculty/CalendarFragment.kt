package com.bmsit.faculty

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.content.Intent
import android.widget.LinearLayout
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

class CalendarFragment : Fragment() {

    private lateinit var monthYearText: TextView
    private lateinit var calendarRecyclerView: RecyclerView
    private lateinit var selectedDate: Calendar
    private val allMeetings = mutableListOf<Meeting>()
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    
    // Use a background thread executor for heavy operations
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        calendarRecyclerView = view.findViewById(R.id.calendarRecyclerView)
        monthYearText = view.findViewById(R.id.textViewMonthYear)
        
        prevButton = view.findViewById(R.id.buttonPrevious)
        nextButton = view.findViewById(R.id.buttonNext)

        selectedDate = Calendar.getInstance()

        setupNavigationButtons()

        // Load meetings with a delay to ensure UI is ready
        mainHandler.postDelayed({
            // Load meetings in background to avoid blocking UI
            backgroundExecutor.execute {
                fetchAllMeetingsForUser()
            }
        }, 500)
    }

    private fun setupNavigationButtons() {
        prevButton.setOnClickListener { 
            navigatePrevious()
            updateCalendarView()
        }
        
        nextButton.setOnClickListener { 
            navigateNext()
            updateCalendarView()
        }
    }
    
    private fun navigatePrevious() {
        selectedDate.add(Calendar.MONTH, -1)
    }
    
    private fun navigateNext() {
        selectedDate.add(Calendar.MONTH, 1)
    }

    private fun fetchAllMeetingsForUser() {
        // Ensure we have a valid context and current user
        val ctx = context ?: return
        val currentUser = auth.currentUser ?: return
        
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                val userDesignation = userDoc.getString("designation")
                db.collection("meetings").get()
                    .addOnSuccessListener { result ->
                        // Clear and populate on background thread to avoid blocking UI
                        backgroundExecutor.execute {
                            try {
                                allMeetings.clear()
                                for (document in result) {
                                    try {
                                        val meeting = document.toObject(Meeting::class.java)
                                        // Unlike DashboardFragment, CalendarFragment shows ALL meetings including cancelled ones
                                        val validDesignationsForFacultyMeeting = listOf(
                                            "Faculty",
                                            "Assistant Professor",
                                            "Associate Professor",
                                            "Lab Assistant",
                                            "HOD",
                                            "ADMIN",
                                            "Unassigned"
                                        )
                                        val canSeeMeeting = when (meeting.attendees) {
                                            "All Associate Prof" -> userDesignation == "Associate Professor"
                                            "All Assistant Prof" -> userDesignation == "Assistant Professor"
                                            "All Faculty" -> userDesignation in listOf("Faculty", "Assistant Professor", "Associate Professor", "Lab Assistant", "HOD", "ADMIN", "Unassigned")
                                            "Custom" -> meeting.customAttendeeUids.contains(currentUser.uid)
                                            else -> false
                                        }
                                        // Ensure scheduler always sees their own meetings
                                        val visibleToUser = canSeeMeeting || meeting.scheduledBy == currentUser.uid
                                        if (visibleToUser) allMeetings.add(meeting)
                                    } catch (e: Exception) {
                                        Log.e("CalendarFragment", "Error processing meeting document", e)
                                    }
                                }
                                // Update UI on main thread
                                activity?.runOnUiThread {
                                    updateCalendarView()
                                }
                            } catch (e: Exception) {
                                Log.e("CalendarFragment", "Error processing meetings", e)
                                activity?.runOnUiThread {
                                    Toast.makeText(ctx, "Error processing meetings: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("CalendarFragment", "Error fetching meetings: ", exception)
                        activity?.runOnUiThread {
                            Toast.makeText(ctx, "Error loading meetings: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("CalendarFragment", "Error fetching user data: ", exception)
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "Error loading user data: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun updateCalendarView() {
        setMonthView()
    }

    private fun setMonthView() {
        // Ensure we have a valid context before proceeding
        val ctx = context ?: return
        
        monthYearText.text = monthYearFromDate(selectedDate)
        val daysInMonth = daysInMonthArray(selectedDate)
        val meetingsForMonth = filterMeetingsForMonth(selectedDate)

        val calendarAdapter = CalendarAdapter(daysInMonth, meetingsForMonth, auth.currentUser?.uid ?: "") { date ->
            onDateClick(date)
        }
        val layoutManager = GridLayoutManager(ctx, 7)
        calendarRecyclerView.layoutManager = layoutManager
        calendarRecyclerView.adapter = calendarAdapter
    }

    private fun filterMeetingsForMonth(date: Calendar): List<Meeting> {
        return allMeetings.filter {
            val meetingCalendar = Calendar.getInstance()
            meetingCalendar.time = it.dateTime.toDate()
            isSameMonth(meetingCalendar, date)
        }
    }

    private fun isSameMonth(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    // This function now tells the MainActivity to switch to the dashboard
    private fun onDateClick(date: Date) {
        try {
            // Ensure we have a valid context before proceeding
            val ctx = context ?: return
            
            val meetingsOnDate = allMeetings.filter {
                val meetingCalendar = Calendar.getInstance()
                meetingCalendar.time = it.dateTime.toDate()
                val dateCalendar = Calendar.getInstance()
                dateCalendar.time = date
                isSameDay(meetingCalendar, dateCalendar)
            }

            if (meetingsOnDate.isNotEmpty()) {
                // Instead of showing options, directly open the new activity
                val intent = Intent(ctx, MeetingsForDateActivity::class.java).apply {
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    // Format date with zero-padding to ensure proper parsing
                    val year = calendar.get(Calendar.YEAR)
                    val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
                    val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
                    putExtra("SELECTED_DATE", "$year-$month-$day")
                }
                ctx.startActivity(intent)
            } else {
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "No meetings on this day.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarFragment", "Error in onDateClick", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Error opening meetings for date: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showHostedMeetingOptions(date: Date, hostedMeetings: List<Meeting>) {
        val meetingTitles = hostedMeetings.map { it.title }.toTypedArray()
        val dateCalendar = Calendar.getInstance()
        dateCalendar.time = date
        
        activity?.runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle("Hosted Meetings on ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)}")
                .setItems(meetingTitles) { _, which ->
                    val selectedMeeting = hostedMeetings[which]
                    showMeetingOptions(selectedMeeting)
                }
                .setNegativeButton("View All Meetings") { _, _ ->
                    (activity as? MainActivity)?.switchToDashboardAndShowDate(dateCalendar)
                }
                .show()
        }
    }

    private fun showMeetingOptions(meeting: Meeting) {
        // Only show "Take Attendance" option now
        val options = arrayOf("Take Attendance")
        
        activity?.runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle(meeting.title)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showAttendanceDialog(meeting)
                        // Removed transcription option
                    }
                }
                .show()
        }
    }

    private fun showAttendanceDialog(meeting: Meeting) {
        // Get the list of attendees for this meeting
        when (meeting.attendees) {
            "Custom" -> {
                if (meeting.customAttendeeUids.isEmpty()) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "No attendees found for this meeting.", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                
                // Show attendance dialog for custom attendees
                val intent = Intent(context, AttendanceActivity::class.java).apply {
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
                activity?.runOnUiThread {
                    Toast.makeText(context, "Attendance can only be taken for custom meetings with specific attendees.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchAndShowAttendance(meeting: Meeting, designations: List<String>) {
        // Show loading message
        activity?.runOnUiThread {
            Toast.makeText(context, "Fetching attendee list...", Toast.LENGTH_SHORT).show()
        }
        
        // Fetch users with specified designations
        db.collection("users")
            .whereIn("designation", designations)
            .get()
            .addOnSuccessListener { result ->
                val attendeeUids = result.map { it.id }
                
                if (attendeeUids.isEmpty()) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "No attendees found for this meeting.", Toast.LENGTH_SHORT).show()
                    }
                    return@addOnSuccessListener
                }

                // Show attendance dialog
                val intent = Intent(context, AttendanceActivity::class.java).apply {
                    putExtra("MEETING_ID", meeting.id)
                    putExtra("MEETING_TITLE", meeting.title)
                    putStringArrayListExtra("ATTENDEE_UIDS", ArrayList(attendeeUids))
                }
                startActivity(intent)
            }
            .addOnFailureListener { exception ->
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error fetching attendees: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun daysInMonthArray(date: Calendar): ArrayList<Date?> {
        val daysInMonthArray = ArrayList<Date?>()
        val daysInMonth = date.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstOfMonth = date.clone() as Calendar
        firstOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        val dayOfWeek = (firstOfMonth.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Sun=0, Mon=1...

        for (i in 1..42) {
            if (i <= dayOfWeek || i > daysInMonth + dayOfWeek) {
                daysInMonthArray.add(null)
            } else {
                val dayCalendar = date.clone() as Calendar
                dayCalendar.set(Calendar.DAY_OF_MONTH, i - dayOfWeek)
                daysInMonthArray.add(dayCalendar.time)
            }
        }
        return daysInMonthArray
    }

    private fun monthYearFromDate(date: Calendar): String {
        val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return formatter.format(date.time)
    }
    
    private fun formatDate(date: Date, pattern: String): String {
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        return formatter.format(date)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Shutdown background executor
        backgroundExecutor.shutdown()
    }
}