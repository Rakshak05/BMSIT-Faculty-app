package com.bmsit.faculty.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.bmsit.faculty.MeetingTranscription
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class SpeechService(private val context: Context) {
    private val TAG = "SpeechService"
    
    // Recording status
    private val isRecording = AtomicBoolean(false)
    
    // Speech recognizer
    private var speechRecognizer: SpeechRecognizer? = null
    
    // Firestore instance
    private val db = FirebaseFirestore.getInstance()
    
    // Supported languages
    private val SUPPORTED_LANGUAGES = listOf("en-US", "hi-IN", "kn-IN")
    
    // Current meeting info
    private var currentMeetingId: String? = null
    private var currentTranscription: StringBuilder = StringBuilder()
    private var onTranscriptionUpdateCallback: ((String) -> Unit)? = null
    
    init {
        Log.d(TAG, "SpeechService initialized")
    }
    
    /**
     * Start recording and transcribing audio
     */
    fun startRecording(meetingId: String, onTranscriptionUpdate: (String) -> Unit) {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return
        }
        
        currentMeetingId = meetingId
        onTranscriptionUpdateCallback = onTranscriptionUpdate
        currentTranscription.clear()
        
        isRecording.set(true)
        
        Log.d(TAG, "Started recording for meeting: $meetingId")
        
        // Initialize speech recognizer
        initializeSpeechRecognizer()
        
        // Start speech recognition
        startSpeechRecognition()
    }
    
    /**
     * Stop recording
     */
    fun stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "Not recording")
            return
        }
        
        isRecording.set(false)
        currentMeetingId?.let { meetingId ->
            saveTranscription(meetingId, currentTranscription.toString()) { success ->
                Log.d(TAG, "Transcription saved: $success")
            }
        }
        
        // Stop speech recognizer
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        
        Log.d(TAG, "Stopped recording")
    }
    
    /**
     * Initialize speech recognizer
     */
    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Not used
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Not used
                }
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                }
                
                override fun onError(error: Int) {
                    val errorMessage = getErrorMessage(error)
                    Log.e(TAG, "Speech recognition error: $errorMessage")
                    if (isRecording.get()) {
                        // Restart recognition if still recording
                        startSpeechRecognition()
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    Log.d(TAG, "Speech recognition results received")
                    processRecognitionResults(results)
                    
                    // Continue recognition if still recording
                    if (isRecording.get()) {
                        startSpeechRecognition()
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    Log.d(TAG, "Partial speech recognition results received")
                    processRecognitionResults(partialResults)
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Not used
                }
            })
        } else {
            Log.e(TAG, "Speech recognition not available on this device")
        }
    }
    
    /**
     * Start speech recognition
     */
    private fun startSpeechRecognition() {
        if (speechRecognizer != null && isRecording.get()) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // Default to English
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
            }
        }
    }
    
    /**
     * Process recognition results
     */
    private fun processRecognitionResults(results: Bundle?) {
        results?.let { bundle ->
            val matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val transcription = matches[0] // Use the most confident result
                currentTranscription.append(transcription).append(" ")
                
                // Update callback with new transcription
                onTranscriptionUpdateCallback?.invoke(currentTranscription.toString())
                
                Log.d(TAG, "Transcription updated: $transcription")
            }
        }
    }
    
    /**
     * Get error message from error code
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
    }
    
    /**
     * Translate text to English
     */
    fun translateToEnglish(text: String, sourceLanguage: String, onTranslationComplete: (String) -> Unit) {
        // In a real implementation, you would use a translation service
        // For now, we'll just return the original text
        Log.d(TAG, "Translating text from $sourceLanguage to English")
        onTranslationComplete(text)
    }
    
    /**
     * Save transcription to Firestore
     */
    fun saveTranscription(meetingId: String, transcription: String, onComplete: (Boolean) -> Unit) {
        if (transcription.isBlank()) {
            Log.d(TAG, "Transcription is empty, not saving")
            onComplete(false)
            return
        }
        
        val transcriptionData = MeetingTranscription(
            meetingId = meetingId,
            transcription = transcription,
            language = "en-US", // Default to English
            isFinal = true
        )
        
        db.collection("transcriptions")
            .add(transcriptionData)
            .addOnSuccessListener {
                Log.d(TAG, "Transcription saved successfully for meeting: $meetingId")
                onComplete(true)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error saving transcription for meeting: $meetingId", exception)
                onComplete(false)
            }
    }
    
    /**
     * Check if service is currently recording
     */
    fun isRecording(): Boolean {
        return isRecording.get()
    }
}