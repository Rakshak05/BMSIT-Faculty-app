package com.bmsit.faculty

import java.util.Date

data class CurrentMeeting(
    val id: String,
    val title: String,
    val dateTime: Date,
    val location: String,
    val attendees: String,
    val scheduledBy: String,
    val startTime: Date,
    val isRecording: Boolean = false,
    val endTime: Date? = null
)