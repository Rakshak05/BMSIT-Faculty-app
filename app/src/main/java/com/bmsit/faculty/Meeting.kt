package com.bmsit.faculty

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import java.util.*

data class Meeting(
    val id: String = "",
    val title: String = "",
    val location: String = "",
    val dateTime: Timestamp = Timestamp.now(),
    val attendees: String = "", // e.g., "All Associate Prof", "All Assistant Prof", "All Faculty", "Custom"
    val scheduledBy: String = "", // UID of the user who scheduled it

    // --- NEW: A list to hold the UIDs of custom attendees ---
    val customAttendeeUids: List<String> = emptyList(),
    // --- NEW: Status of the meeting (e.g., Active, Cancelled) ---
    val status: String = "Active",
    // --- NEW: Actual end time of the meeting ---
    val endTime: Timestamp? = null,
    // --- NEW: Duration of the meeting in minutes ---
    val duration: Int = 60, // Default to 60 minutes (1 hour)
    // --- NEW: Timestamp when attendance was taken ---
    val attendanceTakenAt: Timestamp? = null,

    @get:Exclude var isExpanded: Boolean = false
)