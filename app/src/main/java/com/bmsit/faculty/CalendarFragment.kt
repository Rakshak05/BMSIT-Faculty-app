package com.bmsit.faculty

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class CalendarFragment : Fragment() {

    private lateinit var monthYearText: TextView
    private lateinit var calendarRecyclerView: RecyclerView
    private lateinit var selectedDate: Calendar
    private val allMeetings = mutableListOf<Meeting>()
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

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
        val prevButton: Button = view.findViewById(R.id.buttonPreviousMonth)
        val nextButton: Button = view.findViewById(R.id.buttonNextMonth)

        selectedDate = Calendar.getInstance()

        prevButton.setOnClickListener { previousMonthAction() }
        nextButton.setOnClickListener { nextMonthAction() }

        fetchAllMeetingsForUser()
    }

    private fun fetchAllMeetingsForUser() {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid).get().addOnSuccessListener { userDoc ->
            val userDesignation = userDoc.getString("designation")
            db.collection("meetings").get().addOnSuccessListener { result ->
                allMeetings.clear()
                for (document in result) {
                    val meeting = document.toObject(Meeting::class.java)
                    // Unlike DashboardFragment, CalendarFragment shows ALL meetings including cancelled ones
                    val validDesignationsForFacultyMeeting = listOf(
                        "Faculty",
                        "Assistant Professor",
                        "Associate Professor",
                        "Lab Assistant",
                        "HOD",
                        "DEAN",
                        "ADMIN"
                    )
                    val canSeeMeeting = when (meeting.attendees) {
                        "All Faculty" -> userDesignation in validDesignationsForFacultyMeeting
                        "All Deans" -> userDesignation == "DEAN" || userDesignation == "ADMIN"
                        "All HODs" -> userDesignation == "HOD" || userDesignation == "ADMIN"
                        "Custom" -> meeting.customAttendeeUids.contains(currentUser.uid)
                        else -> false
                    }
                    // Ensure scheduler always sees their own meetings
                    val visibleToUser = canSeeMeeting || meeting.scheduledBy == currentUser.uid
                    if(visibleToUser) allMeetings.add(meeting)
                }
                setMonthView()
            }.addOnFailureListener { exception ->
                Log.e("CalendarFragment", "Error fetching meetings: ", exception)
                Toast.makeText(context, "Error loading meetings: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { exception ->
            Log.e("CalendarFragment", "Error fetching user data: ", exception)
            Toast.makeText(context, "Error loading user data: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setMonthView() {
        monthYearText.text = monthYearFromDate(selectedDate)
        val daysInMonth = daysInMonthArray(selectedDate)
        val meetingsForMonth = filterMeetingsForMonth(selectedDate)

        val calendarAdapter = CalendarAdapter(daysInMonth, meetingsForMonth) { date ->
            onDateClick(date)
        }
        val layoutManager = GridLayoutManager(context, 7)
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
        val meetingsOnDate = allMeetings.any {
            val meetingCalendar = Calendar.getInstance()
            meetingCalendar.time = it.dateTime.toDate()
            val dateCalendar = Calendar.getInstance()
            dateCalendar.time = date
            isSameDay(meetingCalendar, dateCalendar)
        }

        if (meetingsOnDate) {
            // If there's a meeting, call the function in MainActivity to handle the switch
            val dateCalendar = Calendar.getInstance()
            dateCalendar.time = date
            (activity as? MainActivity)?.switchToDashboardAndShowDate(dateCalendar)
        } else {
            Toast.makeText(context, "No meetings on this day.", Toast.LENGTH_SHORT).show()
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

    private fun previousMonthAction() {
        selectedDate.add(Calendar.MONTH, -1)
        setMonthView()
    }

    private fun nextMonthAction() {
        selectedDate.add(Calendar.MONTH, 1)
        setMonthView()
    }
}