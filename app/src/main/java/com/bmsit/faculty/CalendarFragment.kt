package com.bmsit.faculty

import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class CalendarFragment : Fragment() {

    private lateinit var monthYearText: TextView
    private lateinit var calendarRecyclerView: RecyclerView
    private lateinit var selectedDate: LocalDate
    private val allMeetings = mutableListOf<Meeting>()
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        calendarRecyclerView = view.findViewById(R.id.calendarRecyclerView)
        monthYearText = view.findViewById(R.id.textViewMonthYear)
        val prevButton: Button = view.findViewById(R.id.buttonPreviousMonth)
        val nextButton: Button = view.findViewById(R.id.buttonNextMonth)

        selectedDate = LocalDate.now()

        prevButton.setOnClickListener { previousMonthAction() }
        nextButton.setOnClickListener { nextMonthAction() }

        fetchAllMeetingsForUser()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchAllMeetingsForUser() {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid).get().addOnSuccessListener { userDoc ->
            val userDesignation = userDoc.getString("designation")
            db.collection("meetings").get().addOnSuccessListener { result ->
                allMeetings.clear()
                for (document in result) {
                    val meeting = document.toObject(Meeting::class.java)
                    val validDesignationsForFacultyMeeting = listOf("Faculty", "HOD", "Assistant Professor", "Associate Professor", "Professor", "Lab Assistant", "ADMIN")
                    val canSeeMeeting = when (meeting.attendees) {
                        "All Faculty" -> userDesignation in validDesignationsForFacultyMeeting
                        "All HODs" -> userDesignation == "HOD" || userDesignation == "ADMIN"
                        "Custom" -> meeting.customAttendeeUids.contains(currentUser.uid)
                        else -> false
                    }
                    if(canSeeMeeting) allMeetings.add(meeting)
                }
                setMonthView()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun filterMeetingsForMonth(date: LocalDate): List<Meeting> {
        return allMeetings.filter {
            val meetingDate = it.dateTime.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            meetingDate.year == date.year && meetingDate.month == date.month
        }
    }

    // This function now tells the MainActivity to switch to the dashboard
    @RequiresApi(Build.VERSION_CODES.O)
    private fun onDateClick(date: LocalDate) {
        val meetingsOnDate = allMeetings.any {
            val meetingDate = it.dateTime.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            meetingDate.isEqual(date)
        }

        if (meetingsOnDate) {
            // If there's a meeting, call the function in MainActivity to handle the switch
            (activity as? MainActivity)?.switchToDashboardAndShowDate(date)
        } else {
            Toast.makeText(context, "No meetings on this day.", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun daysInMonthArray(date: LocalDate): ArrayList<LocalDate?> {
        val daysInMonthArray = ArrayList<LocalDate?>()
        val yearMonth = YearMonth.from(date)
        val daysInMonth = yearMonth.lengthOfMonth()
        val firstOfMonth = selectedDate.withDayOfMonth(1)
        val dayOfWeek = firstOfMonth.dayOfWeek.value % 7 // Sun=0, Mon=1...

        for (i in 1..42) {
            if (i <= dayOfWeek || i > daysInMonth + dayOfWeek) {
                daysInMonthArray.add(null)
            } else {
                daysInMonthArray.add(LocalDate.of(selectedDate.year, selectedDate.month, i - dayOfWeek))
            }
        }
        return daysInMonthArray
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun monthYearFromDate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
        return date.format(formatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun previousMonthAction() {
        selectedDate = selectedDate.minusMonths(1)
        setMonthView()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun nextMonthAction() {
        selectedDate = selectedDate.plusMonths(1)
        setMonthView()
    }
}

