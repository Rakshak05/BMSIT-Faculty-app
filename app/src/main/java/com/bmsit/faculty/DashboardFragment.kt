package com.bmsit.faculty

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.ktx.functions
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.Timestamp
import com.bmsit.faculty.SectionedMeetingAdapter
import com.bmsit.faculty.MeetingListItem
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.regex.Pattern
import java.util.HashSet
import java.util.Date
import java.util.concurrent.Executors

class DashboardFragment : Fragment(), MeetingAdapter.OnMeetingInteractionListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var meetingsRecyclerView: RecyclerView
    private var sectionedAdapter: SectionedMeetingAdapter? = null
    private val meetingList = mutableListOf<Meeting>()
    private val currentMeetingsList = mutableListOf<CurrentMeeting>()
    private val sectionedItems = mutableListOf<MeetingListItem>()
    private var currentUserDesignation: String? = null
    private lateinit var scheduleMeetingButton: View  // Changed from ExtendedFloatingActionButton to View
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateTextView: TextView
    private lateinit var dashboardTitle: TextView
    private var selectedDate: Calendar? = null
    // Map to store user IDs and their display names
    private val userNamesMap = mutableMapOf<String, String>()
    
    private var pendingMeetingForAlarm: Meeting? = null
    private var pendingReminderMinutes: Int = 0
    private var meetingsListener: ListenerRegistration? = null
    private val knownMeetingIds = mutableSetOf<String>()
    private val meetingStateMap = mutableMapOf<String, Pair<Long, String>>() // id -> (dateMillis, status)
    
    // Add a set to track notified meetings to prevent duplicates
    private val notifiedMeetingIds = HashSet<String>()
    
    // SharedPreferences for tracking permission requests
    private lateinit var preferences: SharedPreferences
    
    // Handler for periodic checks - using main thread looper explicitly
    private val handler = Handler(Looper.getMainLooper())
    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            // Just check if we need to re-establish listener, don't call startListeningMeetings()
            // which is an expensive operation
            if (meetingsListener == null) {
                // Only re-establish if we're currently viewing the fragment
                if (isAdded && !isDetached && isVisible) {
                    fetchUserDesignationAndThenMeetings()
                }
            }
            // Schedule next check with longer interval to reduce resource usage
            handler.postDelayed(this, 60000) // Check every 60 seconds instead of 30
        }
    }

    // Use a background thread executor for heavy operations
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                pendingMeetingForAlarm?.let { scheduleAlarm(it, pendingReminderMinutes) }
            } else {
                Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            Log.d("DashboardFragment", "onCreateView started")
            arguments?.getString("SELECTED_DATE")?.let { dateString ->
                // Parse the date string in format "YYYY-MM-DD"
                try {
                    val parts = dateString.split("-")
                    if (parts.size == 3) {
                        val year = parts[0].toInt()
                        val month = parts[1].toInt() - 1 // Calendar months are 0-based
                        val day = parts[2].toInt()
                        
                        selectedDate = Calendar.getInstance().apply {
                            set(year, month, day)
                        }
                        Log.d("DashboardFragment", "Selected date parsed: $dateString")
                    } else {
                        Log.w("DashboardFragment", "Invalid date format: $dateString")
                    }
                } catch (e: Exception) {
                    // If parsing fails, selectedDate remains null
                    Log.e("DashboardFragment", "Error parsing selected date: $dateString", e)
                    selectedDate = null
                }
            }
            
            val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
            Log.d("DashboardFragment", "Layout inflated successfully")
            
            // Initialize SharedPreferences
            preferences = requireContext().getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)
            
            return view
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error in onCreateView", e)
            Toast.makeText(context, "Error creating dashboard view: ${e.message}", Toast.LENGTH_LONG).show()
            
            // Return a simple view as fallback
            val view = View(context)
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            return view
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            Log.d("DashboardFragment", "onViewCreated started")
            super.onViewCreated(view, savedInstanceState)
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()
            Log.d("DashboardFragment", "Firebase instances initialized")
            
            scheduleMeetingButton = view.findViewById(R.id.cardScheduleMeeting)
            progressBar = view.findViewById(R.id.progressBarDashboard)
            emptyStateTextView = view.findViewById(R.id.textViewEmptyState)
            dashboardTitle = view.findViewById(R.id.textViewTitle)
            Log.d("DashboardFragment", "Views found successfully")
            
            scheduleMeetingButton.setOnClickListener {
                try {
                    Log.d("DashboardFragment", "Schedule meeting button clicked")
                    startActivity(Intent(activity, ScheduleMeetingActivity::class.java))
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Error starting ScheduleMeetingActivity", e)
                    Toast.makeText(context, "Error opening schedule meeting screen: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            meetingsRecyclerView = view.findViewById(R.id.meetingsRecyclerView)
            meetingsRecyclerView.layoutManager = LinearLayoutManager(context)
            Log.d("DashboardFragment", "RecyclerView initialized")
            
            Log.d("DashboardFragment", "onViewCreated completed successfully")
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error in onViewCreated", e)
            Toast.makeText(context, "Error initializing dashboard: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        fetchUserDesignationAndThenMeetings()
        // Start periodic checks with a delay to avoid immediate execution
        handler.postDelayed(periodicCheckRunnable, 5000)
    }

    override fun onPause() {
        super.onPause()
        meetingsListener?.remove()
        meetingsListener = null
        // Clear the notified meeting IDs when the fragment is paused
        notifiedMeetingIds.clear()
        // Remove periodic checks
        handler.removeCallbacks(periodicCheckRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shutdown background executor
        backgroundExecutor.shutdown()
    }

    override fun onSetReminderClick(meeting: Meeting) {
        // Let the user pick a reminder time and then schedule the alarm
        val options = arrayOf("5 minutes before", "10 minutes before", "30 minutes before")
        val minutes = intArrayOf(5, 10, 30)
        AlertDialog.Builder(requireContext())
            .setTitle("Set reminder")
            .setItems(options) { _, which ->
                val reminderMinutes = minutes[which]
                checkPermissionsAndScheduleAlarm(meeting, reminderMinutes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onEditClick(meeting: Meeting) {
        // Check if editing is allowed based on attendance timestamp
        if (isEditingAllowed(meeting)) {
            val ctx = requireContext()
            val intent = Intent(ctx, EditMeetingActivity::class.java)
            intent.putExtra("MEETING_ID", meeting.id)
            startActivity(intent)
        } else {
            // Show a message explaining why editing is not allowed
            AlertDialog.Builder(requireContext())
                .setTitle("Editing Not Allowed")
                .setMessage("Attendance for this meeting was taken more than 3 working days ago. Editing is no longer permitted.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onCancelClick(meeting: Meeting) {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel meeting")
            .setMessage("Are you sure you want to cancel this meeting?")
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ ->
                db.collection("meetings").document(meeting.id)
                    .update(mapOf("status" to "Cancelled"))
                    .addOnSuccessListener {
                        cancelAlarm(meeting)
                        Toast.makeText(context, "Meeting cancelled.", Toast.LENGTH_SHORT)
                            .show()
                        // Remove from current list view and rebuild sections
                        meetingList.remove(meeting)
                        buildSectionedItems()
                        // Fixed: Using safe call operator for nullable sectionedAdapter
                        sectionedAdapter?.notifyDataSetChanged()
                        if (meetingList.isEmpty()) {
                            emptyStateTextView.text =
                                if (selectedDate != null) "No meetings on this day." else "No upcoming meetings."
                            emptyStateTextView.visibility = View.VISIBLE
                            meetingsRecyclerView.visibility = View.GONE
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            context,
                            "Failed to cancel meeting.",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("DashboardFragment", "Error updating meeting status", e)
                    }
            }
            .show()
    }

    private fun checkPermissionsAndScheduleAlarm(meeting: Meeting, reminderMinutes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                // Save pending state, request permission
                pendingMeetingForAlarm = meeting
                pendingReminderMinutes = reminderMinutes
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // On Android 12+ (S), apps need explicit user approval to schedule exact alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager =
                requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Allow exact alarms")
                    .setMessage("To set precise meeting reminders, allow Exact alarms for this app in Settings.")
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("Schedule approximate") { _, _ ->
                        scheduleInexactAlarm(meeting, reminderMinutes)
                    }
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent =
                            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data =
                                    android.net.Uri.parse("package:${requireContext().packageName}")
                            }
                        startActivity(intent)
                    }
                    .show()
                return
            }
        }
        scheduleAlarm(meeting, reminderMinutes)
    }

    private fun scheduleAlarm(meeting: Meeting, reminderMinutes: Int) {
        val ctx = requireContext()
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            putExtra("MEETING_TITLE", meeting.title)
            putExtra("MEETING_LOCATION", meeting.location)
        }
        // Derive a stable request code per meeting and reminder offset
        val requestCode = (meeting.id + "_" + reminderMinutes).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val triggerAtMillis = meeting.dateTime.toDate().time - reminderMinutes * 60_000L
        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            Toast.makeText(
                context,
                "Selected reminder time is in the past.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Cancel any existing alarm with the same request code to prevent duplicates
        alarmManager.cancel(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
        Toast.makeText(
            context,
            "Reminder set for ${formatTime(triggerAtMillis)}.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun scheduleInexactAlarm(meeting: Meeting, reminderMinutes: Int) {
        val ctx = requireContext()
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            putExtra("MEETING_TITLE", meeting.title)
            putExtra("MEETING_LOCATION", meeting.location)
        }
        val requestCode = (meeting.id + "_approx_" + reminderMinutes).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val triggerAtMillis = meeting.dateTime.toDate().time - reminderMinutes * 60_000L
        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            Toast.makeText(
                context,
                "Selected reminder time is in the past.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // set() will schedule an inexact alarm (no privileged permission required)
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        Toast.makeText(
            context,
            "Approximate reminder set $reminderMinutes minutes before.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun cancelAlarm(meeting: Meeting) {
        val ctx = requireContext()
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderOptions = listOf(5, 10, 30)
        reminderOptions.forEach { minutes ->
            val intent = Intent(ctx, AlarmReceiver::class.java)
            val requestCode = (meeting.id + "_" + minutes).hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                ctx,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun fetchUserDesignationAndThenMeetings() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            try {
                // Initialize the adapter with an empty user names map initially
                sectionedAdapter =
                    SectionedMeetingAdapter(sectionedItems, this, currentUser.uid, userNamesMap)
                meetingsRecyclerView.adapter = sectionedAdapter
                db.collection("users").document(currentUser.uid).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            currentUserDesignation = document.getString("designation")
                            // Only show schedule meeting button for HODs, Associate Professors, and HOD's Assistants
                            val trimmedDesignation = currentUserDesignation?.trim()?.uppercase()
                            val hodDesignation = "HOD"
                            val associateProfDesignation = "ASSOCIATE PROFESSOR"
                            val hodAssistantDesignation = "HOD'S ASSISTANT"
                            val canScheduleMeeting = trimmedDesignation == hodDesignation || 
                                                   trimmedDesignation == associateProfDesignation || 
                                                   trimmedDesignation == hodAssistantDesignation
                            scheduleMeetingButton.visibility = if (canScheduleMeeting) View.VISIBLE else View.GONE
                            // Request notification permissions to ensure instant notifications
                            requestNotificationPermissionIfNeeded()
                            startListeningMeetings()
                            
                            // Check for conflict notifications when user opens the app
                            checkForConflictNotifications()
                        } else {
                            // Handle case where user document doesn't exist
                            Log.e("DashboardFragment", "User document does not exist for UID: ${currentUser.uid}")
                            scheduleMeetingButton.visibility = View.GONE
                            requestNotificationPermissionIfNeeded()
                            startListeningMeetings()
                            
                            // Check for conflict notifications when user opens the app
                            checkForConflictNotifications()
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("DashboardFragment", "Error fetching user document", exception)
                        // Check if it's a permission error
                        if (exception.message?.contains("PERMISSION_DENIED") == true) {
                            Log.e("DashboardFragment", "PERMISSION_DENIED error: Check Firebase Firestore rules")
                            emptyStateTextView.text = "Permission Error: Check Firebase Firestore rules. The app needs read access to the 'users' collection."
                            emptyStateTextView.visibility = View.VISIBLE
                        }
                        // Hide schedule button on error
                        scheduleMeetingButton.visibility = View.GONE
                        requestNotificationPermissionIfNeeded()
                        startListeningMeetings()
                        
                        // Check for conflict notifications when user opens the app
                        checkForConflictNotifications()
                    }
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error initializing adapter or fetching user data", e)
                // Hide schedule button on error
                scheduleMeetingButton.visibility = View.GONE               
                // Check for conflict notifications when user opens the app
                checkForConflictNotifications()
            }
        } else {
            Log.e("DashboardFragment", "Current user is null")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        // Only request permission on Android 13+ (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                // Check if we've already requested permission recently to avoid spam
                val lastRequestTime = preferences.getLong("last_notification_permission_request", 0)
                val currentTime = System.currentTimeMillis()
                val timeSinceLastRequest = currentTime - lastRequestTime
                
                // Only request permission if it's been more than 5 minutes since the last request
                if (timeSinceLastRequest > 5 * 60 * 1000) { // 5 minutes
                    // Update the last request time
                    preferences.edit().putLong("last_notification_permission_request", currentTime).apply()
                    // Request the permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun startListeningMeetings() {
        try {
            if (currentUserDesignation == null) {
                Log.w("DashboardFragment", "User designation is null, but continuing to load meetings")
                // We'll still try to load meetings, but with limited visibility
            }
            val currentUid = auth.currentUser?.uid ?: run {
                Log.e("DashboardFragment", "Current user UID is null")
                return
            }

            // Reset known IDs so we only notify for truly new docs after listener starts
            knownMeetingIds.clear()

            progressBar.visibility = View.VISIBLE
            meetingsRecyclerView.visibility = View.GONE
            emptyStateTextView.visibility = View.GONE

            var query: Query = db.collection("meetings")

            if (selectedDate != null) {
                dashboardTitle.text =
                    "Meetings on ${selectedDate!!.get(Calendar.DAY_OF_YEAR)}/${selectedDate!!.get(Calendar.MONTH) + 1}"
                val calendar = selectedDate!!.clone() as Calendar
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                
                val nowCal = Calendar.getInstance()
                val isToday =
                    selectedDate!!.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                    selectedDate!!.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH) &&
                    selectedDate!!.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
                    
                val startTs = if (isToday) Timestamp(nowCal.time) else Timestamp(calendar.time)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfDay = Timestamp(calendar.time)
                query = query.whereGreaterThanOrEqualTo("dateTime", startTs)
                    .whereLessThanOrEqualTo("dateTime", endOfDay)
                    .orderBy("dateTime", Query.Direction.ASCENDING)
            } else {
                dashboardTitle.text = "Meetings"
                val calendar = Calendar.getInstance()
                // Subtract 1 hour to account for recently finished meetings that users might still want to see
                calendar.add(Calendar.HOUR_OF_DAY, -1)
                val adjustedTs = Timestamp(calendar.time)
                query = query.whereGreaterThanOrEqualTo("dateTime", adjustedTs)
                    .orderBy("dateTime", Query.Direction.ASCENDING)
            }

            meetingsListener?.remove()
            meetingsListener = query.addSnapshotListener { snapshots, exception ->
                if (exception != null) {
                    progressBar.visibility = View.GONE
                    emptyStateTextView.text = "Error: ${exception.message}"
                    emptyStateTextView.visibility = View.VISIBLE
                    Log.e("DashboardFragment", "Error listening meetings: ", exception)
                    
                    // Check if it's a permission error
                    if (exception.message?.contains("PERMISSION_DENIED") == true) {
                        Log.e("DashboardFragment", "PERMISSION_DENIED error: Check Firebase Firestore rules")
                        emptyStateTextView.text = "🔴 PERMISSION_DENIED ERROR\n\nThe app needs read access to the 'meetings' collection.\n\n🔧 FIX: Update Firebase Firestore security rules.\n📄 See FIREBASE_RULES_FIX.md for detailed instructions."
                    }
                    
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    Log.w("DashboardFragment", "Snapshots is null")
                    return@addSnapshotListener
                }

                // Handle changes for cancel/reschedule notifications before we mutate state
                snapshots.documentChanges.forEach { dc ->
                    try {
                        val meeting = dc.document.toObject(Meeting::class.java)
                        val id = meeting.id
                        val newMillis = meeting.dateTime.toDate().time
                        val newStatus = meeting.status
                        val prev = meetingStateMap[id]

                        when (dc.type) {
                            DocumentChange.Type.MODIFIED -> {
                                if (prev != null) {
                                    val (oldMillis, oldStatus) = prev
                                    // Cancelled
                                    if (oldStatus == "Active" && newStatus == "Cancelled") {
                                        notifyMeetingCancelled(meeting, oldMillis)
                                    }
                                    // Rescheduled (date changed while active)
                                    if (newStatus == "Active" && oldMillis != newMillis) {
                                        notifyMeetingRescheduled(meeting, oldMillis, newMillis)
                                    }
                                }
                            }
                            else -> { /* ignore here; additions handled elsewhere */ }
                        }
                    } catch (e: Exception) { 
                        Log.e("DashboardFragment", "Error processing document change", e)
                    }
                }

                meetingList.clear()
                currentMeetingsList.clear()
                val currentKnown = mutableSetOf<String>()

                for (document in snapshots.documents) {
                    try {
                        val meeting = document.toObject(Meeting::class.java) ?: continue
                        // When a specific date is selected (from calendar), show all meetings including cancelled ones
                        // When showing upcoming meetings (no specific date), only show active meetings
                        if (selectedDate == null && meeting.status != "Active") continue
                        
                        // Exclude meetings that have already ended (have an endTime)
                        // This prevents ended meetings from showing up in the "Upcoming Meetings" section
                        if (selectedDate == null && meeting.endTime != null) continue
                        
                        // Debug logging
                        Log.d("DashboardFragment", "Found meeting: ${meeting.title}, DateTime: ${meeting.dateTime.toDate()}, Status: ${meeting.status}")
                        
                        val validDesignations = listOf(
                            "Faculty",
                            "Assistant Professor",
                            "Associate Professor",
                            "Lab Assistant",
                            "HOD",
                            "ADMIN",
                            "Unassigned"
                        )
                        val canSeeMeeting = when (meeting.attendees) {
                            "All Associate Prof" -> currentUserDesignation == "Associate Professor"
                            "All Assistant Prof" -> currentUserDesignation == "Assistant Professor"
                            "All Faculty" -> currentUserDesignation in listOf("Faculty", "Assistant Professor", "Associate Professor", "Lab Assistant", "HOD", "ADMIN", "Unassigned")
                            "Custom" -> meeting.customAttendeeUids.contains(currentUid)
                            else -> false
                        }
                        // Ensure scheduler always sees their own meetings
                        val visibleToUser = canSeeMeeting || meeting.scheduledBy == currentUid
                        
                        Log.d("DashboardFragment", "User can see meeting: $visibleToUser, User designation: $currentUserDesignation, Scheduled by: ${meeting.scheduledBy}, Current UID: $currentUid")
                        
                        if (visibleToUser) {
                            meetingList.add(meeting)
                            currentKnown.add(meeting.id)
                            Log.d("DashboardFragment", "Added meeting to list: ${meeting.title}")
                            
                            // Check if this meeting is currently ongoing and add it to current meetings list
                            checkAndAddCurrentMeeting(meeting, currentUid)
                        } else {
                            Log.d("DashboardFragment", "Meeting not added to list: ${meeting.title}")
                        }
                    } catch (e: Exception) {
                        Log.e("DashboardFragment", "Error processing meeting document", e)
                    }
                }
                // Fire local notification for newly visible meetings while app is in foreground
                val newlyAdded = currentKnown.minus(knownMeetingIds)
                if (newlyAdded.isNotEmpty()) {
                    snapshots.documentChanges.filter { it.type == DocumentChange.Type.ADDED }
                        .mapNotNull { dc ->
                            try {
                                dc.document.toObject(Meeting::class.java)
                            } catch (e: Exception) { 
                                Log.e("DashboardFragment", "Error converting document to Meeting", e)
                                null 
                            }
                        }
                        .forEach { mtg ->
                            // When a specific date is selected (from calendar), show all meetings including cancelled ones
                            // When showing upcoming meetings (no specific date), only show active meetings
                            if (selectedDate == null && mtg.status != "Active") return@forEach
                            
                            // Exclude meetings that have already ended (have an endTime)
                            // This prevents ended meetings from showing up in the "Upcoming Meetings" section
                            if (selectedDate == null && mtg.endTime != null) return@forEach
                            
                            // Only notify if user should see this meeting
                            val validDesignations = listOf(
                                "Faculty",
                                "Assistant Professor",
                                "Associate Professor",
                                "Lab Assistant",
                                "HOD",
                                "ADMIN",
                                "Unassigned"
                            )
                            val canSee = when (mtg.attendees) {
                                "All Associate Prof" -> currentUserDesignation == "Associate Professor"
                                "All Assistant Prof" -> currentUserDesignation == "Assistant Professor"
                                "All Faculty" -> currentUserDesignation in listOf("Faculty", "Assistant Professor", "Associate Professor", "Lab Assistant", "HOD", "ADMIN", "Unassigned")
                                "Custom" -> mtg.customAttendeeUids.contains(currentUid)
                                else -> false
                            }
                            val visibleToUserNow = canSee || mtg.scheduledBy == currentUid
                            if (visibleToUserNow) showImmediateMeetingNotification(mtg)
                        }
                }
                knownMeetingIds.clear()
                knownMeetingIds.addAll(currentKnown)
                // Update meetingState snapshot for next diff
                meetingStateMap.clear()
                meetingList.forEach { m ->
                    meetingStateMap[m.id] = Pair(m.dateTime.toDate().time, m.status)
                }

                progressBar.visibility = View.GONE
                Log.d("DashboardFragment", "Meeting list size: ${meetingList.size}, Selected date: $selectedDate")
                
                if (meetingList.isEmpty()) {
                    // Improved empty state message to be more helpful
                    val emptyMessage = if (selectedDate != null) {
                        "No meetings scheduled for this date."
                    } else {
                        "No meetings scheduled."
                    }
                    emptyStateTextView.text = emptyMessage
                    emptyStateTextView.visibility = View.VISIBLE
                    meetingsRecyclerView.visibility = View.GONE
                    Log.d("DashboardFragment", "Showing empty state: $emptyMessage")
                } else {
                    emptyStateTextView.visibility = View.GONE
                    emptyStateTextView.text = ""
                    meetingsRecyclerView.visibility = View.VISIBLE
                    Log.d("DashboardFragment", "Showing meetings list with ${meetingList.size} items")
                }
                
                // Fetch user names for all meetings before building sectioned items
                fetchUserNamesForMeetings { 
                    buildSectionedItems()
                    // Update the adapter with the user names map
                    sectionedAdapter = SectionedMeetingAdapter(sectionedItems, this, currentUid, userNamesMap)
                    meetingsRecyclerView.adapter = sectionedAdapter
                    // Fixed: Using safe call operator for nullable sectionedAdapter
                    sectionedAdapter?.notifyDataSetChanged()
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error in startListeningMeetings", e)
            progressBar.visibility = View.GONE
            emptyStateTextView.text = "Error loading meetings: ${e.message}"
            emptyStateTextView.visibility = View.VISIBLE
            
            // Check if it's a permission error
            if (e.message?.contains("PERMISSION_DENIED") == true) {
                Log.e("DashboardFragment", "PERMISSION_DENIED error: Check Firebase Firestore rules")
                emptyStateTextView.text = "Permission Error: Check Firebase Firestore rules. The app needs read access to the 'meetings' collection."
            }
        }
    }

    /**
     * Check if a meeting is currently ongoing and add it to the current meetings list
     */
    private fun checkAndAddCurrentMeeting(meeting: Meeting, currentUid: String) {
        val meetingStartTime = meeting.dateTime.toDate()
        val currentTime = Date()
        
        // Check if user is involved in this meeting (host or attendee)
        val isUserInvolved = meeting.scheduledBy == currentUid || 
            (meeting.attendees == "Custom" && meeting.customAttendeeUids.contains(currentUid))
        
        // Check if meeting is ongoing (started but no end time yet)
        // Only add meetings that have started (meetingStartTime is before or equal to currentTime)
        if (isUserInvolved && 
            (meetingStartTime.before(currentTime) || meetingStartTime.equals(currentTime)) &&
            meeting.endTime == null) {
            
            currentMeetingsList.add(
                CurrentMeeting(
                    id = meeting.id,
                    title = meeting.title,
                    dateTime = meetingStartTime,
                    location = meeting.location,
                    attendees = meeting.attendees,
                    scheduledBy = meeting.scheduledBy,
                    startTime = meetingStartTime,
                    endTime = meeting.endTime?.toDate() // Add endTime if available
                )
            )
        }
    }

    private fun buildSectionedItems() {
        sectionedItems.clear()
        
        // Add current meetings section at the top if there are any
        if (currentMeetingsList.isNotEmpty()) {
            sectionedItems.add(MeetingListItem.Header("Current Meetings"))
            currentMeetingsList.forEach { currentMeeting ->
                // Convert CurrentMeeting to a special type of MeetingListItem
                sectionedItems.add(MeetingListItem.CurrentMeetingItem(currentMeeting))
            }
        }
        
        // Sort regular meetings by time ascending
        meetingList.sortBy { it.dateTime.toDate().time }

        val sdfHeader =
            java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault())

        fun sameDay(c1: Calendar, c2: Calendar): Boolean {
            return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                    c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
        }

        val todayCal = Calendar.getInstance()
        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

        val todayItems = meetingList.filter { m ->
            val mc = Calendar.getInstance().apply { time = m.dateTime.toDate() }
            sameDay(mc, todayCal)
        }
        val tomorrowItems = meetingList.filter { m ->
            val mc = Calendar.getInstance().apply { time = m.dateTime.toDate() }
            sameDay(mc, tomorrowCal)
        }
        val laterItems = meetingList.filter { m ->
            val mc = Calendar.getInstance().apply { time = m.dateTime.toDate() }
            mc.after(tomorrowCal) && !sameDay(mc, todayCal) && !sameDay(mc, tomorrowCal)
        }

        // Today section (only if there are items that aren't current meetings)
        if (todayItems.isNotEmpty()) {
            // Filter out meetings that are already shown as current meetings
            val nonCurrentTodayItems = todayItems.filter { meeting ->
                !currentMeetingsList.any { currentMeeting -> currentMeeting.id == meeting.id }
            }
            
            if (nonCurrentTodayItems.isNotEmpty()) {
                sectionedItems.add(MeetingListItem.Header("Today"))
                if (nonCurrentTodayItems.isEmpty()) {
                    sectionedItems.add(MeetingListItem.Info("No meetings scheduled"))
                } else {
                    nonCurrentTodayItems.forEach { sectionedItems.add(MeetingListItem.Item(it)) }
                }
            }
        }

        // Tomorrow section
        if (tomorrowItems.isNotEmpty()) {
            sectionedItems.add(MeetingListItem.Header("Tomorrow"))
            if (tomorrowItems.isEmpty()) {
                sectionedItems.add(MeetingListItem.Info("No meetings scheduled"))
            } else {
                tomorrowItems.forEach { sectionedItems.add(MeetingListItem.Item(it)) }
            }
        }

        // Later dates, group by date
        val groupedLater: Map<String, List<Meeting>> = laterItems.groupBy { m ->
            val c = Calendar.getInstance().apply { time = m.dateTime.toDate() }
            sdfHeader.format(c.time)
        }
        // Keep chronological order by sorted keys according to first meeting time
        val orderedKeys = groupedLater.keys.sortedBy { key ->
            // derive ordering from the first matching item in that date group
            laterItems.firstOrNull { sdfHeader.format(it.dateTime.toDate()) == key }?.dateTime?.toDate()?.time
                ?: Long.MAX_VALUE
        }
        orderedKeys.forEach { key ->
            sectionedItems.add(MeetingListItem.Header(key))
            groupedLater[key]?.forEach { sectionedItems.add(MeetingListItem.Item(it)) }
        }
    }

    /**
     * Fetches user names for all meetings in the list and calls the callback when done
     * Uses background thread to avoid blocking UI
     */
    private fun fetchUserNamesForMeetings(callback: () -> Unit) {
        // Clear the user names map
        userNamesMap.clear()
        
        // Get unique scheduler IDs from meetings
        val schedulerIds = meetingList.map { it.scheduledBy }.distinct()
        
        // If no meetings or no scheduler IDs, call callback immediately
        if (schedulerIds.isEmpty()) {
            callback()
            return
        }
        
        // Use background thread for Firestore operations
        backgroundExecutor.execute {
            try {
                // Counter to track completed requests using atomic integer for thread safety
                val completedRequests = java.util.concurrent.atomic.AtomicInteger(0)
                val totalRequests = schedulerIds.size
                
                // Fetch user data for each scheduler ID
                schedulerIds.forEach { userId ->
                    try {
                        db.collection("users").document(userId).get()
                            .addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    val user = document.toObject(User::class.java)
                                    userNamesMap[userId] = user?.name ?: "Unknown User"
                                } else {
                                    userNamesMap[userId] = "Unknown User"
                                }
                                
                                // Check if all requests are completed
                                if (completedRequests.incrementAndGet() == totalRequests) {
                                    requireActivity().runOnUiThread {
                                        callback()
                                    }
                                }
                            }
                            .addOnFailureListener {
                                userNamesMap[userId] = "Unknown User"
                                
                                // Check if all requests are completed
                                if (completedRequests.incrementAndGet() == totalRequests) {
                                    requireActivity().runOnUiThread {
                                        callback()
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        Log.e("DashboardFragment", "Error fetching user data for $userId", e)
                        userNamesMap[userId] = "Unknown User"
                        
                        // Check if all requests are completed
                        if (completedRequests.incrementAndGet() == totalRequests) {
                            requireActivity().runOnUiThread {
                                callback()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error in fetchUserNamesForMeetings background task", e)
                // Call callback on main thread in case of error
                requireActivity().runOnUiThread {
                    callback()
                }
            }
        }
    }
    
    // Add the missing methods that were referenced but not included
    private fun normalizeTitleFromAttendees(title: String, attendees: String): String {
        return title
    }
    
    private fun normalizeAttendees(attendees: String): String {
        return attendees
    }
    
    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }
    
    private fun notifyMeetingCancelled(meeting: Meeting, oldMillis: Long) {
        // Empty implementation for now
    }
    
    private fun notifyMeetingRescheduled(meeting: Meeting, oldMillis: Long, newMillis: Long) {
        // Empty implementation for now
    }
    
    private fun showImmediateMeetingNotification(meeting: Meeting) {
        // Empty implementation for now
    }
    
    private fun checkForConflictNotifications() {
        // Empty implementation for now
    }
    
    /**
     * Check if editing a meeting is allowed based on the 3 working days rule
     */
    private fun isEditingAllowed(meeting: Meeting): Boolean {
        // If attendance has been taken, check if editing is still allowed
        meeting.attendanceTakenAt?.let { attendanceTimestamp ->
            val attendanceTime = attendanceTimestamp.toDate()
            return WorkingDaysUtils.canEditMeeting(attendanceTime)
        }
        // If no attendance has been taken yet, editing is always allowed
        return true
    }



}