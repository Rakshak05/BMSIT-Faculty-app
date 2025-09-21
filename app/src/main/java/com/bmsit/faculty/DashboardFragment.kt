package com.bmsit.faculty

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class DashboardFragment : Fragment(), MeetingAdapter.OnMeetingInteractionListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var meetingsRecyclerView: RecyclerView
    private lateinit var meetingAdapter: MeetingAdapter
    private val meetingList = mutableListOf<Meeting>()
    private var currentUserRole: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val scheduleMeetingButton: Button = view.findViewById(R.id.buttonScheduleMeeting)
        scheduleMeetingButton.setOnClickListener {
            startActivity(Intent(activity, ScheduleMeetingActivity::class.java))
        }

        meetingsRecyclerView = view.findViewById(R.id.meetingsRecyclerView)
        meetingsRecyclerView.layoutManager = LinearLayoutManager(context)

        fetchUserRoleAndThenMeetings(scheduleMeetingButton)
    }

    override fun onSetReminderClick(meeting: Meeting) {
        // This functionality remains the same
        Toast.makeText(context, "Set reminder for: ${meeting.title}", Toast.LENGTH_SHORT).show()
    }

    // This is the updated function that makes the "Edit" button work
    override fun onEditClick(meeting: Meeting) {
        // Create an Intent to open the EditMeetingActivity
        val intent = Intent(activity, EditMeetingActivity::class.java)
        // Pass the unique ID of the clicked meeting to the new screen
        // so it knows which meeting to load and edit.
        intent.putExtra("MEETING_ID", meeting.id)
        startActivity(intent)
    }

    override fun onCancelClick(meeting: Meeting) {
        // Show a confirmation dialog before deleting
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Meeting")
            .setMessage("Are you sure you want to cancel the meeting: '${meeting.title}'?")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                db.collection("meetings").document(meeting.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Meeting canceled successfully.", Toast.LENGTH_SHORT).show()
                        fetchMeetings() // Refresh the list
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Error canceling meeting.", Toast.LENGTH_SHORT).show()
                        Log.w("DashboardFragment", "Error deleting document", e)
                    }
            }
            .setNegativeButton("No", null)
            .show()
    }


    private fun fetchUserRoleAndThenMeetings(button: Button) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            meetingAdapter = MeetingAdapter(meetingList, this, currentUser.uid)
            meetingsRecyclerView.adapter = meetingAdapter

            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        currentUserRole = document.getString("role")
                        if (currentUserRole == "ADMIN" || currentUserRole == "HOD") {
                            button.visibility = View.VISIBLE
                        }
                        fetchMeetings()
                    }
                }
        }
    }

    private fun fetchMeetings() {
        if (currentUserRole == null) return

        db.collection("meetings")
            .whereGreaterThan("dateTime", com.google.firebase.Timestamp.now())
            .orderBy("dateTime", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                meetingList.clear()
                for (document in result) {
                    val meeting = document.toObject(Meeting::class.java)
                    val canSeeMeeting = when (meeting.attendees) {
                        "All Faculty" -> currentUserRole == "Faculty" || currentUserRole == "HOD" || currentUserRole == "ADMIN"
                        "All HODs" -> currentUserRole == "HOD" || currentUserRole == "ADMIN"
                        else -> false
                    }

                    if (canSeeMeeting) {
                        meetingList.add(meeting)
                    }
                }
                meetingAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w("DashboardFragment", "Error getting meetings.", exception)
            }
    }
}

