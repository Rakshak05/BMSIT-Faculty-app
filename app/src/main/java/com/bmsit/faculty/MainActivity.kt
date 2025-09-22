package com.bmsit.faculty

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        navigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        if (savedInstanceState == null) {
            replaceFragment(DashboardFragment())
            navigationView.setCheckedItem(R.id.nav_dashboard)
        }

        setupNavigationMenuBasedOnUser()
    }

    // This is the new function that acts as the messenger from the calendar to the dashboard.
    fun switchToDashboardAndShowDate(date: LocalDate) {
        val bundle = Bundle()
        // We pass the date as a string because it's a simple and safe way to pass data.
        bundle.putString("SELECTED_DATE", date.toString())

        val dashboardFragment = DashboardFragment()
        dashboardFragment.arguments = bundle

        replaceFragment(dashboardFragment)
        // Visually update the side menu to show "Dashboard" as selected.
        navigationView.setCheckedItem(R.id.nav_dashboard)
    }

    private fun setupNavigationMenuBasedOnUser() {
        val currentUser = auth.currentUser
        val adminMenuItem = navigationView.menu.findItem(R.id.nav_admin)

        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val userDesignation = document.getString("designation")
                        adminMenuItem.isVisible = (userDesignation == "ADMIN" || userDesignation == "HOD")
                    } else {
                        adminMenuItem.isVisible = false
                    }
                }
        } else {
            adminMenuItem.isVisible = false
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> replaceFragment(DashboardFragment())
            R.id.nav_calendar -> replaceFragment(CalendarFragment())
            R.id.nav_profile -> replaceFragment(ProfileFragment())
            R.id.nav_admin -> replaceFragment(AdminFragment())
        }

        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            // If we are on a filtered dashboard, pressing back should go to the main dashboard view.
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is DashboardFragment && currentFragment.arguments != null) {
                replaceFragment(DashboardFragment())
                navigationView.setCheckedItem(R.id.nav_dashboard)
            } else {
                super.onBackPressed()
            }
        }
    }
}

