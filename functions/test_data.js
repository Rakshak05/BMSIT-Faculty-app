// Script to create test data for the BMSIT Faculty app
// This should be run in the Firebase Cloud Functions environment or locally with Firebase Admin SDK

const admin = require('firebase-admin');

// Initialize Firebase Admin SDK (you'll need to configure this with your credentials)
// admin.initializeApp();

const db = admin.firestore();

// Function to create test meetings
async function createTestMeetings() {
  try {
    // Create a test meeting for all faculty
    const testMeeting = {
      title: "Test Meeting for All Faculty",
      location: "Conference Room 1",
      dateTime: admin.firestore.Timestamp.fromDate(new Date(Date.now() + 24 * 60 * 60 * 1000)), // Tomorrow
      attendees: "All Faculty",
      scheduledBy: "test-admin-id", // Replace with actual admin user ID
      customAttendeeUids: [],
      status: "Active"
    };

    // Add the test meeting to Firestore
    const docRef = await db.collection('meetings').add(testMeeting);
    console.log('Test meeting created with ID:', docRef.id);

    // Create another test meeting for a specific date
    const nextWeek = new Date();
    nextWeek.setDate(nextWeek.getDate() + 7);
    
    const testMeeting2 = {
      title: "Weekly Team Meeting",
      location: "Virtual Meeting Room",
      dateTime: admin.firestore.Timestamp.fromDate(nextWeek),
      attendees: "All Faculty",
      scheduledBy: "test-admin-id", // Replace with actual admin user ID
      customAttendeeUids: [],
      status: "Active"
    };

    const docRef2 = await db.collection('meetings').add(testMeeting2);
    console.log('Second test meeting created with ID:', docRef2.id);

    console.log('Test data creation completed successfully!');
  } catch (error) {
    console.error('Error creating test data:', error);
  }
}

// Function to update a user's designation to Faculty
async function updateUserDesignation(userId) {
  try {
    await db.collection('users').doc(userId).update({
      designation: 'Faculty'
    });
    console.log(`User ${userId} designation updated to Faculty`);
  } catch (error) {
    console.error('Error updating user designation:', error);
  }
}

// Function to create a test user
async function createTestUser() {
  try {
    const testUser = {
      uid: "test-user-id",
      name: "Test User",
      email: "test@bmsit.edu",
      department: "CS",
      designation: "Faculty"
    };

    const docRef = await db.collection('users').doc(testUser.uid).set(testUser);
    console.log('Test user created with ID:', testUser.uid);
  } catch (error) {
    console.error('Error creating test user:', error);
  }
}

// Export functions for use in Firebase Functions
// exports.createTestData = createTestMeetings;
// exports.updateUserDesignation = updateUserDesignation;
// exports.createTestUser = createTestUser;

// Uncomment the following line to run the functions locally
// createTestMeetings();