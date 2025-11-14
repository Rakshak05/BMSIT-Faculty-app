// Test script for transcription functionality
// This script can be used to manually test the transcription function

const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
try {
  admin.app();
} catch (e) {
  admin.initializeApp();
}

const db = admin.firestore();

// Function to simulate a recording upload
async function testTranscription() {
  try {
    // Create a test recording document
    const testRecording = {
      meetingId: 'test-meeting-id',
      fileName: 'test-audio-file.mp3',
      uploadedBy: 'test-user-id',
      uploadedAt: admin.firestore.FieldValue.serverTimestamp(),
      status: 'uploaded',
      fileSize: 1024000 // 1MB
    };

    // Add the test recording to Firestore
    const docRef = await db.collection('meeting_recordings').add(testRecording);
    console.log('Test recording added with ID:', docRef.id);
    
    console.log('The transcribeAudio function should now be triggered automatically.');
    console.log('Check the Firebase Functions logs to see the transcription process.');
    
  } catch (error) {
    console.error('Error adding test recording:', error);
  }
}

// Run the test
testTranscription();