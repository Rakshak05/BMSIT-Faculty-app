package com.bmsit.faculty

// This is a simple data blueprint.
// It helps us store user information in a clean object.
// We give default values so it's safe to create an empty user.
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = ""
)
