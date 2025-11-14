package com.bmsit.faculty

import androidx.appcompat.app.AppCompatActivity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import android.widget.TextView
import android.util.Log
import com.bmsit.faculty.FacultyMembersFragment
import android.widget.ImageView
import com.google.firebase.storage.FirebaseStorage
import android.graphics.BitmapFactory
import android.view.View
import android.widget.Button
import com.bmsit.faculty.Meeting
import android.widget.LinearLayout

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private val handler = Handler(Looper.getMainLooper())
    private var isReplacingFragment = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d("MainActivity", "onCreate started")
            setContentView(R.layout.activity_main)
            Log.d("MainActivity", "Layout inflated successfully")

            // Schedule periodic checks with a delay
            handler.postDelayed({
                schedulePeriodicCheck()
            }, 5000)

            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()
            // Initialize Firebase Storage with the correct bucket from google-services.json
            storage = FirebaseStorage.getInstance("gs://bmsit-faculty-30834.firebasestorage.app")
            Log.d("MainActivity", "Firebase instances initialized")

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
            Log.d("MainActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Critical error in onCreate", e)
            Toast.makeText(this, "Critical error initializing main activity: ${e.message}", Toast.LENGTH_LONG).show()
        }
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

            // Check every 30 minutes
            val interval = 30 * 60 * 1000L
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
            val diagnosticMenuItem = navigationView.menu.findItem(R.id.nav_diagnostic)
            val downloadCSVMenuItem = navigationView.menu.findItem(R.id.nav_download_csv)
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
                                // Make faculty members panel accessible to ALL users
                                facultyMembersMenuItem.isVisible = true
                                
                                // Show diagnostic tools only to Developers
                                diagnosticMenuItem.isVisible = (userDesignation == "Developer")
                                
                                // Show download CSV option only to administrators
                                downloadCSVMenuItem.isVisible = (userDesignation == "ADMIN" || userDesignation == "DEAN" || userDesignation == "HOD" || userDesignation == "Developer")
                                
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
                                loadProfilePicture(currentUser.uid, headerProfileImage)
                                
                                Log.d("MainActivity", "User menu setup completed")
                            } else {
                                // Make faculty members panel accessible to ALL users
                                facultyMembersMenuItem.isVisible = true
                                diagnosticMenuItem.isVisible = false
                                downloadCSVMenuItem.isVisible = false
                                profileMenuItem?.isVisible = true
                                val displayName = currentUser.displayName?.trim()
                                if (!displayName.isNullOrBlank()) {
                                    headerGreeting?.text = "Welcome back, $displayName"
                                } else {
                                    headerGreeting?.text = "Welcome back,"
                                }
                                
                                // Load profile picture
                                loadProfilePicture(currentUser.uid, headerProfileImage)
                                
                                Log.d("MainActivity", "User document not found, using default menu setup")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error processing user document", e)
                            // Check if it's a permission error
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                Log.e("MainActivity", "PERMISSION_DENIED error: Check Firebase Firestore rules")
                                Toast.makeText(this, "PERMISSION_DENIED ERROR: The app needs read access to the 'users' collection. Update Firebase Firestore security rules. See FIREBASE_RULES_FIX.md for instructions.", Toast.LENGTH_LONG).show()
                            }
                            // Make faculty members panel accessible to ALL users
                            facultyMembersMenuItem.isVisible = true
                            diagnosticMenuItem.isVisible = false // Keep diagnostic hidden by default on error
                            downloadCSVMenuItem.isVisible = false
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
                            
                            // Make faculty members panel accessible to ALL users
                            // This prevents the menu from disappearing due to network issues
                            Log.w("MainActivity", "Failed to fetch user data, keeping faculty members menu visible")
                            facultyMembersMenuItem.isVisible = true
                            diagnosticMenuItem.isVisible = false // Keep diagnostic hidden by default on error
                            downloadCSVMenuItem.isVisible = false
                            profileMenuItem?.isVisible = true
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error in failure handler", e)
                        }
                    }
            } else {
                // Make faculty members panel accessible to ALL users
                facultyMembersMenuItem.isVisible = true
                diagnosticMenuItem.isVisible = false
                downloadCSVMenuItem.isVisible = false
                profileMenuItem?.isVisible = true
                headerGreeting?.text = "Welcome back,"
                
                Log.d("MainActivity", "No current user, using default menu setup")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupNavigationMenuBasedOnUser", e)
            Toast.makeText(this, "Error setting up navigation menu: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                            // This is a past meeting
                                            if (meeting.endTime != null) {
                                                // Meeting was conducted, count as attended
                                                meetingsAttended++
                                                // Calculate meeting duration in minutes
                                                val durationMillis = meeting.endTime!!.toDate().time - meeting.dateTime.toDate().time
                                                val durationMinutes = durationMillis / (1000 * 60)
                                                totalMeetingMinutes += durationMinutes
                                            } else {
                                                // Meeting has passed but no end time is recorded, count as missed
                                                meetingsMissed++
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
        
        // For group meetings (All Faculty, All HODs, etc.), we would need to check if the user 
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

    private fun loadProfilePicture(userId: String, imageView: ImageView) {
        try {
            // Reference to the profile picture in Firebase Storage with explicit bucket
            val storageRef = storage.reference.child("profile_pictures/$userId.jpg")
            
            val ONE_MEGABYTE: Long = 1024 * 1024
            storageRef.getBytes(ONE_MEGABYTE).addOnSuccessListener { bytes ->
                // Successfully downloaded data, convert to bitmap and display
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imageView.setImageBitmap(bitmap)
                imageView.background = null // Remove the default background
            }.addOnFailureListener {
                // Handle any errors - use default image
                Log.d("MainActivity", "No profile picture found for user: $userId, using default")
                // Set default image resource to ensure it's visible
                imageView.setImageResource(R.drawable.universalpp)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading profile picture", e)
            // Set default image resource to ensure it's visible
            imageView.setImageResource(R.drawable.universalpp)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        try {
            Log.d("MainActivity", "onNavigationItemSelected called for item: ${item.itemId}")
            
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
                    R.id.nav_dashboard -> replaceFragment(DashboardFragment())
                    R.id.nav_calendar -> replaceFragment(CalendarFragment())
                    R.id.nav_profile -> replaceFragment(ProfileFragment())
                    R.id.nav_admin -> {
                        try {
                            replaceFragment(FacultyMembersFragment())
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error creating FacultyMembersFragment", e)
                            Toast.makeText(this, "Error accessing faculty members panel: ${e.message}", Toast.LENGTH_LONG).show()
                            // Try to load dashboard as fallback
                            replaceFragment(DashboardFragment())
                        }
                    }
                    R.id.nav_diagnostic -> replaceFragment(DiagnosticFragment())
                    R.id.nav_download_csv -> {
                        // Handle CSV download
                        val currentUser = auth.currentUser
                        if (currentUser != null) {
                            db.collection("users").document(currentUser.uid).get()
                                .addOnSuccessListener { document ->
                                    if (document != null && document.exists()) {
                                        val userDesignation = document.getString("designation")
                                        // Check if user is admin before allowing download
                                        if (userDesignation == "ADMIN" || userDesignation == "DEAN" || userDesignation == "HOD" || userDesignation == "Developer") {
                                            exportAllUsersDataToCSV()
                                        } else {
                                            Toast.makeText(this, "Only administrators can download user data", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .addOnFailureListener { 
                                    Toast.makeText(this, "Error checking user permissions", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                // Reset the flag after a delay to allow next navigation
                handler.postDelayed({
                    isReplacingFragment = false
                }, 500)
            }, 300)
            
            Log.d("MainActivity", "Navigation item handled successfully")
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
    
    override fun onDestroy() {
        super.onDestroy()
        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null)
    }
}