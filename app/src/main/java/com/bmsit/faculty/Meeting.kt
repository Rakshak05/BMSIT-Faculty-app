package com.bmsit.faculty

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import java.util.*

data class Meeting(
    val id: String = "",
    val title: String = "",
    val location: String = "",
    val dateTime: Timestamp = Timestamp.now(),
    val attendees: String = "", // e.g., "All Faculty", "All HODs", "Custom"
    val scheduledBy: String = "", // UID of the user who scheduled it

    // --- NEW: A list to hold the UIDs of custom attendees ---
    val customAttendeeUids: List<String> = emptyList(),

    @get:Exclude var isExpanded: Boolean = false
)

