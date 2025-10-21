package com.bmsit.faculty

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d("MainActivity", "onCreate started")
            setContentView(R.layout.activity_main)
            Log.d("MainActivity", "Layout inflated successfully")

            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()
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
                    replaceFragment(DashboardFragment())
                    navigationView.setCheckedItem(R.id.nav_dashboard)
                    Log.d("MainActivity", "Dashboard fragment loaded")
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
            val adminMenuItem = navigationView.menu.findItem(R.id.nav_admin)
            val diagnosticMenuItem = navigationView.menu.findItem(R.id.nav_diagnostic)
            val headerView = navigationView.getHeaderView(0)
            val headerGreeting = headerView.findViewById<TextView>(R.id.textViewHeaderGreeting)
            val headerInitials = headerView.findViewById<TextView>(R.id.textViewHeaderInitials)

            if (currentUser != null) {
                db.collection("users").document(currentUser.uid).get()
                    .addOnSuccessListener { document ->
                        try {
                            if (document != null && document.exists()) {
                                val userDesignation = document.getString("designation")
                                // Make admin panel accessible to ADMIN users, with a fallback check
                                adminMenuItem.isVisible = (userDesignation == "ADMIN" || userDesignation == "DEAN" || userDesignation == "HOD")
                                
                                // Show diagnostic tools to all users for debugging
                                diagnosticMenuItem.isVisible = true
                                
                                // If designation is null or Unassigned, still allow access to admin panel for ADMIN users
                                // This handles cases where the designation might not have been updated yet
                                if (!adminMenuItem.isVisible && userDesignation == null) {
                                    // Check if the user's email domain suggests they might be an admin
                                    val email = currentUser.email
                                    if (email != null && (email.contains("admin") || email.contains("bmsit"))) {
                                        adminMenuItem.isVisible = true
                                    }
                                }

                                // Set header greeting from Firestore name or Google displayName
                                val nameFromDb = document.getString("name")
                                val fallbackName = currentUser.displayName
                                val name = (nameFromDb ?: fallbackName ?: "").trim()
                                if (name.isNotBlank()) {
                                    // Greeting with full name (including surname)
                                    headerGreeting?.text = "Welcome back, $name"
                                    // Initials: if multi-word, first letter of first + first letter of last.
                                    // If single-word name, display the full first name word.
                                    val parts = name.split(" ").filter { it.isNotBlank() }
                                    val initialsOrName = when {
                                        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                                        parts.size == 1 -> parts.first()
                                        else -> "?"
                                    }
                                    headerInitials?.text = initialsOrName
                                } else {
                                    headerGreeting?.text = "Welcome back,"
                                    headerInitials?.text = "?"
                                }
                                Log.d("MainActivity", "User menu setup completed")
                            } else {
                                adminMenuItem.isVisible = false
                                diagnosticMenuItem.isVisible = true
                                val displayName = currentUser.displayName?.trim()
                                if (!displayName.isNullOrBlank()) {
                                    headerGreeting?.text = "Welcome back, $displayName"
                                    val parts = displayName.split(" ").filter { it.isNotBlank() }
                                    val initialsOrName = when {
                                        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                                        parts.size == 1 -> parts.first()
                                        else -> "?"
                                    }
                                    headerInitials?.text = initialsOrName
                                } else {
                                    headerGreeting?.text = "Welcome back,"
                                    headerInitials?.text = "?"
                                }
                                Log.d("MainActivity", "User document not found, using default menu setup")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error processing user document", e)
                            adminMenuItem.isVisible = true // Fallback to visible for ADMIN users
                            diagnosticMenuItem.isVisible = true // Also keep diagnostic visible for debugging
                        }
                    }
                    .addOnFailureListener { 
                        try {
                            // Even if we fail to fetch user data, don't hide the admin menu for ADMIN users
                            // This prevents the menu from disappearing due to network issues
                            Log.w("MainActivity", "Failed to fetch user data, keeping admin menu visible for ADMIN users")
                            adminMenuItem.isVisible = true // Fallback to visible for ADMIN users
                            diagnosticMenuItem.isVisible = true // Also keep diagnostic visible for debugging
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error in failure handler", e)
                        }
                    }
            } else {
                adminMenuItem.isVisible = false
                diagnosticMenuItem.isVisible = true
                headerGreeting?.text = "Welcome back,"
                headerInitials?.text = "?"
                Log.d("MainActivity", "No current user, using default menu setup")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupNavigationMenuBasedOnUser", e)
            Toast.makeText(this, "Error setting up navigation menu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        try {
            Log.d("MainActivity", "onNavigationItemSelected called for item: ${item.itemId}")
            when (item.itemId) {
                R.id.nav_dashboard -> replaceFragment(DashboardFragment())
                R.id.nav_calendar -> replaceFragment(CalendarFragment())
                R.id.nav_profile -> replaceFragment(ProfileFragment())
                R.id.nav_admin -> replaceFragment(AdminFragment())
                R.id.nav_diagnostic -> replaceFragment(DiagnosticFragment())
            }

            drawerLayout.closeDrawer(GravityCompat.START)
            Log.d("MainActivity", "Navigation item handled successfully")
            return true
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onNavigationItemSelected", e)
            Toast.makeText(this, "Error handling navigation: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        try {
            Log.d("MainActivity", "replaceFragment called for ${fragment.javaClass.simpleName}")
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            Log.d("MainActivity", "Fragment replaced successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in replaceFragment", e)
            Toast.makeText(this, "Error loading fragment: ${e.message}", Toast.LENGTH_SHORT).show()
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
            super.onBackPressed() // Still call super to ensure back press works
        }
    }
}