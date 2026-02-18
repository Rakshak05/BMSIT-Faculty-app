package com.bmsit.faculty

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    // BroadcastReceiver to handle refresh notifications
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "REFRESH_DASHBOARD") {
                Log.d("MainActivity", "Received refresh broadcast")
                refreshCurrentFragment()
            }
        }
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var refreshListener: ListenerRegistration? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isReplacingFragment = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d("MainActivity", "onCreate started")
            setContentView(R.layout.activity_main)
            Log.d("MainActivity", "Layout inflated successfully")

            // Initialize Firebase instances early
            try {
                auth = FirebaseAuth.getInstance()
                db = FirebaseFirestore.getInstance()
                // Initialize Firebase Storage with the correct bucket from google-services.json
                storage = FirebaseStorage.getInstance("gs://bmsit-faculty-30834.firebasestorage.app")
                Log.d("MainActivity", "Firebase instances initialized")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing Firebase instances", e)
                Toast.makeText(this, "Error initializing services: ${e.message}", Toast.LENGTH_LONG).show()
            }

            drawerLayout = findViewById(R.id.drawer_layout)
            val toolbar: Toolbar = findViewById(R.id.toolbar)
            setSupportActionBar(toolbar)
            Log.d("MainActivity", "Toolbar and drawer layout initialized")

            navigationView = findViewById(R.id.nav_view)
            navigationView.setNavigationItemSelectedListener(this)
            Log.d("MainActivity", "Navigation view initialized")

            val toggle = ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
            )
            drawerLayout.addDrawerListener(toggle)
            toggle.syncState()
            Log.d("MainActivity", "Drawer toggle initialized")

            // Check if we need to navigate to a specific fragment based on intent
            handleNavigationIntent()

            if (savedInstanceState == null) {
                try {
                    Log.d("MainActivity", "Loading initial fragment")
                    // Use a delayed approach to ensure UI is ready and all views are initialized
                    handler.postDelayed({
                        // Additional safety check to ensure views are initialized
                        if (::drawerLayout.isInitialized && ::navigationView.isInitialized) {
                            replaceFragment(DashboardFragment())
                            navigationView.setCheckedItem(R.id.nav_dashboard)
                            Log.d("MainActivity", "Dashboard fragment loaded")
                        } else {
                            Log.e("MainActivity", "Views not properly initialized for fragment loading")
                            Toast.makeText(this, "Error initializing app UI", Toast.LENGTH_LONG).show()
                        }
                    }, 300)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading dashboard fragment", e)
                    Toast.makeText(this, "Error loading dashboard: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            setupNavigationMenuBasedOnUser()
            // Start listening for refresh notifications
            startListeningForRefreshNotifications()
            
            // Schedule periodic checks with a delay
            handler.postDelayed({
                schedulePeriodicCheck()
            }, 5000)
            
            // Register the refresh receiver (moved to background)
            handler.post {
                try {
                    val filter = IntentFilter("REFRESH_DASHBOARD")
                    registerReceiver(refreshReceiver, filter)
                    Log.d("MainActivity", "Refresh receiver registered successfully")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error registering refresh receiver", e)
                }
            }
            
            // Subscribe to the refresh topic for FCM notifications (moved to background)
            handler.post {
                FirebaseMessaging.getInstance().subscribeToTopic("refresh")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("MainActivity", "Subscribed to refresh topic successfully")
                        } else {
                            Log.e("MainActivity", "Failed to subscribe to refresh topic", task.exception)
                        }
                    }
            }
            
            Log.d("MainActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Critical error in onCreate", e)
            Toast.makeText(this, "Critical error initializing main activity: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Handle navigation intent from ProfileActivity
    private fun handleNavigationIntent() {
        val navigateTo = intent.getStringExtra("NAVIGATE_TO")
        if (navigateTo != null) {
            // Clear the intent data to prevent reprocessing
            intent.removeExtra("NAVIGATE_TO")
            
            when (navigateTo) {
                "DASHBOARD" -> {
                    handler.postDelayed({
                        replaceFragment(DashboardFragment())
                        navigationView.setCheckedItem(R.id.nav_dashboard)
                    }, 300)
                }
                "CALENDAR" -> {
                    handler.postDelayed({
                        replaceFragment(CalendarFragment())
                        navigationView.setCheckedItem(R.id.nav_calendar)
                    }, 300)
                }
                "FACULTY_MEMBERS" -> {
                    handler.postDelayed({
                        replaceFragment(FacultyMembersFragment())
                        navigationView.setCheckedItem(R.id.nav_admin)
                    }, 300)
                }
                "DOWNLOAD_CSV" -> {
                    // For CSV download, we just need to show the dashboard and then trigger the download
                    handler.postDelayed({
                        replaceFragment(DashboardFragment())
                        navigationView.setCheckedItem(R.id.nav_dashboard)
                        // Trigger the CSV download after a short delay
                        handler.postDelayed({
                            // Get the current selection before triggering CSV download
                            val currentSelection = navigationView.checkedItem?.itemId ?: R.id.nav_dashboard
                            onNavigationItemSelected(navigationView.menu.findItem(R.id.nav_download_csv))
                            // Ensure the selection remains unchanged after CSV download
                            handler.postDelayed({
                                navigationView.setCheckedItem(currentSelection)
                            }, 1000)
                        }, 500)
                    }, 300)
                }
            }
        }
    }
    
    // Handle new intents when activity is already running (singleTop mode)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Update the intent to the new one
        this.intent = intent
        // Handle the navigation intent
        handleNavigationIntent()
    }

    private fun schedulePeriodicCheck() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, PeriodicCheckReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Check every 15 minutes
            val interval = 15 * 60 * 1000L
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                interval,
                pendingIntent
            )
            Log.d("MainActivity", "Periodic check scheduled")
            
            // Schedule auto-end meeting check
            scheduleAutoEndMeetingCheck(alarmManager)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error scheduling periodic check", e)
        }
    }
    
    private fun scheduleAutoEndMeetingCheck(alarmManager: AlarmManager) {
        try {
            val intent = Intent(this, AutoEndMeetingReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Check every 15 minutes instead of 30 for more responsive meeting ending
            val interval = 15 * 60 * 1000L
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                interval,
                pendingIntent
            )
            Log.d("MainActivity", "Auto-end meeting check scheduled")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error scheduling auto-end meeting check", e)
        }
    }

    // This is the new function that acts as the messenger from the calendar to the dashboard.
    fun switchToDashboardAndShowDate(date: Calendar) {
        try {
            Log.d("MainActivity", "switchToDashboardAndShowDate called")
            val bundle = Bundle()
            // We pass the date as a string representation of the Calendar
            bundle.putString("SELECTED_DATE", "${date.get(Calendar.YEAR)}-${date.get(Calendar.MONTH) + 1}-${date.get(Calendar.DAY_OF_MONTH)}")

            val dashboardFragment = DashboardFragment()
            dashboardFragment.arguments = bundle

            replaceFragment(dashboardFragment)
            // Visually update the side menu to show "Dashboard" as selected.
            navigationView.setCheckedItem(R.id.nav_dashboard)
            Log.d("MainActivity", "switchToDashboardAndShowDate completed")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in switchToDashboardAndShowDate", e)
            Toast.makeText(this, "Error switching to dashboard: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigationMenuBasedOnUser() {
        try {
            Log.d("MainActivity", "setupNavigationMenuBasedOnUser started")
            val currentUser = auth.currentUser
            val facultyMembersMenuItem = navigationView.menu.findItem(R.id.nav_admin)
            val downloadCSVMenuItem = navigationView.menu.findItem(R.id.nav_download_csv)
            val addFacultyMenuItem = navigationView.menu.findItem(R.id.nav_add_faculty)
            val profileMenuItem = navigationView.menu.findItem(R.id.nav_profile)
            
            val headerView = navigationView.getHeaderView(0)
            val headerGreeting = headerView.findViewById<TextView>(R.id.textViewHeaderGreeting)
            val headerProfileImage = headerView.findViewById<ImageView>(R.id.imageViewHeaderProfile)

            if (currentUser != null) {
                db.collection("users").document(currentUser.uid).get()
                    .addOnSuccessListener { document ->
                        try {
                            if (document != null && document.exists()) {
                                val userDesignation = document.getString("designation")
                                // Show faculty members panel only to HODs and HOD's Assistants
                                facultyMembersMenuItem.isVisible = (userDesignation == "HOD" || userDesignation == "HOD'S ASSISTANT")
                                
                                // Show download CSV option only to HODs and HOD's Assistants
                                downloadCSVMenuItem.isVisible = (userDesignation == "HOD" || userDesignation == "HOD'S ASSISTANT")
                                
                                // Show add faculty option only to ADMIN users
                                addFacultyMenuItem?.isVisible = (userDesignation == "ADMIN")
                                
                                // Profile is always visible
                                profileMenuItem?.isVisible = true
                                
                                // Set header greeting from Firestore name or Google displayName
                                val nameFromDb = document.getString("name")
                                val fallbackName = currentUser.displayName
                                val name = (nameFromDb ?: fallbackName ?: "").trim()
                                if (name.isNotBlank()) {
                                    // Greeting with full name (including surname)
                                    headerGreeting?.text = "Welcome back, $name"
                                } else {
                                    headerGreeting?.text = "Welcome back,"
                                }
                                
                                // Load profile picture
                                loadProfilePicture(currentUser.uid, headerProfileImage, name)
                                
                                Log.d("MainActivity", "User menu setup completed")
                            } else {
                                // Hide faculty members panel for users without proper designation
                                facultyMembersMenuItem.isVisible = false
                                downloadCSVMenuItem.isVisible = false
                                addFacultyMenuItem?.isVisible = false
                                profileMenuItem?.isVisible = true
                                val displayName = currentUser.displayName?.trim()
                                if (!displayName.isNullOrBlank()) {
                                    headerGreeting?.text = "Welcome back, $displayName"
                                } else {
                                    headerGreeting?.text = "Welcome back,"
                                }
                                
                                // Load profile picture
                                loadProfilePicture(currentUser.uid, headerProfileImage, "U")
                                
                                Log.d("MainActivity", "User document not found, using default menu setup")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error processing user document", e)
                            // Check if it's a permission error
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                Log.e("MainActivity", "PERMISSION_DENIED error: Check Firebase Firestore rules")
                                Toast.makeText(this, "PERMISSION_DENIED ERROR: The app needs read access to the 'users' collection. Update Firebase Firestore security rules. See FIREBASE_RULES_FIX.md for instructions.", Toast.LENGTH_LONG).show()
                            }
                            // Hide faculty members panel on error
                            facultyMembersMenuItem.isVisible = false
                            downloadCSVMenuItem.isVisible = false
                            addFacultyMenuItem?.isVisible = false
                            profileMenuItem?.isVisible = true
                        }
                    }
                    .addOnFailureListener { exception ->
                        try {
                            // Check if it's a permission error
                            if (exception.message?.contains("PERMISSION_DENIED") == true) {
                                Log.e("MainActivity", "PERMISSION_DENIED error: Check Firebase Firestore rules")
                                Toast.makeText(this, "PERMISSION_DENIED ERROR: The app needs read access to the 'users' collection. Update Firebase Firestore security rules. See FIREBASE_RULES_FIX.md for instructions.", Toast.LENGTH_LONG).show()
                            }
                            
                            // Hide faculty members panel on failure
                            Log.w("MainActivity", "Failed to fetch user data, hiding faculty members menu")
                            facultyMembersMenuItem.isVisible = false
                            downloadCSVMenuItem.isVisible = false
                            addFacultyMenuItem?.isVisible = false
                            profileMenuItem?.isVisible = true
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error in failure handler", e)
                        }
                    }
            } else {
                // Hide faculty members panel for non-logged in users
                facultyMembersMenuItem.isVisible = false
                downloadCSVMenuItem.isVisible = false
                addFacultyMenuItem?.isVisible = false
                profileMenuItem?.isVisible = true
                headerGreeting?.text = "Welcome back,"
                
                Log.d("MainActivity", "No current user, using default menu setup")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupNavigationMenuBasedOnUser", e)
            Toast.makeText(this, "Error setting up navigation menu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Updates the user name in the navigation header
     */
    fun updateUserNameInNavigation(newName: String) {
        try {
            val headerView = navigationView.getHeaderView(0)
            val headerGreeting = headerView.findViewById<TextView>(R.id.textViewHeaderGreeting)
            if (headerGreeting != null && newName.isNotBlank()) {
                headerGreeting.text = "Welcome back, $newName"
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating user name in navigation", e)
        }
    }

    // Function to export all users data to CSV
    private fun exportAllUsersDataToCSV() {
        try {
            val db = FirebaseFirestore.getInstance()
            
            // Fetch all users
            db.collection("users").get()
                .addOnSuccessListener { usersResult ->
                    val csvData = StringBuilder()
                    
                    // Add CSV header
                    csvData.append("User Name,Email,Department,Designation,Meetings Attended,Meetings Missed,Total Hours (HH:MM)\n")
                    
                    // Process each user
                    var processedUsers = 0
                    val totalUsers = usersResult.size()
                    
                    if (totalUsers == 0) {
                        // Save empty CSV file
                        saveCSVFile(csvData.toString(), "all_users_data.csv")
                        return@addOnSuccessListener
                    }
                    
                    for (userDocument in usersResult) {
                        val userId = userDocument.id
                        val userName = userDocument.getString("name") ?: "Unknown User"
                        val userEmail = userDocument.getString("email") ?: ""
                        val userDepartment = userDocument.getString("department") ?: ""
                        val userDesignation = userDocument.getString("designation") ?: ""
                        
                        // Fetch meetings for this user
                        db.collection("meetings").get()
                            .addOnSuccessListener { meetingsResult ->
                                var meetingsAttended = 0
                                var meetingsMissed = 0
                                var totalMeetingMinutes = 0L
                                
                                for (meetingDocument in meetingsResult) {
                                    val meeting = meetingDocument.toObject(Meeting::class.java)
                                    
                                    // Check if the user is involved in this meeting
                                    val isUserInvolved = isUserInvolvedInMeeting(meeting, userId)
                                    
                                    // If user is involved in this meeting
                                    if (isUserInvolved) {
                                        val meetingDate = meeting.dateTime.toDate()
                                        val currentDate = Date()
                                        
                                        if (meetingDate.before(currentDate)) {
                                            // This is a past meeting (scheduled time has passed)
                                            if (meeting.endTime != null) {
                                                // Meeting was conducted, count as attended
                                                meetingsAttended++
                                                // Calculate meeting duration in minutes
                                                val durationMillis = meeting.endTime!!.toDate().time - meeting.dateTime.toDate().time
                                                val durationMinutes = durationMillis / (1000 * 60)
                                                totalMeetingMinutes += durationMinutes
                                            } else {
                                                // Meeting has passed but no end time is recorded
                                                // Check if the current time is past the expected end time
                                                val calendar = Calendar.getInstance()
                                                calendar.time = meetingDate
                                                // Ensure duration is valid, default to 60 minutes if not set properly
                                                val duration = if (meeting.duration > 0) meeting.duration else 60
                                                calendar.add(Calendar.MINUTE, duration)
                                                val expectedEndTime = calendar.time
                                                
                                                // Only count as missed if we're past the expected end time
                                                if (currentDate.after(expectedEndTime)) {
                                                    meetingsMissed++
                                                }
                                                // If we're still within the expected meeting duration, don't count it yet
                                            }
                                        }
                                        // For future meetings, we don't count them in either category
                                    }
                                }
                                
                                // Add user data to CSV
                                val hours = totalMeetingMinutes / 60
                                val minutes = totalMeetingMinutes % 60
                                val totalHoursFormatted = String.format("%02d:%02d", hours, minutes)
                                csvData.append("${userName.replace(",", ";")},${userEmail.replace(",", ";")},${userDepartment.replace(",", ";")},${userDesignation.replace(",", ";")},$meetingsAttended,$meetingsMissed,$totalHoursFormatted\n")
                                
                                processedUsers++
                                
                                // If we've processed all users, save the CSV file
                                if (processedUsers == totalUsers) {
                                    saveCSVFile(csvData.toString(), "all_users_data.csv")
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("MainActivity", "Error fetching meetings for user $userId: ", exception)
                                // Still add user data without meeting stats
                                csvData.append("${userName.replace(",", ";")},${userEmail.replace(",", ";")},${userDepartment.replace(",", ";")},${userDesignation.replace(",", ";")},0,0,00:00\n")
                                
                                processedUsers++
                                
                                // If we've processed all users, save the CSV file
                                if (processedUsers == totalUsers) {
                                    saveCSVFile(csvData.toString(), "all_users_data.csv")
                                }
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("MainActivity", "Error fetching users for CSV export: ", exception)
                    Toast.makeText(this, "Error exporting all users data.", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error exporting all users data to CSV", e)
            Toast.makeText(this, "Error exporting all users data: ${e.message}", Toast.LENGTH_SHORT).show()
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
    
    // Function to save CSV file
    private fun saveCSVFile(csvContent: String, fileName: String) {
        try {
            val file = java.io.File(cacheDir, fileName)
            file.writeText(csvContent)
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Download All Users Data (CSV)"))
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving CSV file", e)
            Toast.makeText(this, "Error saving CSV file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfilePicture(userId: String, imageView: ImageView, userName: String) {
        try {
            // Show user initials instead of profile picture
            showUserInitials(imageView, userName)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading profile picture", e)
            // Show user initials as fallback
            showUserInitials(imageView, "U")
        }
    }
    
    private fun showUserInitials(imageView: ImageView, userName: String) {
        try {
            // Extract initials from the user's name
            val initials = getUserInitials(userName)
            
            // Create a bitmap with the initials
            val bitmap = createInitialsBitmap(initials)
            
            // Set the bitmap to the ImageView
            imageView.setImageBitmap(bitmap)
            imageView.background = null // Remove any background
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating initials bitmap", e)
            // Fallback to default image if there's an error
            imageView.setImageResource(R.drawable.universalpp)
        }
    }
    
    private fun getUserInitials(name: String): String {
        return try {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) return "U"
            
            val names = trimmedName.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            when (names.size) {
                0 -> "U"
                1 -> names[0].firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                else -> {
                    val firstInitial = names[0].firstOrNull()?.uppercaseChar()?.toString() ?: ""
                    val lastInitial = names[names.size - 1].firstOrNull()?.uppercaseChar()?.toString() ?: ""
                    firstInitial + lastInitial
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error extracting initials from name: $name", e)
            "U"
        }
    }
    
    private fun createInitialsBitmap(initials: String): Bitmap {
        // Define the size of the bitmap (in pixels)
        val size = 200
        
        // Create a bitmap and canvas
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background (blue color) - using a fixed color instead of context
        val backgroundPaint = Paint().apply {
            color = Color.parseColor("#FF6200EE") // Using purple_500 color directly
            isAntiAlias = true
        }
        canvas.drawCircle((size / 2).toFloat(), (size / 2).toFloat(), (size / 2).toFloat(), backgroundPaint)
        
        // Draw text (initials)
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size / 2.toFloat()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        
        // Measure text to center it
        val textBounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, textBounds)
        
        // Draw the text centered
        val x = size / 2.toFloat()
        val y = (size / 2 + (textBounds.bottom - textBounds.top) / 2).toFloat()
        canvas.drawText(initials, x, y, textPaint)
        
        return bitmap
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        try {
            Log.d("MainActivity", "onNavigationItemSelected called for item: ${item.itemId}")

            // 1. HANDLE ACTION ITEMS (Like Export CSV)
            // These should NOT change the selected state in the nav drawer
            if (item.itemId == R.id.nav_download_csv) { 
                // Perform the action
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    db.collection("users").document(currentUser.uid).get()
                        .addOnSuccessListener { document ->
                            if (document != null && document.exists()) {
                                val userDesignation = document.getString("designation")
                                // Check if user is HOD or HOD's Assistant before allowing download
                                if (userDesignation == "HOD" || userDesignation == "HOD'S ASSISTANT") {
                                    exportAllUsersDataToCSV()
                                } else {
                                    Toast.makeText(this, "Only HODs and HOD's Assistants can download faculty data.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(this, "Error: User data not found.", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(this, "Error checking user authorization: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(this, "You must be logged in to download data.", Toast.LENGTH_LONG).show()
                }
                
                // Close drawer
                drawerLayout.closeDrawer(GravityCompat.START)
                
                // RETURN FALSE: This is the fix. It tells the drawer NOT to highlight this item.
                // The previously selected item (Dashboard, Calendar, etc.) remains selected.
                return false
            }

            // 2. HANDLE NAVIGATION DESTINATIONS
            // Prevent multiple rapid transitions
            if (isReplacingFragment) {
                Log.d("MainActivity", "Fragment replacement in progress, ignoring navigation request")
                drawerLayout.closeDrawer(GravityCompat.START)
                return true
            }
            
            isReplacingFragment = true
            
            // Close drawer first to provide better UX
            drawerLayout.closeDrawer(GravityCompat.START)
            
            // Use a slight delay to allow drawer to close before fragment transition
            handler.postDelayed({
                when (item.itemId) {
                    R.id.nav_dashboard -> {
                        replaceFragment(DashboardFragment())
                        navigationView.setCheckedItem(R.id.nav_dashboard)
                    }
                    R.id.nav_calendar -> {
                        replaceFragment(CalendarFragment())
                        navigationView.setCheckedItem(R.id.nav_calendar)
                    }
                    R.id.nav_profile -> {
                        // Launch the new ProfileActivity instead of loading ProfileFragment
                        val intent = Intent(this, ProfileActivity::class.java)
                        startActivity(intent)
                        // Don't reset isReplacingFragment here since we're launching a new activity
                    }
                    R.id.nav_admin -> {
                        // Check if current user is HOD or HOD's Assistant before allowing access to Faculty Members page
                        val currentUser = auth.currentUser
                        if (currentUser != null) {
                            db.collection("users").document(currentUser.uid).get()
                                .addOnSuccessListener { document ->
                                    if (document != null && document.exists()) {
                                        val userDesignation = document.getString("designation")
                                        // Check if user is HOD or HOD's Assistant before allowing access
                                        if (userDesignation == "HOD" || userDesignation == "HOD'S ASSISTANT") {
                                            try {
                                                replaceFragment(FacultyMembersFragment())
                                                navigationView.setCheckedItem(R.id.nav_admin)
                                            } catch (e: Exception) {
                                                Log.e("MainActivity", "Error creating FacultyMembersFragment", e)
                                                Toast.makeText(this, "Error accessing faculty members panel: ${e.message}", Toast.LENGTH_LONG).show()
                                                // Try to load dashboard as fallback
                                                replaceFragment(DashboardFragment())
                                                navigationView.setCheckedItem(R.id.nav_dashboard)
                                            }
                                        } else {
                                            Toast.makeText(this, "Only HODs and HOD's Assistants can access faculty members.", Toast.LENGTH_LONG).show()
                                            // Load dashboard as fallback
                                            replaceFragment(DashboardFragment())
                                            navigationView.setCheckedItem(R.id.nav_dashboard)
                                        }
                                    } else {
                                        Toast.makeText(this, "Error: User data not found.", Toast.LENGTH_LONG).show()
                                        // Load dashboard as fallback
                                        replaceFragment(DashboardFragment())
                                        navigationView.setCheckedItem(R.id.nav_dashboard)
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    Toast.makeText(this, "Error checking user authorization: ${exception.message}", Toast.LENGTH_LONG).show()
                                    // Load dashboard as fallback
                                    replaceFragment(DashboardFragment())
                                    navigationView.setCheckedItem(R.id.nav_dashboard)
                                }
                        } else {
                            Toast.makeText(this, "You must be logged in to access this feature.", Toast.LENGTH_LONG).show()
                            // Load dashboard as fallback
                            replaceFragment(DashboardFragment())
                            navigationView.setCheckedItem(R.id.nav_dashboard)
                        }
                    }

                }
                // Reset the flag after a delay to allow next navigation (except for profile)
                if (item.itemId != R.id.nav_profile) {
                    handler.postDelayed({
                        isReplacingFragment = false
                    }, 500)
                } else {
                    // For profile, reset immediately since we're launching a new activity
                    isReplacingFragment = false
                }
            }, 300)
            
            // Return true for navigation items so the touch feedback occurs
            return true

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onNavigationItemSelected", e)
            isReplacingFragment = false
            Toast.makeText(this, "Error handling navigation: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        try {
            Log.d("MainActivity", "replaceFragment called for ${fragment.javaClass.simpleName}")
            // Safety check to ensure we have a valid fragment manager
            if (supportFragmentManager.isDestroyed) {
                Log.e("MainActivity", "Fragment manager is destroyed, cannot replace fragment")
                isReplacingFragment = false
                return
            }
            
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, fragment)
            transaction.commitAllowingStateLoss() // Use this to prevent crashes during state saving
            Log.d("MainActivity", "Fragment replaced successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in replaceFragment", e)
            isReplacingFragment = false
            // Show error on UI thread
            handler.post {
                Toast.makeText(this, "Error loading fragment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            
            // Try to show a fallback fragment
            try {
                val transaction = supportFragmentManager.beginTransaction()
                transaction.replace(R.id.fragment_container, DashboardFragment())
                transaction.commitAllowingStateLoss()
                handler.post {
                    Toast.makeText(this, "Failed to load requested fragment. Showing dashboard instead.", Toast.LENGTH_LONG).show()
                }
            } catch (fallbackException: Exception) {
                Log.e("MainActivity", "Error loading fallback fragment", fallbackException)
                handler.post {
                    Toast.makeText(this, "Critical error: Unable to load any fragment. Please restart the app.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        try {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFragment is DashboardFragment && currentFragment.arguments != null) {
                    replaceFragment(DashboardFragment())
                    navigationView.setCheckedItem(R.id.nav_dashboard)
                } else {
                    super.onBackPressed()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onBackPressed", e)
            super.onBackPressed()
        }
    }
    
    // Start listening for refresh notifications
    private fun startListeningForRefreshNotifications() {
        try {
            refreshListener?.remove() // Remove any existing listener
            refreshListener = db.collection("notifications")
                .whereEqualTo("type", "refresh_dashboard")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("MainActivity", "Error listening for refresh notifications", error)
                        // Handle network errors gracefully
                        if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                            when (error.code) {
                                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> {
                                    Log.e("MainActivity", "Firestore unavailable, will retry")
                                }
                                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                                    Log.e("MainActivity", "Permission denied for refresh notifications")
                                }
                                else -> {
                                    Log.e("MainActivity", "Other Firestore error: ${error.code}")
                                }
                            }
                        }
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null && !snapshot.isEmpty) {
                        val notification = snapshot.documents[0]
                        val timestamp = notification.getTimestamp("timestamp")
                        
                        // Only refresh if this is a recent notification (within last 5 minutes)
                        if (timestamp != null) {
                            val currentTime = System.currentTimeMillis()
                            val notificationTime = timestamp.toDate().time
                            val timeDiff = currentTime - notificationTime
                            
                            // Refresh if notification is within last 5 minutes
                            if (timeDiff < 5 * 60 * 1000) {
                                Log.d("MainActivity", "Received refresh notification, refreshing dashboard")
                                refreshCurrentFragment()
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up refresh listener", e)
        }
    }
    
    // Refresh the current fragment
    private fun refreshCurrentFragment() {
        try {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is DashboardFragment) {
                Log.d("MainActivity", "Refreshing dashboard fragment")
                // Replace with a new instance of the same fragment
                replaceFragment(DashboardFragment())
                navigationView.setCheckedItem(R.id.nav_dashboard)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error refreshing fragment", e)
        }
    }
    
    

    override fun onDestroy() {
        super.onDestroy()
        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null)
        // Remove the refresh listener
        refreshListener?.remove()
        // Unregister the refresh receiver
        try {
            unregisterReceiver(refreshReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
            Log.d("MainActivity", "Refresh receiver was not registered")
        } catch (e: Exception) {
            // Other exception during unregistration
            Log.e("MainActivity", "Error unregistering refresh receiver", e)
        }
    }
}