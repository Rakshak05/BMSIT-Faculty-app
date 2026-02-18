const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
admin.initializeApp();

// Function to send notifications when a notification document is created
exports.handleNotification = functions.firestore
    .document('notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        try {
            // Get the notification data
            const notification = snap.data();
            
            // Handle different types of notifications
            switch (notification.type) {
                case 'refresh_dashboard':
                    // Already handled by refreshNotifier.js
                    return null;
                    
                case 'meeting_cancelled':
                    // Handle meeting cancelled notifications
                    await handleMeetingCancelledNotification(notification);
                    break;
                    
                default:
                    console.log(`Unknown notification type: ${notification.type}`);
                    return null;
            }
            
            console.log(`Successfully handled notification of type: ${notification.type}`);
            return true;
        } catch (error) {
            console.error('Error handling notification:', error);
            return null;
        }
    });

// Get affected user IDs for a cancelled meeting
async function getAffectedUserIds(meeting) {
    try {
        // For custom meetings, return the custom attendee UIDs
        if (meeting.attendees === 'Custom' && Array.isArray(meeting.customAttendeeUids)) {
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
        
        const snap = await admin.firestore().collection('users')
            .where('designation', 'in', allowedDesignations)
            .get();
        
        // Also include the scheduler in the list of affected users
        const userIds = snap.docs.map(d => d.id);
        if (meeting.scheduledBy && !userIds.includes(meeting.scheduledBy)) {
            userIds.push(meeting.scheduledBy);
        }
        
        return userIds;
    } catch (error) {
        console.error('Error getting affected user IDs:', error);
        return [];
    }
}

// Get FCM tokens for user IDs
async function getUserTokens(userIds) {
    try {
        if (!userIds.length) return [];
        
        // Get user documents
        const userRefs = userIds.map(uid => admin.firestore().collection('users').doc(uid));
        const userDocs = await admin.firestore().getAll(...userRefs);
        
        // Extract FCM tokens
        const tokens = userDocs
            .filter(doc => doc.exists)
            .map(doc => doc.data().fcmToken)
            .filter(token => token);
            
        return tokens;
    } catch (error) {
        console.error('Error getting user tokens:', error);
        return [];
    }
}

// Clean up invalid FCM tokens
async function cleanupInvalidTokens(response, tokens) {
    try {
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
            
            // Remove invalid tokens from user documents
            const usersSnapshot = await admin.firestore().collection('users')
                .where('fcmToken', 'in', invalidTokens)
                .get();
                
            const batch = admin.firestore().batch();
            usersSnapshot.forEach(doc => {
                batch.update(doc.ref, {
                    fcmToken: admin.firestore.FieldValue.delete()
                });
            });
            
            if (!usersSnapshot.empty) {
                await batch.commit();
                console.log(`Cleaned up ${usersSnapshot.size} invalid tokens`);
            }
        }
    } catch (error) {
        console.error('Error cleaning up invalid tokens:', error);
    }
}

// Handle meeting cancelled notifications
async function handleMeetingCancelledNotification(notification) {
    try {
        console.log('Handling meeting cancelled notification');
        
        // Get the meeting ID from the notification
        const meetingId = notification.meetingId;
        if (!meetingId) {
            console.log('No meeting ID in notification');
            return;
        }
        
        // Get the meeting document to find the affected users
        const meetingDoc = await admin.firestore().collection('meetings').doc(meetingId).get();
        if (!meetingDoc.exists) {
            console.log(`Meeting ${meetingId} not found`);
            return;
        }
        
        const meeting = meetingDoc.data();
        
        // Get all affected users (attendees of the cancelled meeting)
        const affectedUserIds = await getAffectedUserIds(meeting);
        if (!affectedUserIds.length) {
            console.log('No affected users found');
            return;
        }
        
        // Get FCM tokens for all affected users
        const userTokens = await getUserTokens(affectedUserIds);
        if (!userTokens.length) {
            console.log('No FCM tokens found for affected users');
            return;
        }
        
        // Prepare notification payload
        const payload = {
            notification: {
                title: notification.title || 'Meeting Cancelled',
                body: notification.body || 'A meeting has been cancelled.'
            },
            data: {
                type: 'meeting_cancelled',
                meetingId: meetingId,
                timestamp: notification.timestamp?.toDate?.().toISOString() || new Date().toISOString()
            }
        };
        
        // Send the notification to all affected users
        const response = await admin.messaging().sendEachForMulticast({
            tokens: userTokens,
            ...payload
        });
        console.log(`Successfully sent meeting cancelled notification to ${response.successCount} users, failed: ${response.failureCount}`);
        
        // Clean up invalid tokens
        if (response.failureCount > 0) {
            await cleanupInvalidTokens(response, userTokens);
        }
        
        return response;
    } catch (error) {
        console.error('Error handling meeting cancelled notification:', error);
        return null;
    }
}