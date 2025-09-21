package com.bmsit.faculty

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

        setupNavigationMenuBasedOnUserRole()
    }

    private fun setupNavigationMenuBasedOnUserRole() {
        val currentUser = auth.currentUser
        val adminMenuItem = navigationView.menu.findItem(R.id.nav_admin)

        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val userRole = document.getString("role")
                        // Show the admin panel only if the role is "ADMIN" or "HOD"
                        adminMenuItem.isVisible = (userRole == "ADMIN" || userRole == "HOD")
                    } else {
                        Log.d("MainActivity", "User document not found.")
                        adminMenuItem.isVisible = false
                    }
                }
                .addOnFailureListener { exception ->
                    Log.w("MainActivity", "Error fetching user role.", exception)
                    adminMenuItem.isVisible = false
                }
        } else {
            adminMenuItem.isVisible = false
        }
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> replaceFragment(DashboardFragment())
            // This line makes the calendar button work
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
            super.onBackPressed()
        }
    }
}

