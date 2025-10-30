package com.bmsit.faculty

// We are adding two new fields to our user blueprint.
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val department: String = "", // e.g., "Computer Science", "Mechanical"
    val designation: String = "" // e.g., "Assistant Professor"
)

