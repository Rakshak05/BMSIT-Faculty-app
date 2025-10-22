# Firebase Firestore Security Rules Fix

## Issue
The app is showing permission errors because the Firebase Firestore security rules are not properly configured to allow the app to read and write data.

Error message: "Permission Error: Check Firebase Firestore rules. The app needs read access to the 'meetings' collection."

## Solution
You need to update the Firebase Firestore security rules in the Firebase Console to allow read and write access for the collections used by the app.

## Required Rules

Here are the exact rules that need to be implemented:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow users to read their own user document
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Allow authenticated users to read meetings
    match /meetings/{meetingId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Allow authenticated users to read and write to any collection (for other data)
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## More Restrictive Rules (Optional)
If you want more restrictive rules for production, you can use:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read and write only their own user document
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Authenticated users can read meetings
    match /meetings/{meetingId} {
      allow read: if request.auth != null;
      // Only meeting creators can write to meetings
      allow write: if request.auth != null && (resource == null || resource.data.scheduledBy == request.auth.uid);
    }
  }
}
```

## How to Update Rules

1. Go to the Firebase Console (https://console.firebase.google.com/)
2. Select your project (bmsit-faculty-30834)
3. Click on "Firestore Database" in the left sidebar
4. Click on the "Rules" tab
5. Replace the existing rules with one of the rule sets above
6. Click "Publish" to save the changes

## Collections Used by the App

The app uses the following collections:
1. `users` - Stores user profile information
2. `meetings` - Stores meeting information

## Testing the Fix

After updating the rules:
1. Reinstall the app on your device
2. Log in with a Google account
3. The permission errors should no longer appear
4. You should be able to see meetings on the dashboard and schedule new meetings

## Additional Notes

- The rules above allow all authenticated users to read meetings, which is appropriate for a faculty management system where all faculty members should be able to see meetings
- The rules can be made more restrictive if needed for security purposes
- Make sure to test the app thoroughly after updating the rules to ensure all functionality works correctly

## Troubleshooting

If you still see permission errors after updating the rules:
1. Make sure you clicked "Publish" to save the changes
2. Wait a few minutes for the rules to propagate
3. Try signing out and signing back in to the app
4. Check the Firebase Console logs for more detailed error information

## Push Notifications

For push notifications to work when the app is closed, you also need to deploy the Firebase Cloud Functions. See FIREBASE_FUNCTIONS_SETUP.md for detailed instructions.

---

Improved responsiveness, added error handling, fixed Firebase permission issue, improved UI, added 'FIREBASE_RULES_FIX.md' for Firebase configuration and the alarm is working properly.