const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
admin.initializeApp();

// Function to send refresh notification when a refresh notification document is created
exports.sendRefreshNotification = functions.firestore
    .document('notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        try {
            // Get the notification data
            const notification = snap.data();
            
            // Check if this is a refresh notification
            if (notification.type !== 'refresh_dashboard') {
                return null;
            }
            
            console.log('Sending refresh notification to all users');
            
            // Get all users tokens (in a real app, you would store FCM tokens for each user)
            // For now, we'll send to all devices subscribed to the "refresh" topic
            const payload = {
                notification: {
                    title: 'Dashboard Update',
                    body: notification.message || 'Please refresh your dashboard',
                },
                data: {
                    type: 'refresh_dashboard',
                    timestamp: notification.timestamp?.toDate?.().toISOString() || new Date().toISOString()
                }
            };
            
            // Send to all users subscribed to the "refresh" topic
            const response = await admin.messaging().sendToTopic('refresh', payload);
            console.log('Successfully sent refresh notification:', response);
            
            return response;
        } catch (error) {
            console.error('Error sending refresh notification:', error);
            return null;
        }
    });