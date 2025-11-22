# Keystore Fingerprints for Firebase Registration

## Overview
This document contains the SHA-1 and SHA-256 fingerprints from your debug keystore that need to be added to your Firebase project for Google Sign-In to work properly.

## Certificate Fingerprints

### SHA-1 Fingerprint
```
4F:AA:E5:4B:6E:DB:C0:52:F7:58:DE:B2:5C:5D:CF:CD:A3:81:2D:5C
```

### SHA-256 Fingerprint
```
DF:92:09:CC:41:7F:B4:E1:A9:05:ED:87:EB:D4:18:E1:8E:BF:6C:84:2B:7D:44:5D:E4:6A:77:54:FB:5A:B6:08
```

## How to Add These to Firebase Console

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click on the gear icon next to "Project Overview" and select "Project settings"
4. In the "General" tab, scroll down to the "Your apps" section
5. Find your Android app (package name: com.bmsit.faculty)
6. In the "SHA certificate fingerprints" section:
   - Click "Add fingerprint"
   - Paste the SHA-1 fingerprint: `4F:AA:E5:4B:6E:DB:C0:52:F7:58:DE:B2:5C:5D:CF:CD:A3:81:2D:5C`
   - Click "Add fingerprint" again
   - Paste the SHA-256 fingerprint: `DF:92:09:CC:41:7F:B4:E1:A9:05:ED:87:EB:D4:18:E1:8E:BF:6C:84:2B:7D:44:5D:E4:6A:77:54:FB:5A:B6:08`
7. Click "Save" to save the changes

## Verification Steps

After adding the fingerprints:

1. Download the updated `google-services.json` file from Firebase Console
2. Replace the existing file in your project:
   - Path: `app/google-services.json`
3. Rebuild your project
4. Test Google Sign-In functionality

## Important Notes

1. **Release Keystore**: If you plan to distribute your app, you'll need to add the fingerprints for your release keystore as well.

2. **Multiple Developers**: If multiple developers are working on the project, each developer's debug keystore fingerprints should be added to Firebase Console.

3. **App Signing by Google Play**: If you're using App Signing by Google Play, you'll need to add Google's upload and deployment certificate fingerprints.

## Troubleshooting

If Google Sign-In still doesn't work after adding these fingerprints:

1. Verify that the package name in Firebase Console matches exactly: `com.bmsit.faculty`
2. Check that the Web client ID in your `strings.xml` file matches the one in Firebase Console:
   - Path: `app/src/main/res/values/strings.xml`
   - Look for the `default_web_client_id` string
3. Ensure you've downloaded and replaced the `google-services.json` file after adding the fingerprints

## Command to Retrieve Fingerprints (for reference)

If you need to retrieve these fingerprints again, use the following command:

```bash
keytool -list -v -keystore "C:\Users\RAKSHAK\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

Attribution: Document created by Rakshak S. Barkur