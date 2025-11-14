package com.bmsit.faculty

import com.google.firebase.Timestamp

data class MeetingTranscription(
    val id: String = "",
    val meetingId: String = "",
    val transcription: String = "",
    val language: String = "en-US", // Default to English
    val createdAt: Timestamp = Timestamp.now(),
    val isFinal: Boolean = false
)