const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
admin.initializeApp();

// Function to send meeting start notifications
exports.sendMeetingStartNotification = functions.pubsub
    .schedule('every 1 minutes from 00:00 to 23:59')
    .timeZone('Asia/Kolkata')
    .onRun(async (context) => {
        try {
            console.log('Checking for meetings starting now...');
            
            // Get current time with a small window (±30 seconds) to account for timing differences
            const now = new Date();
            const fiveMinutesAgo = new Date(now.getTime() - 5 * 60 * 1000); // 5 minutes ago
            const fiveMinutesFromNow = new Date(now.getTime() + 5 * 60 * 1000); // 5 minutes from now
            
            // Query for active meetings that are starting around now
            const snapshot = await admin.firestore().collection('meetings')
                .where('status', '==', 'Active')
                .where('dateTime', '>=', admin.firestore.Timestamp.fromDate(fiveMinutesAgo))
                .where('dateTime', '<=', admin.firestore.Timestamp.fromDate(fiveMinutesFromNow))
                .get();
            
            console.log(`Found ${snapshot.size} meetings starting soon`);
            
            if (snapshot.empty) {
                console.log('No meetings starting now');
                return null;
            }
            
            // Process each meeting that's starting
            for (const doc of snapshot.docs) {
                try {
                    const meeting = doc.data();
                    console.log(`Processing meeting: ${meeting.title}`);
                    
                    // Get target user IDs for this meeting
                    const targetUids = await getTargetUids(meeting);
                    console.log(`Meeting has ${targetUids.length} target users`);
                    
                    if (targetUids.length > 0) {
                        // Send FCM notification to target users
                        await sendMeetingStartNotificationToUsers(meeting, targetUids);
                    }
                } catch (error) {
                    console.error(`Error processing meeting ${doc.id}:`, error);
                }
            }
            
            return null;
        } catch (error) {
            console.error('Error in sendMeetingStartNotification:', error);
            return null;
        }
    });

// Helper function to get target user IDs for a meeting
async function getTargetUids(meeting) {
    if (meeting.attendees === 'Custom' && Array.isArray(meeting.customAttendeeUids)) {
        // For custom attendees, return the custom attendee UIDs
        return meeting.customAttendeeUids;
    }
    
    // For group meetings, get users with matching designations
    const roleMap = {
        'All Faculty': ['Faculty', 'Assistant Professor', 'Associate Professor', 'Lab Assistant', 'HOD', 'ADMIN'],
        'Associate Professors': ['Associate Professor'],
        'Assistant Professors': ['Assistant Professor'],
        'HODs Only': ['HOD']
    };
    
    const allowedDesignations = roleMap[meeting.attendees] || [];
    if (!allowedDesignations.length) return [];
    
    try {
        const snap = await admin.firestore().collection('users')
            .where('designation', 'in', allowedDesignations)
            .get();
        return snap.docs.map(d => d.id);
    } catch (error) {
        console.error('Error fetching users by designation:', error);
        return [];
    }
}

// Helper function to send meeting start notification to users
async function sendMeetingStartNotificationToUsers(meeting, uids) {
    try {
        if (!uids.length) return;
        
        console.log(`Sending meeting start notification to ${uids.length} users`);
        
        // Get FCM tokens for target users
        const tokens = [];
        const userRefs = uids.map(uid => admin.firestore().collection('users').doc(uid));
        const userDocs = await admin.firestore().getAll(...userRefs);
        
        userDocs.forEach(doc => {
            if (doc.exists) {
                const fcmToken = doc.get('fcmToken');
                if (fcmToken) {
                    tokens.push(fcmToken);
                }
            }
        });
        
        console.log(`Found ${tokens.length} valid FCM tokens`);
        
        if (tokens.length === 0) {
            console.log('No valid FCM tokens found for users');
            return;
        }
        
        // Prepare notification payload
        const payload = {
            notification: {
                title: 'Meeting Starting Now',
                body: `The meeting "${meeting.title}" is starting now at ${meeting.location || 'the designated location'}.`,
            },
            data: {
                type: 'meeting_start',
                meetingId: meeting.id,
                title: meeting.title,
                location: meeting.location || ''
            }
        };
        
        // Send notification to all tokens
        const response = await admin.messaging().sendEachForMulticast({
            tokens: tokens,
            ...payload
        });
        
        console.log(`Successfully sent ${response.successCount} notifications, failed: ${response.failureCount}`);
        
        // Clean up invalid tokens
        if (response.failureCount > 0) {
            const invalidTokens = [];
            response.responses.forEach((resp, idx) => {
                if (!resp.success) {
                    const errorCode = resp.error?.code;
                    if (errorCode === 'messaging/invalid-registration-token' || 
                        errorCode === 'messaging/registration-token-not-registered') {
                        invalidTokens.push(tokens[idx]);
                    }
                }
            });
            
            if (invalidTokens.length > 0) {
                console.log(`Cleaning up ${invalidTokens.length} invalid tokens`);
                await cleanupInvalidTokens(invalidTokens);
            }
        }
        
        return response;
    } catch (error) {
        console.error('Error sending meeting start notification:', error);
        return null;
    }
}

// Helper function to clean up invalid FCM tokens
async function cleanupInvalidTokens(invalidTokens) {
    try {
        if (!invalidTokens.length) return;
        
        const batch = admin.firestore().batch();
        const usersSnapshot = await admin.firestore().collection('users')
            .where('fcmToken', 'in', invalidTokens)
            .get();
        
        usersSnapshot.forEach(doc => {
            batch.update(doc.ref, {
                fcmToken: admin.firestore.FieldValue.delete()
            });
        });
        
        if (!usersSnapshot.empty) {
            await batch.commit();
            console.log(`Cleaned up ${usersSnapshot.size} invalid tokens`);
        }
    } catch (error) {
        console.error('Error cleaning up invalid tokens:', error);
    }
}