# APK Installation Guide

## Overview
This guide will help you install the debug APK manually when automatic installation fails with the "UNKNOWN" error.

## Prerequisites
1. Enable Developer Options on your Android device
2. Enable USB Debugging in Developer Options
3. Connect your Android device to your computer via USB

## Method 1: Using Android Studio (Recommended)

1. Open Android Studio
2. Connect your Android device via USB
3. In Android Studio, go to "Run" > "Run 'app'" or click the green play button
4. Select your connected device from the list
5. Android Studio will automatically install and launch the app

## Method 2: Manual Installation via ADB

### Step 1: Locate ADB
ADB is part of the Android SDK. Common locations:
- Windows: `C:\Users\[YourUsername]\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- macOS: `~/Library/Android/sdk/platform-tools/adb`
- Linux: `~/Android/Sdk/platform-tools/adb`

### Step 2: Install the APK
1. Open a terminal/command prompt
2. Navigate to the directory containing ADB
3. Run the following command:
   ```
   adb install -r "D:\BMSIT-Faculty-app\app\build\outputs\apk\debug\app-debug.apk"
   ```
   
   The `-r` flag allows reinstalling if the app is already installed.

### Step 3: Launch the App
After installation, you can launch the app manually from your device's app drawer, or use ADB:
```
adb shell am start -n com.bmsit.faculty/.LoginActivity
```

## Method 3: Direct APK Transfer

### Step 1: Transfer APK to Device
1. Connect your Android device to your computer
2. Copy the APK file to your device's storage:
   - Path: `D:\BMSIT-Faculty-app\app\build\outputs\apk\debug\app-debug.apk`
3. Transfer it to your device's Downloads folder or any accessible location

### Step 2: Install from Device
1. On your Android device, open a file manager app
2. Navigate to the location where you copied the APK
3. Tap on the APK file
4. If prompted, enable "Install from unknown sources" for your file manager
5. Follow the installation prompts

## Troubleshooting Common Issues

### Issue 1: "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
**Solution:**
- Uninstall the existing app first:
  ```
  adb uninstall com.bmsit.faculty
  ```
- Then reinstall the APK

### Issue 2: "INSTALL_FAILED_ALREADY_EXISTS"
**Solution:**
- Use the `-r` flag to reinstall:
  ```
  adb install -r "path/to/app-debug.apk"
  ```

### Issue 3: "INSTALL_FAILED_INVALID_APK"
**Solution:**
- Verify the APK file is not corrupted
- Rebuild the project and try again

### Issue 4: "adb: command not found" or "'adb' is not recognized"
**Solution:**
1. Add ADB to your system PATH:
   - Find the platform-tools directory in your Android SDK
   - Add it to your system's PATH environment variable
2. Or use the full path to ADB:
   ```
   C:\Users\[YourUsername]\AppData\Local\Android\Sdk\platform-tools\adb install -r "path/to/app-debug.apk"
   ```

### Issue 5: "adb: no devices/emulators found"
**Solution:**
1. Check USB connection
2. Ensure USB Debugging is enabled on your device
3. Try different USB cables or ports
4. Install proper USB drivers for your device

## Device-Specific Considerations

### Samsung Devices
- May require enabling "USB debugging (Security settings)" in Developer Options
- May show additional security prompts during installation

### Xiaomi/Redmi Devices
- May require enabling "Install via USB" in Developer Options
- May require disabling "MIUI optimization"

### Huawei Devices
- May require enabling "Allow debugging (Security settings)" in Developer Options

## Verifying Installation

After installation, you can verify the app is installed:
```
adb shell pm list packages | grep com.bmsit.faculty
```

## Additional Notes

1. **Debug vs Release APK**: The APK generated is a debug version, which is larger and less optimized than a release version but suitable for development and testing.

2. **Certificate Issues**: If you encounter certificate-related issues, you may need to uninstall the existing app before installing the new one.

3. **Permissions**: The app requires several permissions that will be requested at runtime on newer Android versions.

Attribution: Guide created by Rakshak S. Barkur