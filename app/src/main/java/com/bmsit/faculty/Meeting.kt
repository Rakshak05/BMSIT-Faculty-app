package com.bmsit.faculty

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import java.util.*

// A blueprint for what a meeting object looks like.
data class Meeting(
    val id: String = "",
    val title: String = "",
    val location: String = "",
    // We use Timestamp for Firestore to handle dates and times correctly
    val dateTime: Timestamp = Timestamp.now(),
    val attendees: String = "", // e.g., "All Faculty", "All HODs"
    val scheduledBy: String = "", // UID of the user who scheduled it

    // This property is for the UI only, to track if the card is expanded.
    // @get:Exclude tells Firestore not to save this field in the database.
    @get:Exclude var isExpanded: Boolean = false
)

