# Firebase Registration Guide for Google Sign-In

## Overview
To enable Google Sign-In with Firebase Authentication, you must register your app with the correct certificate fingerprints in the Firebase Console. This guide will walk you through the process.

## Prerequisites
1. You must have access to the Firebase Console for your project
2. You need to have the SHA-1 and SHA-256 certificate fingerprints for your app
3. Your app must be registered in Firebase

## Step-by-Step Registration Process

### Step 1: Get Your Certificate Fingerprints
Use the CertificateDebugActivity we created to get your fingerprints:
1. Run the CertificateDebugActivity from your app
2. The SHA-1 and SHA-256 fingerprints will be displayed
3. Copy both fingerprints to your clipboard

Alternatively, you can get the fingerprints using the command line:

For debug keystore:
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

For release keystore:
```bash
keytool -list -v -keystore /path/to/your/release.keystore -alias your_key_alias
```

### Step 2: Add Fingerprints to Firebase Console
1. Go to the Firebase Console (https://console.firebase.google.com/)
2. Select your project
3. Click on the gear icon next to "Project Overview" and select "Project settings"
4. In the "General" tab, scroll down to the "Your apps" section
5. Find your Android app (package name: com.bmsit.faculty)
6. In the "SHA certificate fingerprints" section, add both fingerprints:
   - Click "Add fingerprint"
   - Paste the SHA-1 fingerprint
   - Click "Add fingerprint" again
   - Paste the SHA-256 fingerprint
7. Click "Save" to save the changes

### Step 3: Download Updated google-services.json
1. After adding the fingerprints, download the updated google-services.json file
2. Replace the existing google-services.json file in your project:
   - Path: app/google-services.json
3. Sync your project with Gradle files

### Step 4: Verify Google Sign-In Configuration
1. Check that your Web client ID is correct in strings.xml:
   - Path: app/src/main/res/values/strings.xml
   - Look for the `default_web_client_id` string
   - It should match the Web client ID in Firebase Console > Authentication > Sign-in method > Google

2. Verify the package name:
   - In Firebase Console, ensure the package name is exactly "com.bmsit.faculty"

### Step 5: Test Google Sign-In
1. Rebuild and run your app
2. Try to sign in with Google
3. The authentication should now work properly

## Common Issues and Solutions

### Issue 1: "DEVELOPER_ERROR" or "Error 7"
**Cause:** Mismatch between app configuration and Firebase settings
**Solution:**
- Verify SHA-1 and SHA-256 fingerprints are correctly added to Firebase Console
- Check that the package name matches exactly
- Ensure the Web client ID in strings.xml is correct

### Issue 2: "unknown calling package" error
**Cause:** Google Play Services broker issue, often related to missing SHA-256 fingerprint
**Solution:**
- Ensure both SHA-1 and SHA-256 fingerprints are added to Firebase Console
- Check that you're using the correct google-services.json file

### Issue 3: Sign-in succeeds but no user data is created
**Cause:** Issues with Firestore permissions or configuration
**Solution:**
- Check Firebase Firestore security rules
- Verify that the app has proper permissions to read/write to the "users" collection

## Debugging Tools Included in This Project

### CertificateDebugActivity
- Displays SHA-1 and SHA-256 fingerprints
- Allows copying fingerprints to clipboard
- Provides refresh functionality

### EmailDebugActivity
- Tests email sending functionality
- Displays detailed authentication information
- Shows current user status

### TestEmailActivity
- Simple tool to test password reset email sending

## Additional Notes

1. **Release vs Debug Keystore:** If you plan to distribute your app, you'll need to add the fingerprints for your release keystore as well.

2. **Multiple Developers:** If multiple developers are working on the project, each developer's debug keystore fingerprints should be added to Firebase Console.

3. **App Signing by Google Play:** If you're using App Signing by Google Play, you'll need to add Google's upload and deployment certificate fingerprints.

Attribution: Guide created by Rakshak S. Barkur