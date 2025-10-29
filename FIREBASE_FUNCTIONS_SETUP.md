# Firebase Cloud Functions Setup for Push Notifications

## Issue
The app is not receiving notifications when closed because the Firebase Cloud Functions that send push notifications are not deployed.

## Solution
Deploy the Firebase Cloud Functions that trigger on Firestore events and send FCM notifications to users.

**NOTE**: Alternative solutions that do not require the Firebase Blaze plan are available:
1. [Self-Hosted Notification System](SELF_HOSTED_NOTIFICATIONS_ALTERNATIVE.md) (polling-based, works only when app is running)
2. [Email Notifications via SendGrid](EMAIL_NOTIFICATIONS_SETUP.md) (email-based, works even when app is closed)

## Prerequisites

1. **Firebase Project**: You need to have a Firebase project set up (bmsit-faculty-30834)
2. **Billing Plan**: The project must be on the Blaze (pay-as-you-go) plan to deploy Cloud Functions
3. **Firebase CLI**: Firebase CLI must be installed and configured

## Steps to Deploy Firebase Functions

### 1. Upgrade Firebase Project to Blaze Plan

1. Go to the Firebase Console: https://console.firebase.google.com/
2. Select your project (bmsit-faculty-30834)
3. Click on "Usage & billing" in the left sidebar
4. Click on "Details & settings"
5. Click "Upgrade" to switch to the Blaze plan
6. Follow the prompts to set up billing

### 2. Install Firebase CLI (if not already installed)

```bash
npm install -g firebase-tools
```

### 3. Log in to Firebase

```bash
firebase login
```

### 4. Initialize Firebase Project (if not already done)

```bash
firebase use --add
# Select your project (bmsit-faculty-30834)
```

### 5. Deploy the Functions

From the project root directory:

```bash
firebase deploy --only functions
```

## Functions Included

The project includes the following Cloud Functions:

1. **onMeetingCreate**: Triggers when a new meeting is created in Firestore and sends notifications to all relevant users
2. **onMeetingUpdate**: Triggers when a meeting is updated (cancelled or rescheduled) and sends notifications to all relevant users
3. **parseVoiceCommand**: Handles voice command parsing for scheduling meetings

## How Notifications Work

1. When a meeting is created/updated in Firestore, the corresponding Cloud Function is triggered
2. The function identifies all users who should receive the notification based on the meeting's attendees field
3. The function retrieves the FCM tokens for these users from their user documents
4. The function sends an FCM message to all retrieved tokens
5. Users receive push notifications even when the app is closed

## Testing the Functions

After deployment, you can test the notification system by:

1. Creating a new meeting in the app
2. Checking if all relevant users receive a push notification
3. Updating (cancelling/rescheduling) a meeting and verifying notifications are sent

## Troubleshooting

### Common Issues

1. **Functions not triggering**: 
   - Check Firebase Console logs for errors
   - Verify Firestore rules allow functions to read data
   - Ensure the functions are deployed successfully

2. **Notifications not received**:
   - Check if users have valid FCM tokens in their documents
   - Verify users have granted notification permissions
   - Check if the app is properly handling FCM messages

3. **Permission errors**:
   - Ensure Firestore rules allow functions to read user documents
   - Verify the functions have proper permissions to send FCM messages

### Checking Function Logs

```bash
firebase functions:log
```

### Verifying Deployment

```bash
firebase functions:list
```

## Alternative Solutions (No Blaze Plan Required)

### 1. Self-Hosted Notification System

If you prefer not to upgrade to the Firebase Blaze plan, you can use the self-hosted notification system instead. This approach uses your existing backend infrastructure to generate notifications through a polling mechanism.

See [SELF_HOSTED_NOTIFICATIONS_ALTERNATIVE.md](SELF_HOSTED_NOTIFICATIONS_ALTERNATIVE.md) for complete implementation details.

### 2. Email Notifications via SendGrid

For notifications that work even when the app is closed, you can use email notifications via SendGrid. This approach sends email notifications to users when meetings are created, cancelled, or rescheduled.

See [EMAIL_NOTIFICATIONS_SETUP.md](EMAIL_NOTIFICATIONS_SETUP.md) for complete implementation details.

## Additional Notes

- The functions automatically clean up invalid FCM tokens to prevent errors
- Notifications include different types: meeting creation, cancellation, and rescheduling
- The system works for all user types (Faculty, HODs, Deans, Admins) based on meeting attendees
- Voice command parsing is handled server-side for better accuracy

## Attributions

Code and machine learning model attributed to Rakshak S. Barkur as per user preference.