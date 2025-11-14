package com.bmsit.faculty

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeetingTranscriptionActivity : AppCompatActivity() {
    
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var transcriptionListener: ListenerRegistration? = null
    private var currentMeetingId: String? = null
    private val TAG = "MeetingTranscriptionActivity"
    private val REQUEST_AUDIO_PICK = 2001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_transcription)
        
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance("gs://bmsit-faculty-30834.firebasestorage.app")
        
        val meetingId = intent.getStringExtra("MEETING_ID")
        val meetingTitle = intent.getStringExtra("MEETING_TITLE")
        
        if (meetingId.isNullOrEmpty()) {
            Log.e(TAG, "Meeting ID is null or empty")
            finish()
            return
        }
        
        currentMeetingId = meetingId
        loadMeetingTranscription(meetingId, meetingTitle ?: "Unknown Meeting")
    }
    
    private fun loadMeetingTranscription(meetingId: String, meetingTitle: String) {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val transcriptionText = findViewById<TextView>(R.id.textViewTranscription)
        val meetingInfoText = findViewById<TextView>(R.id.textViewMeetingInfo)
        val uploadButton = findViewById<Button>(R.id.buttonUploadRecording)
        
        // Set up upload button for hosts
        uploadButton.setOnClickListener {
            checkIfUserIsHost(meetingId) { isHost ->
                if (isHost) {
                    chooseAudioFile()
                } else {
                    Toast.makeText(this, "Only the meeting host can upload recordings.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        progressBar.visibility = View.VISIBLE
        transcriptionText.text = "Loading transcription..."
        
        // Load meeting details first
        db.collection("meetings").document(meetingId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val meeting = document.toObject(Meeting::class.java)
                    if (meeting != null) {
                        // Format meeting info
                        val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                        val meetingDate = dateFormat.format(meeting.dateTime.toDate())
                        
                        // Get host name
                        db.collection("users").document(meeting.scheduledBy).get()
                            .addOnSuccessListener { userDoc ->
                                val hostName = userDoc.getString("name") ?: "Unknown User"
                                
                                meetingInfoText.text = "Meeting: $meetingTitle\n" +
                                        "Date: $meetingDate\n" +
                                        "Host: $hostName"
                                
                                // Check if user is host to show upload button
                                checkIfUserIsHost(meetingId) { isHost ->
                                    uploadButton.visibility = if (isHost) View.VISIBLE else View.GONE
                                    
                                    // Now load the transcription
                                    loadTranscriptionContent(meetingId, progressBar, transcriptionText)
                                    // Start listening for real-time updates
                                    startListeningForTranscriptionUpdates(meetingId, transcriptionText)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error loading host info", e)
                                meetingInfoText.text = "Meeting: $meetingTitle\n" +
                                        "Date: $meetingDate\n" +
                                        "Host: Unknown User"
                                loadTranscriptionContent(meetingId, progressBar, transcriptionText)
                                startListeningForTranscriptionUpdates(meetingId, transcriptionText)
                            }
                    } else {
                        loadTranscriptionContent(meetingId, progressBar, transcriptionText)
                        startListeningForTranscriptionUpdates(meetingId, transcriptionText)
                    }
                } else {
                    loadTranscriptionContent(meetingId, progressBar, transcriptionText)
                    startListeningForTranscriptionUpdates(meetingId, transcriptionText)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading meeting details", e)
                loadTranscriptionContent(meetingId, progressBar, transcriptionText)
                startListeningForTranscriptionUpdates(meetingId, transcriptionText)
            }
    }
    
    private fun checkIfUserIsHost(meetingId: String, callback: (Boolean) -> Unit) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            callback(false)
            return
        }
        
        db.collection("meetings").document(meetingId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val meeting = document.toObject(Meeting::class.java)
                    callback(meeting?.scheduledBy == currentUser.uid)
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener {
                callback(false)
            }
    }
    
    private fun chooseAudioFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/mp3", "audio/mpeg", "audio/wav", "audio/flac"))
        }
        startActivityForResult(Intent.createChooser(intent, "Select Meeting Recording"), REQUEST_AUDIO_PICK)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_AUDIO_PICK -> {
                    data?.data?.let { uri ->
                        uploadMeetingRecording(uri)
                    }
                }
            }
        }
    }
    
    private fun uploadMeetingRecording(audioUri: Uri) {
        try {
            val meetingId = currentMeetingId
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            
            if (meetingId.isNullOrEmpty() || currentUser == null) {
                Toast.makeText(this, "Error: Missing meeting or user information", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Show progress
            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            val uploadButton = findViewById<Button>(R.id.buttonUploadRecording)
            val transcriptionText = findViewById<TextView>(R.id.textViewTranscription)
            
            progressBar.visibility = View.VISIBLE
            uploadButton.isEnabled = false
            transcriptionText.text = "Uploading recording...\n\nOnce uploaded, the transcription will be processed automatically. Please check back later."
            
            // Get audio file as bytes
            val inputStream = contentResolver.openInputStream(audioUri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            
            if (bytes == null) {
                Toast.makeText(this, "Error reading audio file", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                uploadButton.isEnabled = true
                return
            }
            
            // Determine file extension
            val mimeType = contentResolver.getType(audioUri)
            val extension = when (mimeType) {
                "audio/mp3", "audio/mpeg" -> "mp3"
                "audio/wav" -> "wav"
                "audio/flac" -> "flac"
                else -> "mp3" // default
            }
            
            // Create storage reference
            val fileName = "meeting_recordings/${meetingId}_${System.currentTimeMillis()}.$extension"
            val storageRef = storage.reference.child(fileName)
            val uploadTask = storageRef.putBytes(bytes)
            
            uploadTask
                .addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount
                    transcriptionText.text = "Uploading recording: ${String.format("%.1f", progress)}%\n\nOnce uploaded, the transcription will be processed automatically. Please check back later."
                    Log.d(TAG, "Upload is $progress% done")
                }
                .addOnSuccessListener {
                    Log.d(TAG, "Meeting recording uploaded successfully")
                    Toast.makeText(this, "Meeting recording uploaded successfully. It will be processed for transcription.", Toast.LENGTH_LONG).show()
                    
                    // Store recording metadata in Firestore
                    val recordingData = mapOf(
                        "meetingId" to meetingId,
                        "fileName" to fileName,
                        "uploadedBy" to currentUser.uid,
                        "uploadedAt" to com.google.firebase.Timestamp.now(),
                        "status" to "uploaded",
                        "fileSize" to bytes.size
                    )
                    
                    db.collection("meeting_recordings").add(recordingData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Recording metadata saved successfully")
                            transcriptionText.text = "Recording uploaded successfully!\n\nTranscription is being processed. Please check back in a few minutes."
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error saving recording metadata", e)
                            transcriptionText.text = "Recording uploaded but metadata saving failed.\n\nError: ${e.message}"
                        }
                    
                    // Hide progress
                    progressBar.visibility = View.GONE
                    uploadButton.isEnabled = true
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error uploading meeting recording", exception)
                    Toast.makeText(this, "Error uploading meeting recording: ${exception.message}", Toast.LENGTH_SHORT).show()
                    
                    // Hide progress and show error
                    progressBar.visibility = View.GONE
                    uploadButton.isEnabled = true
                    transcriptionText.text = "Failed to upload recording.\n\nError: ${exception.message}"
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing meeting recording", e)
            Toast.makeText(this, "Error processing meeting recording: ${e.message}", Toast.LENGTH_SHORT).show()
            
            // Hide progress and show error
            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            val uploadButton = findViewById<Button>(R.id.buttonUploadRecording)
            val transcriptionText = findViewById<TextView>(R.id.textViewTranscription)
            
            progressBar.visibility = View.GONE
            uploadButton.isEnabled = true
            transcriptionText.text = "Failed to process recording.\n\nError: ${e.message}"
        }
    }
    
    private fun loadTranscriptionContent(meetingId: String, progressBar: ProgressBar, transcriptionText: TextView) {
        // Query for transcriptions associated with this meeting
        db.collection("transcriptions")
            .whereEqualTo("meetingId", meetingId)
            .orderBy("createdAt")
            .get()
            .addOnSuccessListener { result ->
                progressBar.visibility = View.GONE
                
                if (result.isEmpty) {
                    // Check if there are pending recordings
                    checkForPendingRecordings(meetingId, transcriptionText)
                } else {
                    // Combine all transcriptions
                    val fullTranscription = StringBuilder()
                    for (document in result) {
                        val transcription = document.toObject(MeetingTranscription::class.java)
                        fullTranscription.append(transcription.transcription).append("\n\n")
                    }
                    
                    if (fullTranscription.isNotEmpty()) {
                        transcriptionText.text = fullTranscription.toString()
                    } else {
                        transcriptionText.text = "No transcription content available."
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading transcription", e)
                progressBar.visibility = View.GONE
                transcriptionText.text = "Error loading transcription: ${e.message}"
            }
    }
    
    private fun checkForPendingRecordings(meetingId: String, transcriptionText: TextView) {
        // Check if there are any recordings being processed
        db.collection("meeting_recordings")
            .whereEqualTo("meetingId", meetingId)
            .whereIn("status", listOf("uploaded", "processing"))
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    transcriptionText.text = "Transcription is being processed. Please check back later.\n\nUploaded recordings are automatically transcribed using Google Cloud Speech-to-Text."
                } else {
                    transcriptionText.text = "No transcription available for this meeting.\n\nAs a meeting host, you can upload a recording to generate a transcription."
                }
            }
            .addOnFailureListener {
                transcriptionText.text = "No transcription available for this meeting.\n\nAs a meeting host, you can upload a recording to generate a transcription."
            }
    }
    
    private fun startListeningForTranscriptionUpdates(meetingId: String, transcriptionText: TextView) {
        // Remove any existing listener
        transcriptionListener?.remove()
        
        // Listen for new transcriptions in real-time
        transcriptionListener = db.collection("transcriptions")
            .whereEqualTo("meetingId", meetingId)
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Listen failed: ", e)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    // Combine all transcriptions
                    val fullTranscription = StringBuilder()
                    for (document in snapshot) {
                        val transcription = document.toObject(MeetingTranscription::class.java)
                        fullTranscription.append(transcription.transcription).append("\n\n")
                    }
                    
                    if (fullTranscription.isNotEmpty()) {
                        transcriptionText.text = fullTranscription.toString()
                    } else {
                        // Check for pending recordings
                        checkForPendingRecordings(meetingId, transcriptionText)
                    }
                }
            }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Remove the listener when the activity is destroyed
        transcriptionListener?.remove()
    }
}